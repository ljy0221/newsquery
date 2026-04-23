package com.newsquery.domain;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Phase 5: 검색 히스토리 (Query History)
 * 모든 NQL 쿼리 실행을 기록하여 사용 패턴 분석
 */
public class QueryHistory {

    private final String id;
    private final String userId;
    private final String nql;
    private final double responseTimeMs;
    private final int totalHits;
    private final String errorMessage;
    private final LocalDateTime executedAt;

    public QueryHistory(String userId, String nql, double responseTimeMs, int totalHits) {
        this.id = UUID.randomUUID().toString();
        this.userId = userId;
        this.nql = nql;
        this.responseTimeMs = responseTimeMs;
        this.totalHits = totalHits;
        this.errorMessage = null;
        this.executedAt = LocalDateTime.now();
    }

    public QueryHistory(String userId, String nql, String errorMessage) {
        this.id = UUID.randomUUID().toString();
        this.userId = userId;
        this.nql = nql;
        this.responseTimeMs = 0.0;
        this.totalHits = 0;
        this.errorMessage = errorMessage;
        this.executedAt = LocalDateTime.now();
    }

    public QueryHistory(String id, String userId, String nql, double responseTimeMs,
                        int totalHits, String errorMessage, LocalDateTime executedAt) {
        this.id = id;
        this.userId = userId;
        this.nql = nql;
        this.responseTimeMs = responseTimeMs;
        this.totalHits = totalHits;
        this.errorMessage = errorMessage;
        this.executedAt = executedAt;
    }

    // Getters
    public String getId() { return id; }
    public String getUserId() { return userId; }
    public String getNql() { return nql; }
    public double getResponseTimeMs() { return responseTimeMs; }
    public int getTotalHits() { return totalHits; }
    public String getErrorMessage() { return errorMessage; }
    public LocalDateTime getExecutedAt() { return executedAt; }

    public boolean isSuccess() {
        return errorMessage == null;
    }

    @Override
    public String toString() {
        return "QueryHistory{" +
                "id='" + id + '\'' +
                ", userId='" + userId + '\'' +
                ", nql='" + nql + '\'' +
                ", responseTimeMs=" + responseTimeMs +
                ", totalHits=" + totalHits +
                ", executedAt=" + executedAt +
                '}';
    }
}
