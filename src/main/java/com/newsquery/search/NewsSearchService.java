package com.newsquery.search;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.newsquery.api.NewsHit;
import com.newsquery.api.NewsSearchResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.StringReader;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class NewsSearchService {

    private final ElasticsearchClient esClient;

    @Value("${newsquery.elasticsearch.index:news}")
    private String index;

    public NewsSearchService(ElasticsearchClient esClient) {
        this.esClient = esClient;
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
     * RRF retriever 노드를 사용하는 하이브리드 검색 (ES 8.9+ Retriever API).
     *
     * @param retriever RRFScorer.buildRetriever()가 반환한 JsonNode
     */
    public NewsSearchResponse searchWithRrf(JsonNode retriever) throws IOException {
        ObjectNode body = new ObjectMapper().createObjectNode();
        body.set("retriever", retriever);
        body.put("size", 20);

        SearchResponse<ObjectNode> response = esClient.search(
                s -> s.withJson(new StringReader(body.toString())).index(index),
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
