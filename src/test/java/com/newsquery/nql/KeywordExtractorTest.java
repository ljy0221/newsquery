package com.newsquery.nql;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class KeywordExtractorTest {

    @Autowired KeywordExtractor extractor;

    @Test
    void extract_singleKeyword() {
        var expr = new NQLExpression.KeywordExpr("HBM", null);
        List<NQLExpression.KeywordExpr> result = extractor.extract(expr);
        assertThat(result).hasSize(1);
        assertThat(result.get(0).text()).isEqualTo("HBM");
    }

    @Test
    void extract_keywordWithBoost() {
        var expr = new NQLExpression.KeywordExpr("GPU", 2.0);
        List<NQLExpression.KeywordExpr> result = extractor.extract(expr);
        assertThat(result).hasSize(1);
        assertThat(result.get(0).text()).isEqualTo("GPU");
        assertThat(result.get(0).boost()).isEqualTo(2.0);
    }

    @Test
    void extract_andExpression() {
        var expr = new NQLExpression.AndExpr(
                new NQLExpression.KeywordExpr("HBM", null),
                new NQLExpression.KeywordExpr("GPU", null)
        );
        List<NQLExpression.KeywordExpr> result = extractor.extract(expr);
        assertThat(result).hasSize(2);
        assertThat(result.get(0).text()).isEqualTo("HBM");
        assertThat(result.get(1).text()).isEqualTo("GPU");
    }

    @Test
    void extract_orExpression() {
        var expr = new NQLExpression.OrExpr(
                new NQLExpression.KeywordExpr("AI", null),
                new NQLExpression.KeywordExpr("ML", null)
        );
        List<NQLExpression.KeywordExpr> result = extractor.extract(expr);
        assertThat(result).hasSize(2);
    }

    @Test
    void extract_notExpression() {
        var expr = new NQLExpression.NotExpr(
                new NQLExpression.KeywordExpr("SPAM", null)
        );
        List<NQLExpression.KeywordExpr> result = extractor.extract(expr);
        assertThat(result).hasSize(1);
        assertThat(result.get(0).text()).isEqualTo("SPAM");
    }

    @Test
    void extract_compareExpression_noKeywords() {
        var expr = new NQLExpression.CompareExpr("sentiment", "==", "positive");
        List<NQLExpression.KeywordExpr> result = extractor.extract(expr);
        assertThat(result).isEmpty();
    }

    @Test
    void extract_inExpression_noKeywords() {
        var expr = new NQLExpression.InExpr("source", List.of("Reuters", "Bloomberg"));
        List<NQLExpression.KeywordExpr> result = extractor.extract(expr);
        assertThat(result).isEmpty();
    }

    @Test
    void extract_complexExpression() {
        var expr = new NQLExpression.AndExpr(
                new NQLExpression.KeywordExpr("HBM", 2.0),
                new NQLExpression.AndExpr(
                        new NQLExpression.KeywordExpr("AI", null),
                        new NQLExpression.CompareExpr("sentiment", "!=", "negative")
                )
        );
        List<NQLExpression.KeywordExpr> result = extractor.extract(expr);
        assertThat(result).hasSize(2);
        assertThat(result.get(0).text()).isEqualTo("HBM");
        assertThat(result.get(1).text()).isEqualTo("AI");
    }
}
