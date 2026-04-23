package com.newsquery.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * Phase 7: Kafka 기반 이벤트 발행
 * 쿼리 실행 이벤트를 Kafka에 발행하여 분산 처리
 *
 * 현재: 로컬 EventPublisher 사용
 * Phase 7.1: Kafka 활성화 후 사용
 */
@Component
@ConditionalOnProperty(name = "spring.kafka.bootstrap-servers", matchIfMissing = false)
public class KafkaEventProducer {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private static final String TOPIC = "query-execution-events";

    public KafkaEventProducer(KafkaTemplate<String, String> kafkaTemplate, ObjectMapper objectMapper) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
    }

    public void publishEvent(QueryExecutionEvent event) {
        try {
            String eventJson = objectMapper.writeValueAsString(event);
            kafkaTemplate.send(TOPIC, event.userId(), eventJson)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        System.err.println("[KAFKA_PRODUCER] Failed to publish event: " + ex.getMessage());
                    } else {
                        System.out.println("[KAFKA_PRODUCER] Event published: " + event.nql());
                    }
                });
        } catch (Exception e) {
            System.err.println("[KAFKA_PRODUCER] Error publishing event: " + e.getMessage());
        }
    }
}
