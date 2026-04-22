package com.newsquery.search;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PerformanceMetrics {
    private static final Logger log = LoggerFactory.getLogger(PerformanceMetrics.class);

    private long queryStartTime;
    private long parseEndTime;
    private long buildQueryEndTime;
    private long embeddingEndTime;
    private long searchEndTime;

    public void recordParseTime() {
        parseEndTime = System.currentTimeMillis();
        log.debug("NQL 파싱: {}ms", parseEndTime - queryStartTime);
    }

    public void recordBuildQueryTime() {
        buildQueryEndTime = System.currentTimeMillis();
        log.debug("쿼리 빌드: {}ms", buildQueryEndTime - parseEndTime);
    }

    public void recordEmbeddingTime() {
        embeddingEndTime = System.currentTimeMillis();
        log.debug("임베딩: {}ms", embeddingEndTime - buildQueryEndTime);
    }

    public void recordSearchTime() {
        searchEndTime = System.currentTimeMillis();
        log.debug("ES 검색: {}ms", searchEndTime - embeddingEndTime);
    }

    public long getTotalTime() {
        return searchEndTime - queryStartTime;
    }

    public void start() {
        queryStartTime = System.currentTimeMillis();
    }

    public void logSummary(int totalHits) {
        log.info("전체 검색 완료: {}ms, 결과: {}건",
            getTotalTime(),
            totalHits);
    }

    public boolean isSlowQuery() {
        return getTotalTime() > 5000; // 5초 이상 = 느린 쿼리
    }
}
