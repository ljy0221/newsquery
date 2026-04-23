package com.newsquery.notification.rule;

import com.newsquery.event.QueryExecutionEvent;
import com.newsquery.service.SavedQueryService;
import org.springframework.stereotype.Component;

@Component
public class PerformanceRule implements NotificationRule {

    private final SavedQueryService savedQueryService;
    private static final double PERFORMANCE_THRESHOLD_RATIO = 1.5; // 50% 이상 느린 경우

    public PerformanceRule(SavedQueryService savedQueryService) {
        this.savedQueryService = savedQueryService;
    }

    @Override
    public boolean evaluate(QueryExecutionEvent event) {
        if (!event.success()) {
            return false;
        }

        try {
            var stats = savedQueryService.getQueryStats(event.userId(), event.nql());
            if (stats.isEmpty()) {
                return false;
            }

            Double avgResponseTime = (Double) stats.get("avg_response_time_ms");
            if (avgResponseTime == null) {
                return false;
            }

            return event.responseTimeMs() > avgResponseTime * PERFORMANCE_THRESHOLD_RATIO;
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public String getRuleName() {
        return "PERFORMANCE_DEGRADATION";
    }

    @Override
    public String generateMessage(QueryExecutionEvent event) {
        var stats = savedQueryService.getQueryStats(event.userId(), event.nql());
        Double avgResponseTime = (Double) stats.get("avg_response_time_ms");
        long avgMs = Math.round(avgResponseTime != null ? avgResponseTime : 0);
        return String.format("⚠️ 성능 저하: 쿼리 응답 시간이 평균(%dms)의 150%% 이상입니다. (현재: %dms)",
            avgMs, event.responseTimeMs());
    }
}
