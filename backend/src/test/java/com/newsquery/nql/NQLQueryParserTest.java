package com.newsquery.nql;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NQLQueryParserTest {

    private final NQLQueryParser parser = new NQLQueryParser();

    @Test
    void parseAndBuildQuery() {
        var result = parser.parseToQuery("keyword(\"HBM\") * 2.0 AND sentiment != \"negative\"");
        assertThat(result.toString()).contains("HBM");
        assertThat(result.toString()).contains("negative");
    }

    @Test
    void invalidNql_throwsException() {
        assertThatThrownBy(() -> parser.parseToQuery("INVALID @@@ query"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("NQL 파싱 오류");
    }

    @Test
    void parseToExpression_returnsValidExpression() {
        NQLExpression result = parser.parseToExpression("keyword(\"test\")");
        assertThat(result).isInstanceOf(NQLExpression.KeywordExpr.class);
    }

    @Test
    void buildQuery_fromExpression() {
        var expr = new NQLExpression.KeywordExpr("HBM", null);
        var result = parser.buildQuery(expr);
        assertThat(result.toString()).contains("HBM");
    }

    @Test
    void complexQuery_andOr() {
        var result = parser.parseToQuery("keyword(\"AI\") AND sentiment == \"positive\" OR country IN [\"KR\", \"US\"]");
        assertThat(result.toString()).contains("AI");
        assertThat(result.toString()).contains("positive");
    }

    @Test
    void matchAllQuery() {
        var result = parser.parseToQuery("*");
        assertThat(result.toString()).contains("match_all");
    }

    @Test
    void multipleKeywords_with_boosts() {
        var result = parser.parseToQuery("keyword(\"HBM\") * 2.0 AND keyword(\"GPU\") * 1.5");
        assertThat(result.toString()).contains("HBM");
        assertThat(result.toString()).contains("GPU");
    }

    @Test
    void dateRange_query() {
        var result = parser.parseToQuery("publishedAt >= \"2024-01-01\" AND publishedAt <= \"2024-12-31\"");
        assertThat(result.toString()).contains("publishedAt");
        assertThat(result.toString()).contains("2024");
    }

    @Test
    void sentimentNotEquals() {
        var result = parser.parseToQuery("sentiment != \"negative\"");
        assertThat(result.toString()).contains("negative");
    }

    @Test
    void categoryComparison() {
        var result = parser.parseToQuery("category == \"TECH\"");
        assertThat(result.toString()).contains("category");
        assertThat(result.toString()).contains("TECH");
    }

    @Test
    void scoreGreaterThan() {
        var result = parser.parseToQuery("score > 5.0");
        assertThat(result.toString()).contains("score");
    }
}
