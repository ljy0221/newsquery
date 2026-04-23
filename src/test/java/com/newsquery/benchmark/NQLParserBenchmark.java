package com.newsquery.benchmark;

import com.newsquery.nql.NQLQueryParser;
import org.openjdk.jmh.annotations.*;
import java.util.concurrent.TimeUnit;

/**
 * NQL 파싱 성능 마이크로벤치마크
 * 목표: NQL 파싱이 전체 응답시간(~36ms)의 몇 %인지 수치화
 *
 * 실행: ./gradlew jmhBenchmark
 * 결과: measurements/jmh-results.json
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@State(Scope.Benchmark)
@Warmup(iterations = 3, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 2, timeUnit = TimeUnit.SECONDS)
@Fork(1)
public class NQLParserBenchmark {

    private NQLQueryParser parser;

    @Setup(Level.Trial)
    public void setUp() {
        parser = new NQLQueryParser();
    }

    @Benchmark
    public Object parseSimpleKeyword() {
        return parser.parseToExpression("keyword(\"AI\")");
    }

    @Benchmark
    public Object parseKeywordWithSentiment() {
        return parser.parseToExpression(
            "keyword(\"technology\") AND sentiment == \"positive\""
        );
    }

    @Benchmark
    public Object parseComplexAndOr() {
        return parser.parseToExpression(
            "(keyword(\"AI\") * 2.0 OR keyword(\"tech\")) AND sentiment != \"negative\""
        );
    }

    @Benchmark
    public Object parseOrQuery() {
        return parser.parseToExpression(
            "keyword(\"AI\") OR keyword(\"machine learning\")"
        );
    }

    @Benchmark
    public Object parseWithSourceIn() {
        return parser.parseToExpression(
            "keyword(\"blockchain\") AND source IN [\"Reuters\", \"Bloomberg\"]"
        );
    }

    @Benchmark
    public Object parseBetweenRange() {
        return parser.parseToExpression(
            "keyword(\"technology\") AND publishedAt BETWEEN \"2026-03-01\" AND \"2026-04-23\""
        );
    }

    @Benchmark
    public Object parseContainsPattern() {
        return parser.parseToExpression(
            "source CONTAINS \"Reuters\""
        );
    }

    @Benchmark
    public Object parseLikePattern() {
        return parser.parseToExpression(
            "keyword(\"AI\") AND source LIKE \"tech\""
        );
    }

    @Benchmark
    public Object parseAggregationGroupByCategory() {
        return parser.parseToExpression(
            "keyword(\"AI\") GROUP BY category LIMIT 5"
        );
    }

    @Benchmark
    public Object parseAggregationGroupBySentiment() {
        return parser.parseToExpression(
            "keyword(\"technology\") GROUP BY sentiment"
        );
    }

    @Benchmark
    public Object parseWithBoostAndGroupBy() {
        return parser.parseToExpression(
            "keyword(\"news\") BOOST recency(publishedAt) GROUP BY source LIMIT 10"
        );
    }

    @Benchmark
    public Object parseMatchAll() {
        return parser.parseToExpression("*");
    }

    @TearDown(Level.Trial)
    public void tearDown() {
        parser = null;
    }
}
