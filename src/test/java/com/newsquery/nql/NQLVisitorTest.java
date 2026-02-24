package com.newsquery.nql;

import org.antlr.v4.runtime.*;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

class NQLVisitorTest {

    private NQLExpression parse(String nql) {
        CharStream input = CharStreams.fromString(nql);
        NQLLexer lexer = new NQLLexer(input);
        CommonTokenStream tokens = new CommonTokenStream(lexer);
        NQLParser parser = new NQLParser(tokens);
        NQLVisitorImpl visitor = new NQLVisitorImpl();
        return visitor.visit(parser.query());
    }

    @Test
    void keywordOnly() {
        var result = parse("keyword(\"HBM\")");
        assertThat(result).isEqualTo(new NQLExpression.KeywordExpr("HBM", null));
    }

    @Test
    void keywordWithBoost() {
        var result = parse("keyword(\"HBM\") * 1.5");
        assertThat(result).isEqualTo(new NQLExpression.KeywordExpr("HBM", 1.5));
    }

    @Test
    void sentimentEquals() {
        var result = parse("sentiment == \"positive\"");
        assertThat(result).isEqualTo(new NQLExpression.CompareExpr("sentiment", "==", "positive"));
    }

    @Test
    void sourceIn() {
        var result = parse("source IN [\"Reuters\", \"Bloomberg\"]");
        assertThat(result).isEqualTo(new NQLExpression.InExpr("source", List.of("Reuters", "Bloomberg")));
    }

    @Test
    void andExpression() {
        var result = parse("keyword(\"HBM\") AND sentiment == \"positive\"");
        var expected = new NQLExpression.AndExpr(
            new NQLExpression.KeywordExpr("HBM", null),
            new NQLExpression.CompareExpr("sentiment", "==", "positive")
        );
        assertThat(result).isEqualTo(expected);
    }

    @Test
    void notExpression() {
        var result = parse("!sentiment == \"negative\"");
        var expected = new NQLExpression.NotExpr(
            new NQLExpression.CompareExpr("sentiment", "==", "negative")
        );
        assertThat(result).isEqualTo(expected);
    }

    @Test
    void complexQuery() {
        var result = parse("keyword(\"HBM\") * 2.0 AND sentiment != \"negative\"");
        assertThat(result).isInstanceOf(NQLExpression.AndExpr.class);
        var and = (NQLExpression.AndExpr) result;
        assertThat(and.left()).isEqualTo(new NQLExpression.KeywordExpr("HBM", 2.0));
        assertThat(and.right()).isEqualTo(new NQLExpression.CompareExpr("sentiment", "!=", "negative"));
    }
}
