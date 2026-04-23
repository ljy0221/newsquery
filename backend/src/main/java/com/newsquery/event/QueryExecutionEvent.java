package com.newsquery.event;

import java.time.LocalDateTime;

public record QueryExecutionEvent(
    String userId,
    String nql,
    long responseTimeMs,
    int totalHits,
    boolean success,
    String errorMessage,
    LocalDateTime executedAt
) {
    public static QueryExecutionEvent success(String userId, String nql, long responseTimeMs, int totalHits) {
        return new QueryExecutionEvent(
            userId,
            nql,
            responseTimeMs,
            totalHits,
            true,
            null,
            LocalDateTime.now()
        );
    }

    public static QueryExecutionEvent error(String userId, String nql, String errorMessage) {
        return new QueryExecutionEvent(
            userId,
            nql,
            0,
            0,
            false,
            errorMessage,
            LocalDateTime.now()
        );
    }
}
