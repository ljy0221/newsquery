package com.newsquery.nql;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

class NQLExpressionTest {

    @Test
    void keywordExpr_withBoost() {
        var expr = new NQLExpression.KeywordExpr("HBM", 1.5);
        assertThat(expr.text()).isEqualTo("HBM");
        assertThat(expr.boost()).isEqualTo(1.5);
    }

    @Test
    void keywordExpr_withoutBoost() {
        var expr = new NQLExpression.KeywordExpr("HBM", null);
        assertThat(expr.boost()).isNull();
    }

    @Test
    void compareExpr() {
        var expr = new NQLExpression.CompareExpr("sentiment", "==", "positive");
        assertThat(expr.field()).isEqualTo("sentiment");
        assertThat(expr.op()).isEqualTo("==");
        assertThat(expr.value()).isEqualTo("positive");
    }

    @Test
    void inExpr() {
        var expr = new NQLExpression.InExpr("source", List.of("Reuters", "Bloomberg"));
        assertThat(expr.field()).isEqualTo("source");
        assertThat(expr.values()).containsExactly("Reuters", "Bloomberg");
    }

    @Test
    void andExpr() {
        var left = new NQLExpression.KeywordExpr("HBM", null);
        var right = new NQLExpression.CompareExpr("sentiment", "!=", "negative");
        var and = new NQLExpression.AndExpr(left, right);
        assertThat(and.left()).isEqualTo(left);
        assertThat(and.right()).isEqualTo(right);
    }

    @Test
    void orExpr() {
        var left = new NQLExpression.KeywordExpr("AI", null);
        var right = new NQLExpression.KeywordExpr("ML", null);
        var or = new NQLExpression.OrExpr(left, right);
        assertThat(or.left()).isEqualTo(left);
        assertThat(or.right()).isEqualTo(right);
    }

    @Test
    void notExpr() {
        var inner = new NQLExpression.KeywordExpr("SPAM", null);
        var not = new NQLExpression.NotExpr(inner);
        assertThat(not.expr()).isEqualTo(inner);
    }

    @Test
    void matchAllExpr() {
        var expr = new NQLExpression.MatchAllExpr();
        assertThat(expr).isInstanceOf(NQLExpression.MatchAllExpr.class);
    }

    @Test
    void keywordExpr_equality() {
        var expr1 = new NQLExpression.KeywordExpr("HBM", 2.0);
        var expr2 = new NQLExpression.KeywordExpr("HBM", 2.0);
        assertThat(expr1).isEqualTo(expr2);
    }

    @Test
    void compareExpr_rangeOperators() {
        var gteExpr = new NQLExpression.CompareExpr("publishedAt", ">=", "2024-01-01");
        assertThat(gteExpr.op()).isEqualTo(">=");

        var ltExpr = new NQLExpression.CompareExpr("score", "<", "3.0");
        assertThat(ltExpr.op()).isEqualTo("<");
    }

    @Test
    void inExpr_multipleValues() {
        var expr = new NQLExpression.InExpr("country", List.of("KR", "US", "JP", "CN"));
        assertThat(expr.values()).hasSize(4);
        assertThat(expr.values()).contains("KR", "US");
    }

    @Test
    void nestedExpressions() {
        var inner = new NQLExpression.AndExpr(
                new NQLExpression.KeywordExpr("HBM", null),
                new NQLExpression.CompareExpr("sentiment", "==", "positive")
        );
        var outer = new NQLExpression.NotExpr(inner);
        assertThat(outer.expr()).isEqualTo(inner);
    }
}
