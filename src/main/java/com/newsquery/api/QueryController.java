package com.newsquery.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.newsquery.embedding.EmbeddingClient;
import com.newsquery.monitoring.QueryMetrics;
import com.newsquery.nql.KeywordExtractor;
import com.newsquery.nql.NQLExpression;
import com.newsquery.nql.NQLQueryParser;
import com.newsquery.query.AggregationBuilder;
import com.newsquery.scoring.RRFScorer;
import com.newsquery.search.NewsSearchService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api")
public class QueryController {

    private final NQLQueryParser nqlQueryParser;
    private final KeywordExtractor keywordExtractor;
    private final EmbeddingClient embeddingClient;
    private final RRFScorer rrfScorer;
    private final NewsSearchService newsSearchService;
    private final QueryMetrics queryMetrics;
    private final AggregationBuilder aggregationBuilder;

    public QueryController(NQLQueryParser nqlQueryParser,
                           KeywordExtractor keywordExtractor,
                           EmbeddingClient embeddingClient,
                           RRFScorer rrfScorer,
                           NewsSearchService newsSearchService,
                           QueryMetrics queryMetrics,
                           AggregationBuilder aggregationBuilder) {
        this.nqlQueryParser    = nqlQueryParser;
        this.keywordExtractor  = keywordExtractor;
        this.embeddingClient   = embeddingClient;
        this.rrfScorer         = rrfScorer;
        this.newsSearchService = newsSearchService;
        this.queryMetrics      = queryMetrics;
        this.aggregationBuilder = aggregationBuilder;
    }

    @PostMapping("/query")
    public ResponseEntity<?> query(@RequestBody QueryRequest request) {
        if (request.nql() == null || request.nql().isBlank()) {
            queryMetrics.recordError();
            return ResponseEntity.badRequest().body(
                new ErrorResponse("NQL 쿼리가 비어있습니다. 검색 조건을 입력해주세요.", "EMPTY_QUERY", "/api/query")
            );
        }

        queryMetrics.recordQuery();
        long startTime = System.currentTimeMillis();

        try {

            // 1. NQL → IR
            long parseStart = System.currentTimeMillis();
            NQLExpression expr = nqlQueryParser.parseToExpression(request.nql());
            queryMetrics.recordParseTime(System.currentTimeMillis() - parseStart);

            // Aggregation 여부 확인
            boolean hasAggregation = expr instanceof NQLExpression.AggregationExpr;
            NQLExpression baseExpr = expr;
            String groupByField = null;
            Integer limit = null;

            if (hasAggregation) {
                var agg = (NQLExpression.AggregationExpr) expr;
                baseExpr = agg.expr();
                groupByField = agg.groupByField();
                limit = agg.limit().orElse(10);
            }

            // 2. IR → ES bool query
            long buildStart = System.currentTimeMillis();
            var boolQuery = nqlQueryParser.buildQuery(baseExpr);
            queryMetrics.recordBuildQueryTime(System.currentTimeMillis() - buildStart);

            // 3. keyword() 표현식 추출 → 임베딩 (실패 시 null → BM25 폴백)
            long embeddingStart = System.currentTimeMillis();
            List<NQLExpression.KeywordExpr> keywords = keywordExtractor.extract(baseExpr);
            float[] vector = null;
            if (!keywords.isEmpty()) {
                String keywordText = keywords.stream()
                        .map(NQLExpression.KeywordExpr::text)
                        .collect(Collectors.joining(" "));
                vector = embeddingClient.embed(keywordText);
                if (vector == null) {
                    // 임베딩 실패 - BM25 단독 검색으로 진행
                }
            }
            queryMetrics.recordEmbeddingTime(System.currentTimeMillis() - embeddingStart);

            // 4. RRF retriever 구성 (vector == null 이면 BM25 단독)
            JsonNode retriever = rrfScorer.buildRetriever(boolQuery, keywords, vector);

            // 5. 집계가 있으면 aggregation 추가
            if (hasAggregation && groupByField != null) {
                retriever = aggregationBuilder.buildQueryWithAggregation(retriever, groupByField, limit);
            }

            // 6. ES 검색
            long searchStart = System.currentTimeMillis();
            NewsSearchResponse result = newsSearchService.searchWithRrf(retriever, request.page() * 20);
            queryMetrics.recordSearchTime(System.currentTimeMillis() - searchStart);
            queryMetrics.recordQueryTime(startTime);
            return ResponseEntity.ok(result);

        } catch (IllegalArgumentException e) {
            queryMetrics.recordError();
            queryMetrics.recordQueryTime(startTime);
            return ResponseEntity.badRequest().body(
                new ErrorResponse("NQL 문법 오류: " + e.getMessage(), "NQL_PARSE_ERROR", "/api/query")
            );
        } catch (IOException e) {
            queryMetrics.recordError();
            queryMetrics.recordQueryTime(startTime);
            return ResponseEntity.internalServerError().body(
                new ErrorResponse("Elasticsearch 연결 오류. 잠시 후 다시 시도해주세요.", "ES_CONNECTION_ERROR", "/api/query")
            );
        } catch (Exception e) {
            queryMetrics.recordError();
            queryMetrics.recordQueryTime(startTime);
            return ResponseEntity.internalServerError().body(
                new ErrorResponse("예상치 못한 오류가 발생했습니다: " + e.getMessage(), "INTERNAL_SERVER_ERROR", "/api/query")
            );
        }
    }
}
