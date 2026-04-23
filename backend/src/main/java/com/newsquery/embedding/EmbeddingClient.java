package com.newsquery.embedding;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.newsquery.cache.NQLCacheKeyGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

@Component
public class EmbeddingClient {

    private static final Logger log = LoggerFactory.getLogger(EmbeddingClient.class);

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(3))
            .build();

    private final ObjectMapper mapper = new ObjectMapper();

    @Value("${newsquery.embedding.service-url:http://localhost:8000}")
    private String serviceUrl;

    /**
     * Phase 4: 텍스트를 임베딩 벡터로 변환 (Redis 캐시 24시간)
     * 서비스가 응답하지 않으면 null을 반환하여 BM25 단독 검색으로 폴백한다.
     * 캐시 히트 시: 1-2ms / 캐시 미스 시: 5-10ms
     */
    @Cacheable(
            cacheNames = "embeddings",
            key = "T(com.newsquery.cache.NQLCacheKeyGenerator).generateEmbeddingKey(#text)",
            sync = true
    )
    public float[] embed(String text) {
        try {
            ObjectNode body = mapper.createObjectNode();
            body.put("text", text);

            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(serviceUrl + "/embed/single"))
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofSeconds(5))
                    .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                    .build();

            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() != 200) {
                log.warn("임베딩 서비스 오류 ({}): {}", resp.statusCode(), resp.body());
                return null;
            }

            ObjectNode json = (ObjectNode) mapper.readTree(resp.body());
            ArrayNode vectorNode = (ArrayNode) json.get("vector");
            float[] vector = new float[vectorNode.size()];
            for (int i = 0; i < vectorNode.size(); i++) {
                vector[i] = (float) vectorNode.get(i).asDouble();
            }
            return vector;

        } catch (Exception e) {
            log.warn("임베딩 서비스 호출 실패, BM25 폴백: {}", e.getMessage());
            return null;
        }
    }
}
