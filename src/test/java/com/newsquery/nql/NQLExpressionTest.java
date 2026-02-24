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
}
