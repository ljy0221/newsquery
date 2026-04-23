package com.newsquery.nql;

import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class NQLVisitorImplementationTest {

    private NQLExpression parse(String nql) {
        var input = CharStreams.fromString(nql);
        var lexer = new NQLLexer(input);
        var tokens = new CommonTokenStream(lexer);
        var parser = new NQLParser(tokens);
        var visitor = new NQLVisitorImpl();
        return visitor.visit(parser.query());
    }

    @Test
    void visitKeywordExpr_simple() {
        var result = parse("keyword(\"test\")");
        assertThat(result).isInstanceOf(NQLExpression.KeywordExpr.class);
        var kw = (NQLExpression.KeywordExpr) result;
        assertThat(kw.text()).isEqualTo("test");
        assertThat(kw.boost()).isNull();
    }

    @Test
    void visitKeywordExpr_withBoost() {
        var result = parse("keyword(\"test\") * 2.5");
        assertThat(result).isInstanceOf(NQLExpression.KeywordExpr.class);
        var kw = (NQLExpression.KeywordExpr) result;
        assertThat(kw.text()).isEqualTo("test");
        assertThat(kw.boost()).isEqualTo(2.5);
    }

    @Test
    void visitCompareExpr_equals() {
        var result = parse("sentiment == \"positive\"");
        assertThat(result).isInstanceOf(NQLExpression.CompareExpr.class);
        var cmp = (NQLExpression.CompareExpr) result;
        assertThat(cmp.field()).isEqualTo("sentiment");
        assertThat(cmp.op()).isEqualTo("==");
        assertThat(cmp.value()).isEqualTo("positive");
    }

    @Test
    void visitCompareExpr_notEquals() {
        var result = parse("sentiment != \"negative\"");
        assertThat(result).isInstanceOf(NQLExpression.CompareExpr.class);
        var cmp = (NQLExpression.CompareExpr) result;
        assertThat(cmp.op()).isEqualTo("!=");
    }

    @Test
    void visitCompareExpr_greaterThan() {
        var result = parse("score > 3.0");
        assertThat(result).isInstanceOf(NQLExpression.CompareExpr.class);
        var cmp = (NQLExpression.CompareExpr) result;
        assertThat(cmp.field()).isEqualTo("score");
        assertThat(cmp.op()).isEqualTo(">");
        assertThat(cmp.value()).isEqualTo("3.0");
    }

    @Test
    void visitCompareExpr_lessThanOrEqual() {
        var result = parse("publishedAt <= \"2024-12-31\"");
        assertThat(result).isInstanceOf(NQLExpression.CompareExpr.class);
        var cmp = (NQLExpression.CompareExpr) result;
        assertThat(cmp.op()).isEqualTo("<=");
    }

    @Test
    void visitInExpr() {
        var result = parse("source IN [\"Reuters\", \"Bloomberg\"]");
        assertThat(result).isInstanceOf(NQLExpression.InExpr.class);
        var in = (NQLExpression.InExpr) result;
        assertThat(in.field()).isEqualTo("source");
        assertThat(in.values()).containsExactly("Reuters", "Bloomberg");
    }

    @Test
    void visitInExpr_multipleValues() {
        var result = parse("country IN [\"KR\", \"US\", \"JP\", \"CN\"]");
        assertThat(result).isInstanceOf(NQLExpression.InExpr.class);
        var in = (NQLExpression.InExpr) result;
        assertThat(in.values()).hasSize(4);
    }

    @Test
    void visitAndExpr() {
        var result = parse("keyword(\"HBM\") AND sentiment == \"positive\"");
        assertThat(result).isInstanceOf(NQLExpression.AndExpr.class);
        var and = (NQLExpression.AndExpr) result;
        assertThat(and.left()).isInstanceOf(NQLExpression.KeywordExpr.class);
        assertThat(and.right()).isInstanceOf(NQLExpression.CompareExpr.class);
    }

    @Test
    void visitOrExpr() {
        var result = parse("sentiment == \"positive\" OR sentiment == \"neutral\"");
        assertThat(result).isInstanceOf(NQLExpression.OrExpr.class);
        var or = (NQLExpression.OrExpr) result;
        assertThat(or.left()).isInstanceOf(NQLExpression.CompareExpr.class);
        assertThat(or.right()).isInstanceOf(NQLExpression.CompareExpr.class);
    }

    @Test
    void visitNotExpr() {
        var result = parse("!sentiment == \"negative\"");
        assertThat(result).isInstanceOf(NQLExpression.NotExpr.class);
        var not = (NQLExpression.NotExpr) result;
        assertThat(not.expr()).isInstanceOf(NQLExpression.CompareExpr.class);
    }

    @Test
    void visitMatchAllExpr() {
        var result = parse("*");
        assertThat(result).isInstanceOf(NQLExpression.MatchAllExpr.class);
    }

    @Test
    void visitGroupExpr() {
        var result = parse("(keyword(\"HBM\") AND sentiment == \"positive\") OR keyword(\"GPU\")");
        assertThat(result).isInstanceOf(NQLExpression.OrExpr.class);
    }

    @Test
    void complexQuery_precedence() {
        var result = parse("keyword(\"A\") AND keyword(\"B\") OR keyword(\"C\") AND keyword(\"D\")");
        assertThat(result).isInstanceOf(NQLExpression.OrExpr.class);
    }

    @Test
    void visitAllSentimentValues() {
        var pos = parse("sentiment == \"positive\"");
        var neu = parse("sentiment == \"neutral\"");
        var neg = parse("sentiment == \"negative\"");

        assertThat(pos).isInstanceOf(NQLExpression.CompareExpr.class);
        assertThat(neu).isInstanceOf(NQLExpression.CompareExpr.class);
        assertThat(neg).isInstanceOf(NQLExpression.CompareExpr.class);
    }

    @Test
    void visitAllFieldTypes() {
        var sentiment = parse("sentiment == \"positive\"");
        var source = parse("source IN [\"Reuters\"]");
        var category = parse("category == \"TECH\"");
        var country = parse("country IN [\"KR\"]");
        var publishedAt = parse("publishedAt >= \"2024-01-01\"");
        var score = parse("score > 5.0");

        assertThat(sentiment).isInstanceOf(NQLExpression.CompareExpr.class);
        assertThat(source).isInstanceOf(NQLExpression.InExpr.class);
        assertThat(category).isInstanceOf(NQLExpression.CompareExpr.class);
        assertThat(country).isInstanceOf(NQLExpression.InExpr.class);
        assertThat(publishedAt).isInstanceOf(NQLExpression.CompareExpr.class);
        assertThat(score).isInstanceOf(NQLExpression.CompareExpr.class);
    }

    @Test
    void complexNestedExpression() {
        var result = parse("(keyword(\"HBM\") * 2.0 AND sentiment != \"negative\") OR (source IN [\"Reuters\", \"Bloomberg\"] AND score > 4.0)");
        assertThat(result).isInstanceOf(NQLExpression.OrExpr.class);
    }

    @Test
    void visitCompareExpr_allOperators() {
        assertThat(parse("score == 5.0")).isInstanceOf(NQLExpression.CompareExpr.class);
        assertThat(parse("score != 5.0")).isInstanceOf(NQLExpression.CompareExpr.class);
        assertThat(parse("score >= 5.0")).isInstanceOf(NQLExpression.CompareExpr.class);
        assertThat(parse("score <= 5.0")).isInstanceOf(NQLExpression.CompareExpr.class);
        assertThat(parse("score > 5.0")).isInstanceOf(NQLExpression.CompareExpr.class);
        assertThat(parse("score < 5.0")).isInstanceOf(NQLExpression.CompareExpr.class);
    }
}
