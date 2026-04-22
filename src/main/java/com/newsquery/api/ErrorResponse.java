package com.newsquery.api;

import com.fasterxml.jackson.annotation.JsonProperty;

public record ErrorResponse(
    String message,
    String errorCode,
    @JsonProperty("timestamp")
    long timestamp,
    String path
) {
    public ErrorResponse(String message, String errorCode, String path) {
        this(message, errorCode, System.currentTimeMillis(), path);
    }

    public ErrorResponse(String message, String errorCode) {
        this(message, errorCode, System.currentTimeMillis(), "");
    }
}
