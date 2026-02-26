package com.newsquery.api;

import java.util.List;

public record NewsSearchResponse(long total, List<NewsHit> hits) {}
