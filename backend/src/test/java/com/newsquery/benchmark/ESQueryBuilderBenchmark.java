package com.newsquery.benchmark;

import com.newsquery.nql.NQLExpression;
import com.newsquery.query.ESQueryBuilder;
import org.openjdk.jmh.annotations.*;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Elasticsearch 쿼리 빌드 성능 마이크로벤치마크
 * 목표: NQL Expression → ES Query DSL 변환 속도 측정
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
public class ESQueryBuilderBenchmark {

    private ESQueryBuilder builder;
    private NQLExpression simpleKeyword;
    private NQLExpression keywordWithSentiment;
    private NQLExpression complexAndOr;
    private NQLExpression orExpression;
    private NQLExpression inExpression;
    private NQLExpression betweenExpression;

    @Setup(Level.Trial)
    public void setUp() {
        builder = new ESQueryBuilder();

        // 테스트용 Expression 객체 미리 생성 (불변이므로 재사용 가능)
        simpleKeyword = new NQLExpression.KeywordExpr("AI", null);

        keywordWithSentiment = new NQLExpression.AndExpr(
            new NQLExpression.KeywordExpr("technology", null),
            new NQLExpression.CompareExpr("sentiment", "==", "positive")
        );

        complexAndOr = new NQLExpression.AndExpr(
            new NQLExpression.OrExpr(
                new NQLExpression.KeywordExpr("AI", 2.0),
                new NQLExpression.KeywordExpr("tech", null)
            ),
            new NQLExpression.CompareExpr("sentiment", "!=", "negative")
        );

        orExpression = new NQLExpression.OrExpr(
            new NQLExpression.KeywordExpr("AI", null),
            new NQLExpression.KeywordExpr("machine learning", null)
        );

        inExpression = new NQLExpression.InExpr(
            "source",
            List.of("Reuters", "Bloomberg", "AP")
        );

        betweenExpression = new NQLExpression.BetweenExpr(
            "publishedAt",
            "2026-03-01",
            "2026-04-23"
        );
    }

    @Benchmark
    public Object buildSimpleKeyword() {
        return builder.build(simpleKeyword);
    }

    @Benchmark
    public Object buildKeywordWithSentiment() {
        return builder.build(keywordWithSentiment);
    }

    @Benchmark
    public Object buildComplexAndOr() {
        return builder.build(complexAndOr);
    }

    @Benchmark
    public Object buildOrExpression() {
        return builder.build(orExpression);
    }

    @Benchmark
    public Object buildInExpression() {
        return builder.build(inExpression);
    }

    @Benchmark
    public Object buildBetweenExpression() {
        return builder.build(betweenExpression);
    }

    @TearDown(Level.Trial)
    public void tearDown() {
        builder = null;
    }
}
