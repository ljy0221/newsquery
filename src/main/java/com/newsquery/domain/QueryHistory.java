package com.newsquery.domain;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "query_history", indexes = {
    @Index(name = "idx_query_history_user_id", columnList = "user_id"),
    @Index(name = "idx_query_history_executed_at", columnList = "executed_at")
})
public class QueryHistory {

    @Id
    @Column(name = "id")
    private String id;

    @Column(name = "user_id", nullable = false)
    private String userId;

    @Column(name = "nql", columnDefinition = "TEXT", nullable = false)
    private String nql;

    @Column(name = "response_time_ms", nullable = false)
    private double responseTimeMs;

    @Column(name = "total_hits", nullable = false)
    private long totalHits;

    @Column(name = "success", nullable = false)
    private boolean success;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @CreationTimestamp
    @Column(name = "executed_at", nullable = false, updatable = false)
    private LocalDateTime executedAt;

    public QueryHistory() {}

    public QueryHistory(String userId, String nql, double responseTimeMs, long totalHits, boolean success, String errorMessage) {
        this.id = UUID.randomUUID().toString();
        this.userId = userId;
        this.nql = nql;
        this.responseTimeMs = responseTimeMs;
        this.totalHits = totalHits;
        this.success = success;
        this.errorMessage = errorMessage;
        this.executedAt = LocalDateTime.now();
    }

    public String getId() { return id; }
    public String getUserId() { return userId; }
    public String getNql() { return nql; }
    public double getResponseTimeMs() { return responseTimeMs; }
    public long getTotalHits() { return totalHits; }
    public boolean isSuccess() { return success; }
    public String getErrorMessage() { return errorMessage; }
    public LocalDateTime getExecutedAt() { return executedAt; }

    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
}
