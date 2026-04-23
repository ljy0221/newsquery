package com.newsquery.api;

public record NewsHit(
        String id,
        String title,
        String source,
        String sentiment,
        String country,
        String publishedAt,
        double score,
        String url
) {}
