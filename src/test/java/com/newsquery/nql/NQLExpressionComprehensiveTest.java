package com.newsquery.nql;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class NQLExpressionComprehensiveTest {

    @Test
    void keywordExpr_fieldAccess() {
        var expr = new NQLExpression.KeywordExpr("test", 1.5);
        assertThat(expr.text()).isEqualTo("test");
        assertThat(expr.boost()).isEqualTo(1.5);
    }

    @Test
    void keywordExpr_nullBoost() {
        var expr = new NQLExpression.KeywordExpr("test", null);
        assertThat(expr.boost()).isNull();
    }

    @Test
    void keywordExpr_toString() {
        var expr = new NQLExpression.KeywordExpr("HBM", 2.0);
        assertThat(expr.toString()).contains("KeywordExpr");
    }

    @Test
    void compareExpr_fieldAccess() {
        var expr = new NQLExpression.CompareExpr("sentiment", "==", "positive");
        assertThat(expr.field()).isEqualTo("sentiment");
        assertThat(expr.op()).isEqualTo("==");
        assertThat(expr.value()).isEqualTo("positive");
    }

    @Test
    void compareExpr_allOperators() {
        assertThat(new NQLExpression.CompareExpr("field", "==", "value").op()).isEqualTo("==");
        assertThat(new NQLExpression.CompareExpr("field", "!=", "value").op()).isEqualTo("!=");
        assertThat(new NQLExpression.CompareExpr("field", ">=", "value").op()).isEqualTo(">=");
        assertThat(new NQLExpression.CompareExpr("field", "<=", "value").op()).isEqualTo("<=");
        assertThat(new NQLExpression.CompareExpr("field", ">", "value").op()).isEqualTo(">");
        assertThat(new NQLExpression.CompareExpr("field", "<", "value").op()).isEqualTo("<");
    }

    @Test
    void compareExpr_numericValue() {
        var expr = new NQLExpression.CompareExpr("score", ">", "5.0");
        assertThat(expr.value()).isEqualTo("5.0");
    }

    @Test
    void inExpr_fieldAccess() {
        var values = List.of("Reuters", "Bloomberg");
        var expr = new NQLExpression.InExpr("source", values);
        assertThat(expr.field()).isEqualTo("source");
        assertThat(expr.values()).isEqualTo(values);
    }

    @Test
    void inExpr_multipleValues() {
        var values = List.of("A", "B", "C", "D", "E");
        var expr = new NQLExpression.InExpr("field", values);
        assertThat(expr.values()).hasSize(5);
    }

    @Test
    void inExpr_singleValue() {
        var expr = new NQLExpression.InExpr("source", List.of("Reuters"));
        assertThat(expr.values()).hasSize(1);
    }

    @Test
    void andExpr_leftRight() {
        var left = new NQLExpression.KeywordExpr("HBM", null);
        var right = new NQLExpression.CompareExpr("sentiment", "==", "positive");
        var expr = new NQLExpression.AndExpr(left, right);
        assertThat(expr.left()).isEqualTo(left);
        assertThat(expr.right()).isEqualTo(right);
    }

    @Test
    void andExpr_nestedAnd() {
        var inner = new NQLExpression.AndExpr(
                new NQLExpression.KeywordExpr("A", null),
                new NQLExpression.KeywordExpr("B", null)
        );
        var outer = new NQLExpression.AndExpr(
                inner,
                new NQLExpression.KeywordExpr("C", null)
        );
        assertThat(outer.left()).isEqualTo(inner);
    }

    @Test
    void orExpr_leftRight() {
        var left = new NQLExpression.KeywordExpr("A", null);
        var right = new NQLExpression.KeywordExpr("B", null);
        var expr = new NQLExpression.OrExpr(left, right);
        assertThat(expr.left()).isEqualTo(left);
        assertThat(expr.right()).isEqualTo(right);
    }

    @Test
    void orExpr_withCompare() {
        var left = new NQLExpression.CompareExpr("sentiment", "==", "positive");
        var right = new NQLExpression.CompareExpr("sentiment", "==", "neutral");
        var expr = new NQLExpression.OrExpr(left, right);
        assertThat(expr.left()).isInstanceOf(NQLExpression.CompareExpr.class);
        assertThat(expr.right()).isInstanceOf(NQLExpression.CompareExpr.class);
    }

    @Test
    void notExpr_innerExpression() {
        var inner = new NQLExpression.KeywordExpr("SPAM", null);
        var expr = new NQLExpression.NotExpr(inner);
        assertThat(expr.expr()).isEqualTo(inner);
    }

    @Test
    void notExpr_notOfCompare() {
        var inner = new NQLExpression.CompareExpr("sentiment", "==", "negative");
        var expr = new NQLExpression.NotExpr(inner);
        assertThat(expr.expr()).isInstanceOf(NQLExpression.CompareExpr.class);
    }

    @Test
    void matchAllExpr_instantiation() {
        var expr = new NQLExpression.MatchAllExpr();
        assertThat(expr).isInstanceOf(NQLExpression.MatchAllExpr.class);
    }

    @Test
    void matchAllExpr_equality() {
        var expr1 = new NQLExpression.MatchAllExpr();
        var expr2 = new NQLExpression.MatchAllExpr();
        assertThat(expr1).isEqualTo(expr2);
    }

    @Test
    void complexExpression_tree() {
        var tree = new NQLExpression.AndExpr(
                new NQLExpression.KeywordExpr("HBM", 2.0),
                new NQLExpression.OrExpr(
                        new NQLExpression.CompareExpr("sentiment", "==", "positive"),
                        new NQLExpression.InExpr("source", List.of("Reuters"))
                )
        );
        assertThat(tree.left()).isInstanceOf(NQLExpression.KeywordExpr.class);
        assertThat(tree.right()).isInstanceOf(NQLExpression.OrExpr.class);
    }

    @Test
    void expressionEquality_keywordExpr() {
        var expr1 = new NQLExpression.KeywordExpr("test", 1.5);
        var expr2 = new NQLExpression.KeywordExpr("test", 1.5);
        assertThat(expr1).isEqualTo(expr2);
    }

    @Test
    void expressionEquality_compareExpr() {
        var expr1 = new NQLExpression.CompareExpr("sentiment", "==", "positive");
        var expr2 = new NQLExpression.CompareExpr("sentiment", "==", "positive");
        assertThat(expr1).isEqualTo(expr2);
    }

    @Test
    void expressionInequality() {
        var expr1 = new NQLExpression.KeywordExpr("A", null);
        var expr2 = new NQLExpression.KeywordExpr("B", null);
        assertThat(expr1).isNotEqualTo(expr2);
    }

    @Test
    void allFieldTypes() {
        var sentiment = new NQLExpression.CompareExpr("sentiment", "==", "positive");
        var source = new NQLExpression.InExpr("source", List.of("Reuters"));
        var category = new NQLExpression.CompareExpr("category", "==", "TECH");
        var country = new NQLExpression.InExpr("country", List.of("KR"));
        var publishedAt = new NQLExpression.CompareExpr("publishedAt", ">=", "2024-01-01");
        var score = new NQLExpression.CompareExpr("score", ">", "5.0");

        assertThat(sentiment.field()).isEqualTo("sentiment");
        assertThat(source.field()).isEqualTo("source");
        assertThat(category.field()).isEqualTo("category");
        assertThat(country.field()).isEqualTo("country");
        assertThat(publishedAt.field()).isEqualTo("publishedAt");
        assertThat(score.field()).isEqualTo("score");
    }
}
