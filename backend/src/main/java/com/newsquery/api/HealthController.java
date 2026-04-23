package com.newsquery.api;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import com.newsquery.embedding.EmbeddingClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/health")
public class HealthController {

    private final ElasticsearchClient esClient;
    private final EmbeddingClient embeddingClient;

    public HealthController(ElasticsearchClient esClient, EmbeddingClient embeddingClient) {
        this.esClient = esClient;
        this.embeddingClient = embeddingClient;
    }

    @GetMapping
    public ResponseEntity<?> health() {
        Map<String, Object> health = new HashMap<>();
        health.put("status", "UP");
        health.put("timestamp", System.currentTimeMillis());

        Map<String, Object> components = new HashMap<>();

        // Elasticsearch 상태 확인
        try {
            var info = esClient.info();
            components.put("elasticsearch", Map.of(
                "status", "UP",
                "version", info.version().number()
            ));
        } catch (Exception e) {
            components.put("elasticsearch", Map.of(
                "status", "DOWN",
                "error", e.getMessage()
            ));
        }

        // 임베딩 서비스 상태 확인
        try {
            float[] result = embeddingClient.embed("test");
            String embeddingStatus = result != null ? "UP" : "DOWN";
            components.put("embedding", Map.of("status", embeddingStatus));
        } catch (Exception e) {
            components.put("embedding", Map.of(
                "status", "DOWN",
                "error", "서비스에 연결할 수 없습니다"
            ));
        }

        health.put("components", components);

        // 모든 컴포넌트가 UP인지 확인
        boolean allUp = components.values().stream()
            .allMatch(v -> v instanceof Map && "UP".equals(((Map<?, ?>) v).get("status")));

        if (!allUp) {
            health.put("status", "DEGRADED");
            return ResponseEntity.status(503).body(health);
        }

        return ResponseEntity.ok(health);
    }
}
