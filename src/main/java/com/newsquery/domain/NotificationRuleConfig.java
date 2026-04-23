package com.newsquery.domain;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Phase 7: 동적 알림 규칙 설정
 * DB에서 규칙 조건을 동적으로 로드하기 위한 도메인 모델
 * 프로덕션: PostgreSQL에 저장
 */
public class NotificationRuleConfig {
    private final String id;
    private final String userId;
    private final String ruleName;
    private final String condition;  // JSON 형식: {"type": "PERFORMANCE", "threshold": 1.5}
    private final boolean enabled;
    private final LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public NotificationRuleConfig(String userId, String ruleName, String condition) {
        this.id = UUID.randomUUID().toString();
        this.userId = userId;
        this.ruleName = ruleName;
        this.condition = condition;
        this.enabled = true;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    public String getId() {
        return id;
    }

    public String getUserId() {
        return userId;
    }

    public String getRuleName() {
        return ruleName;
    }

    public String getCondition() {
        return condition;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}
