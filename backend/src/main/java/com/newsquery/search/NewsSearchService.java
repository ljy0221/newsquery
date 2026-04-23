package com.newsquery.search;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.newsquery.api.NewsHit;
import com.newsquery.api.NewsSearchResponse;
import org.elasticsearch.client.Request;
import org.elasticsearch.client.RestClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class NewsSearchService {

    private final ElasticsearchClient esClient;
    private final RestClient restClient;

    @Value("${newsquery.elasticsearch.index:news}")
    private String index;

    public NewsSearchService(ElasticsearchClient esClient, RestClient restClient) {
        this.esClient = esClient;
        this.restClient = restClient;
    }

    /**
     * ESQueryBuilder가 생성한 ES Query DSL을 실행하고 결과를 반환한다.
     *
     * @param query ESQueryBuilder.build()가 반환한 ObjectNode (bool query 등)
     * @return 검색 결과
     */
    public NewsSearchResponse search(ObjectNode query) throws IOException {
        String searchBody = "{\"query\":" + query.toString() + "}";

        SearchResponse<ObjectNode> response = esClient.search(
                s -> s.withJson(new StringReader(searchBody)).index(index),
                ObjectNode.class
        );

        List<NewsHit> hits = response.hits().hits().stream()
                .filter(hit -> hit.source() != null)
                .map(hit -> mapHit(hit.id(), hit.source()))
                .collect(Collectors.toList());

        long total = response.hits().total() != null
                ? response.hits().total().value()
                : hits.size();

        return new NewsSearchResponse(total, hits);
    }

    /**
     * RRFScorer가 반환한 retriever 노드를 ES 8.x 호환 포맷으로 변환하여 검색한다.
     *
     * <ul>
     *   <li>BM25 단독 (standard retriever): {@code {"query": {...}, "size": 20}}</li>
     *   <li>하이브리드 (rrf retriever):       {@code {"query": {...}, "knn": {...}, "size": 20}}</li>
     * </ul>
     *
     * ES retriever API(8.9+ 유료 기능) 대신 표준 query + knn 파라미터를 사용하므로
     * 무료 Basic 라이선스에서도 동작한다.
     *
     * @param retriever RRFScorer.buildRetriever()가 반환한 JsonNode
     */
    public NewsSearchResponse searchWithRrf(JsonNode retriever, int from) throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode body = mapper.createObjectNode();
        body.put("size", 20);
        body.put("from", from);

        if (retriever.has("standard")) {
            // BM25 단독: standard retriever → {"query": {...}}
            body.set("query", retriever.path("standard").path("query"));
        } else if (retriever.has("rrf")) {
            // 하이브리드: rrf retriever → {"query": {...}, "knn": {...}}
            for (JsonNode r : retriever.path("rrf").path("retrievers")) {
                if (r.has("standard")) {
                    body.set("query", r.path("standard").path("query"));
                } else if (r.has("knn")) {
                    body.set("knn", r.path("knn"));
                }
            }
        }

        body.set("sort", mapper.readTree(
                "[{\"_score\":{\"order\":\"desc\"}},{\"publishedAt\":{\"order\":\"desc\"}}]"));

        Request request = new Request("POST", "/" + index + "/_search");
        request.setJsonEntity(body.toString());
        org.elasticsearch.client.Response rawResp = restClient.performRequest(request);

        JsonNode root     = mapper.readTree(rawResp.getEntity().getContent());
        JsonNode hitsNode = root.path("hits");
        long total        = hitsNode.path("total").path("value").asLong(0);

        List<NewsHit> hits = new ArrayList<>();
        for (JsonNode hit : hitsNode.path("hits")) {
            JsonNode src = hit.path("_source");
            if (!src.isMissingNode() && src.isObject()) {
                hits.add(new NewsHit(
                        hit.path("_id").asText(),
                        src.path("title").asText(""),
                        src.path("source").asText(""),
                        src.path("sentiment").asText(""),
                        src.path("country").asText(""),
                        src.path("publishedAt").asText(""),
                        hit.path("_score").asDouble(0.0),
                        src.path("url").asText("")
                ));
            }
        }

        return new NewsSearchResponse(total, hits);
    }

    private NewsHit mapHit(String id, ObjectNode src) {
        return new NewsHit(
                id,
                src.path("title").asText(""),
                src.path("source").asText(""),
                src.path("sentiment").asText(""),
                src.path("country").asText(""),
                src.path("publishedAt").asText(""),
                src.path("score").asDouble(0.0),
                src.path("url").asText("")
        );
    }
}
