package com.newsquery.api;

import com.newsquery.domain.KeywordSubscription;
import com.newsquery.service.KeywordSubscriptionService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Phase 5.1: 키워드 구독 관리 API
 * 사용자의 관심 키워드 구독/해제
 */
@RestController
@RequestMapping("/api/keywords/subscriptions")
public class KeywordSubscriptionController {

    private final KeywordSubscriptionService subscriptionService;
    private static final String DEFAULT_USER_ID = "anonymous";

    public KeywordSubscriptionController(KeywordSubscriptionService subscriptionService) {
        this.subscriptionService = subscriptionService;
    }

    /**
     * 키워드 구독
     * POST /api/keywords/subscriptions
     */
    @PostMapping
    public ResponseEntity<KeywordSubscription> subscribe(@RequestBody SubscribeRequest request) {
        if (request.keyword() == null || request.keyword().isBlank()) {
            return ResponseEntity.badRequest().build();
        }

        KeywordSubscription subscription = subscriptionService.subscribe(
            DEFAULT_USER_ID,
            request.keyword()
        );

        return ResponseEntity.ok(subscription);
    }

    /**
     * 구독 키워드 목록 조회
     * GET /api/keywords/subscriptions
     */
    @GetMapping
    public ResponseEntity<List<KeywordSubscription>> getSubscriptions() {
        List<KeywordSubscription> subscriptions = subscriptionService.getSubscriptions(DEFAULT_USER_ID);
        return ResponseEntity.ok(subscriptions);
    }

    /**
     * 구독 키워드만 조회
     * GET /api/keywords/subscriptions/keywords
     */
    @GetMapping("/keywords")
    public ResponseEntity<?> getKeywords() {
        List<String> keywords = subscriptionService.getSubscribedKeywords(DEFAULT_USER_ID);
        return ResponseEntity.ok(Map.of("keywords", keywords));
    }

    /**
     * 키워드 구독 해제
     * DELETE /api/keywords/subscriptions/{id}
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<?> unsubscribe(@PathVariable String id) {
        try {
            subscriptionService.unsubscribe(id);
            return ResponseEntity.ok(Map.of("status", "success", "subscriptionId", id));
        } catch (Exception e) {
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * 요청 DTO
     */
    public record SubscribeRequest(String keyword) {}
}
