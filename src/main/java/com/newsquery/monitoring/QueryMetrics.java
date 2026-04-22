package com.newsquery.monitoring;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Component
public class QueryMetrics {
    private final Counter queryCount;
    private final Counter errorCount;
    private final Timer queryTimer;
    private final Timer parseTimer;
    private final Timer buildQueryTimer;
    private final Timer embeddingTimer;
    private final Timer searchTimer;

    public QueryMetrics(MeterRegistry meterRegistry) {
        this.queryCount = Counter.builder("nql.query.total")
            .description("총 NQL 쿼리 수")
            .register(meterRegistry);

        this.errorCount = Counter.builder("nql.query.errors")
            .description("NQL 쿼리 에러 수")
            .register(meterRegistry);

        this.queryTimer = Timer.builder("nql.query.duration")
            .description("전체 쿼리 실행 시간")
            .publishPercentiles(0.5, 0.95, 0.99)
            .register(meterRegistry);

        this.parseTimer = Timer.builder("nql.parse.duration")
            .description("NQL 파싱 시간")
            .register(meterRegistry);

        this.buildQueryTimer = Timer.builder("nql.build_query.duration")
            .description("쿼리 빌드 시간")
            .register(meterRegistry);

        this.embeddingTimer = Timer.builder("nql.embedding.duration")
            .description("임베딩 시간")
            .register(meterRegistry);

        this.searchTimer = Timer.builder("nql.search.duration")
            .description("Elasticsearch 검색 시간")
            .register(meterRegistry);
    }

    public void recordQuery() {
        queryCount.increment();
    }

    public void recordError() {
        errorCount.increment();
    }

    public void recordQueryTime(long startTime) {
        queryTimer.record(System.currentTimeMillis() - startTime, TimeUnit.MILLISECONDS);
    }

    public void recordParseTime(long duration) {
        parseTimer.record(duration, TimeUnit.MILLISECONDS);
    }

    public void recordBuildQueryTime(long duration) {
        buildQueryTimer.record(duration, TimeUnit.MILLISECONDS);
    }

    public void recordEmbeddingTime(long duration) {
        embeddingTimer.record(duration, TimeUnit.MILLISECONDS);
    }

    public void recordSearchTime(long duration) {
        searchTimer.record(duration, TimeUnit.MILLISECONDS);
    }
}
