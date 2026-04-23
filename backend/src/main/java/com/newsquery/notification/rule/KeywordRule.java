package com.newsquery.notification.rule;

import com.newsquery.event.QueryExecutionEvent;
import com.newsquery.service.KeywordSubscriptionService;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class KeywordRule implements NotificationRule {

    private final KeywordSubscriptionService keywordSubscriptionService;

    public KeywordRule(KeywordSubscriptionService keywordSubscriptionService) {
        this.keywordSubscriptionService = keywordSubscriptionService;
    }

    @Override
    public boolean evaluate(QueryExecutionEvent event) {
        if (!event.success() || event.totalHits() == 0) {
            return false;
        }

        List<String> subscriptions = keywordSubscriptionService.getSubscribedKeywords(event.userId());
        if (subscriptions.isEmpty()) {
            return false;
        }

        String nqlLower = event.nql().toLowerCase();
        return subscriptions.stream()
            .anyMatch(keyword -> nqlLower.contains(keyword.toLowerCase()));
    }

    @Override
    public String getRuleName() {
        return "KEYWORD_ALERT";
    }

    @Override
    public String generateMessage(QueryExecutionEvent event) {
        List<String> subscriptions = keywordSubscriptionService.getSubscribedKeywords(event.userId());
        String matchedKeyword = subscriptions.stream()
            .filter(keyword -> event.nql().toLowerCase().contains(keyword.toLowerCase()))
            .findFirst()
            .orElse("구독 키워드");
        return String.format("🔔 키워드 알림: '%s' 관련 검색에서 %d개의 결과를 찾았습니다.",
            matchedKeyword, event.totalHits());
    }
}
