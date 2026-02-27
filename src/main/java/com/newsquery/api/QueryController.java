package com.newsquery.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.newsquery.embedding.EmbeddingClient;
import com.newsquery.nql.KeywordExtractor;
import com.newsquery.nql.NQLExpression;
import com.newsquery.nql.NQLQueryParser;
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

    public QueryController(NQLQueryParser nqlQueryParser,
                           KeywordExtractor keywordExtractor,
                           EmbeddingClient embeddingClient,
                           RRFScorer rrfScorer,
                           NewsSearchService newsSearchService) {
        this.nqlQueryParser    = nqlQueryParser;
        this.keywordExtractor  = keywordExtractor;
        this.embeddingClient   = embeddingClient;
        this.rrfScorer         = rrfScorer;
        this.newsSearchService = newsSearchService;
    }

    @PostMapping("/query")
    public ResponseEntity<?> query(@RequestBody QueryRequest request) {
        if (request.nql() == null || request.nql().isBlank()) {
            return ResponseEntity.badRequest().body("nql 필드가 비어있습니다.");
        }
        try {
            // 1. NQL → IR
            NQLExpression expr = nqlQueryParser.parseToExpression(request.nql());

            // 2. IR → ES bool query
            var boolQuery = nqlQueryParser.buildQuery(expr);

            // 3. keyword() 표현식 추출 → 임베딩 (실패 시 null → BM25 폴백)
            List<NQLExpression.KeywordExpr> keywords = keywordExtractor.extract(expr);
            float[] vector = null;
            if (!keywords.isEmpty()) {
                String keywordText = keywords.stream()
                        .map(NQLExpression.KeywordExpr::text)
                        .collect(Collectors.joining(" "));
                vector = embeddingClient.embed(keywordText);
            }

            // 4. RRF retriever 구성 (vector == null 이면 BM25 단독)
            JsonNode retriever = rrfScorer.buildRetriever(boolQuery, keywords, vector);

            // 5. ES 검색
            NewsSearchResponse result = newsSearchService.searchWithRrf(retriever, request.page() * 20);
            return ResponseEntity.ok(result);

        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (IOException e) {
            return ResponseEntity.internalServerError().body("ES 검색 오류: " + e.getMessage());
        }
    }
}
