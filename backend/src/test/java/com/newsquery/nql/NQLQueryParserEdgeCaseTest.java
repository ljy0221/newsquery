package com.newsquery.nql;

import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NQLQueryParserEdgeCaseTest {

    private final NQLQueryParser parser = new NQLQueryParser();

    @Test
    void parseToQuery_withComplexBoolean() {
        ObjectNode result = parser.parseToQuery("keyword(\"A\") AND keyword(\"B\") OR keyword(\"C\")");
        assertThat(result.toString()).contains("A");
        assertThat(result.toString()).contains("B");
        assertThat(result.toString()).contains("C");
    }

    @Test
    void parseToQuery_withNestedParentheses() {
        ObjectNode result = parser.parseToQuery("(keyword(\"A\") AND sentiment == \"positive\") OR (keyword(\"B\") AND sentiment == \"negative\")");
        assertThat(result.toString()).contains("positive");
        assertThat(result.toString()).contains("negative");
    }

    @Test
    void parseToQuery_withHighBoost() {
        ObjectNode result = parser.parseToQuery("keyword(\"urgent\") * 5.0");
        assertThat(result.toString()).contains("urgent");
    }

    @Test
    void parseToQuery_withDateRange() {
        ObjectNode result = parser.parseToQuery("publishedAt >= \"2024-01-01\" AND publishedAt <= \"2024-12-31\"");
        assertThat(result.toString()).contains("publishedAt");
    }

    @Test
    void parseToQuery_withMultipleInClause() {
        ObjectNode result = parser.parseToQuery("source IN [\"Reuters\", \"Bloomberg\", \"AP\"]");
        assertThat(result.toString()).contains("Reuters");
        assertThat(result.toString()).contains("Bloomberg");
    }

    @Test
    void parseToExpression_keywordOnly() {
        NQLExpression expr = parser.parseToExpression("keyword(\"test\")");
        assertThat(expr).isInstanceOf(NQLExpression.KeywordExpr.class);
    }

    @Test
    void parseToExpression_compareExpression() {
        NQLExpression expr = parser.parseToExpression("sentiment == \"positive\"");
        assertThat(expr).isInstanceOf(NQLExpression.CompareExpr.class);
    }

    @Test
    void parseToExpression_andExpression() {
        NQLExpression expr = parser.parseToExpression("keyword(\"A\") AND sentiment == \"positive\"");
        assertThat(expr).isInstanceOf(NQLExpression.AndExpr.class);
    }

    @Test
    void parseToExpression_orExpression() {
        NQLExpression expr = parser.parseToExpression("sentiment == \"positive\" OR sentiment == \"neutral\"");
        assertThat(expr).isInstanceOf(NQLExpression.OrExpr.class);
    }

    @Test
    void parseToExpression_notExpression() {
        NQLExpression expr = parser.parseToExpression("!sentiment == \"negative\"");
        assertThat(expr).isInstanceOf(NQLExpression.NotExpr.class);
    }

    @Test
    void buildQuery_fromSimpleExpression() {
        var expr = new NQLExpression.KeywordExpr("test", null);
        ObjectNode result = parser.buildQuery(expr);
        assertThat(result.toString()).contains("test");
    }

    @Test
    void buildQuery_fromComplexExpression() {
        var expr = new NQLExpression.AndExpr(
                new NQLExpression.KeywordExpr("keyword1", 2.0),
                new NQLExpression.CompareExpr("sentiment", "==", "positive")
        );
        ObjectNode result = parser.buildQuery(expr);
        assertThat(result.toString()).contains("keyword1");
        assertThat(result.toString()).contains("positive");
    }

    @Test
    void parseToQuery_scoreLessThan() {
        ObjectNode result = parser.parseToQuery("score < 2.0");
        assertThat(result.toString()).contains("score");
    }

    @Test
    void parseToQuery_scoreGreaterThanOrEqual() {
        ObjectNode result = parser.parseToQuery("score >= 4.0");
        assertThat(result.toString()).contains("score");
    }

    @Test
    void parseToQuery_categoryEquals() {
        ObjectNode result = parser.parseToQuery("category == \"TECH\"");
        assertThat(result.toString()).contains("category");
    }

    @Test
    void parseToQuery_countryIn() {
        ObjectNode result = parser.parseToQuery("country IN [\"KR\", \"US\", \"JP\"]");
        assertThat(result.toString()).contains("country");
    }

    @Test
    void parseToQuery_notEquals() {
        ObjectNode result = parser.parseToQuery("source != \"Spam\"");
        assertThat(result.toString()).contains("source");
    }

    @Test
    void parseToExpression_inExpression() {
        NQLExpression expr = parser.parseToExpression("source IN [\"A\", \"B\"]");
        assertThat(expr).isInstanceOf(NQLExpression.InExpr.class);
        var in = (NQLExpression.InExpr) expr;
        assertThat(in.values()).hasSize(2);
    }

    @Test
    void parseToExpression_matchAll() {
        NQLExpression expr = parser.parseToExpression("*");
        assertThat(expr).isInstanceOf(NQLExpression.MatchAllExpr.class);
    }

    @Test
    void parseToQuery_multipleKeywordsWithBoosts() {
        ObjectNode result = parser.parseToQuery("keyword(\"HBM\") * 3.0 AND keyword(\"GPU\") * 2.0");
        assertThat(result.toString()).contains("HBM");
        assertThat(result.toString()).contains("GPU");
    }

    @Test
    void invalidNql_missingQuote() {
        assertThatThrownBy(() -> parser.parseToQuery("keyword(HBM)"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void invalidNql_invalidOperator() {
        assertThatThrownBy(() -> parser.parseToQuery("keyword(\"test\") @@@ keyword(\"other\")"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void invalidNql_unclosedBracket() {
        assertThatThrownBy(() -> parser.parseToQuery("source IN [\"A\", \"B\""))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void parseToQuery_withFloatScore() {
        ObjectNode result = parser.parseToQuery("score > 3.14159");
        assertThat(result.toString()).contains("score");
    }

    @Test
    void parseToQuery_sentiment_allValues() {
        ObjectNode pos = parser.parseToQuery("sentiment == \"positive\"");
        ObjectNode neu = parser.parseToQuery("sentiment == \"neutral\"");
        ObjectNode neg = parser.parseToQuery("sentiment == \"negative\"");

        assertThat(pos.toString()).contains("positive");
        assertThat(neu.toString()).contains("neutral");
        assertThat(neg.toString()).contains("negative");
    }
}
