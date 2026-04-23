package com.newsquery.query;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.newsquery.nql.NQLExpression;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

class ESQueryBuilderTest {

    private final ESQueryBuilder builder = new ESQueryBuilder();
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void keywordWithoutBoost() throws Exception {
        var expr = new NQLExpression.KeywordExpr("HBM", null);
        JsonNode result = mapper.readTree(builder.build(expr).toString());
        assertThat(result.path("multi_match").path("query").asText()).isEqualTo("HBM");
    }

    @Test
    void keywordWithBoost() throws Exception {
        var expr = new NQLExpression.KeywordExpr("HBM", 1.5);
        JsonNode result = mapper.readTree(builder.build(expr).toString());
        assertThat(result.path("multi_match").path("query").asText()).isEqualTo("HBM");
        assertThat(result.path("multi_match").path("boost").asDouble()).isEqualTo(1.5);
    }

    @Test
    void sentimentEquals() throws Exception {
        var expr = new NQLExpression.CompareExpr("sentiment", "==", "positive");
        JsonNode result = mapper.readTree(builder.build(expr).toString());
        assertThat(result.path("term").path("sentiment").asText()).isEqualTo("positive");
    }

    @Test
    void sentimentNotEquals() throws Exception {
        var expr = new NQLExpression.CompareExpr("sentiment", "!=", "negative");
        JsonNode result = mapper.readTree(builder.build(expr).toString());
        assertThat(result.path("bool").path("must_not").get(0)
            .path("term").path("sentiment").asText()).isEqualTo("negative");
    }

    @Test
    void publishedAtRange() throws Exception {
        var expr = new NQLExpression.CompareExpr("publishedAt", ">=", "2024-01-01");
        JsonNode result = mapper.readTree(builder.build(expr).toString());
        assertThat(result.path("range").path("publishedAt").path("gte").asText())
            .isEqualTo("2024-01-01");
    }

    @Test
    void sourceIn() throws Exception {
        var expr = new NQLExpression.InExpr("source", List.of("Reuters", "Bloomberg"));
        JsonNode result = mapper.readTree(builder.build(expr).toString());
        JsonNode values = result.path("terms").path("source");
        assertThat(values.get(0).asText()).isEqualTo("Reuters");
        assertThat(values.get(1).asText()).isEqualTo("Bloomberg");
    }

    @Test
    void andExpression() throws Exception {
        var expr = new NQLExpression.AndExpr(
            new NQLExpression.KeywordExpr("HBM", 2.0),
            new NQLExpression.CompareExpr("sentiment", "!=", "negative")
        );
        JsonNode result = mapper.readTree(builder.build(expr).toString());
        JsonNode must = result.path("bool").path("must");
        assertThat(must.isArray()).isTrue();
        assertThat(must.size()).isEqualTo(2);
    }

    @Test
    void orExpression() throws Exception {
        var expr = new NQLExpression.OrExpr(
            new NQLExpression.KeywordExpr("HBM", null),
            new NQLExpression.KeywordExpr("GPU", null)
        );
        JsonNode result = mapper.readTree(builder.build(expr).toString());
        JsonNode should = result.path("bool").path("should");
        assertThat(should.isArray()).isTrue();
        assertThat(result.path("bool").path("minimum_should_match").asInt()).isEqualTo(1);
    }

    @Test
    void notExpression() throws Exception {
        var expr = new NQLExpression.NotExpr(
            new NQLExpression.CompareExpr("sentiment", "==", "negative")
        );
        JsonNode result = mapper.readTree(builder.build(expr).toString());
        JsonNode mustNot = result.path("bool").path("must_not");
        assertThat(mustNot.isArray()).isTrue();
    }

    @Test
    void matchAllExpression() throws Exception {
        var expr = new NQLExpression.MatchAllExpr();
        JsonNode result = mapper.readTree(builder.build(expr).toString());
        assertThat(result.has("match_all")).isTrue();
    }

    @Test
    void scoreRangeGt() throws Exception {
        var expr = new NQLExpression.CompareExpr("score", ">", "5.0");
        JsonNode result = mapper.readTree(builder.build(expr).toString());
        assertThat(result.path("range").path("score").path("gt").asDouble()).isEqualTo(5.0);
    }

    @Test
    void dateRangeLte() throws Exception {
        var expr = new NQLExpression.CompareExpr("publishedAt", "<=", "2024-12-31");
        JsonNode result = mapper.readTree(builder.build(expr).toString());
        assertThat(result.path("range").path("publishedAt").path("lte").asText()).isEqualTo("2024-12-31");
    }

    @Test
    void countryInList() throws Exception {
        var expr = new NQLExpression.InExpr("country", List.of("KR", "US", "JP"));
        JsonNode result = mapper.readTree(builder.build(expr).toString());
        JsonNode values = result.path("terms").path("country");
        assertThat(values.size()).isEqualTo(3);
    }

    @Test
    void complexNestedExpression() throws Exception {
        var expr = new NQLExpression.AndExpr(
            new NQLExpression.KeywordExpr("HBM", 2.0),
            new NQLExpression.OrExpr(
                new NQLExpression.CompareExpr("sentiment", "==", "positive"),
                new NQLExpression.CompareExpr("sentiment", "==", "neutral")
            )
        );
        JsonNode result = mapper.readTree(builder.build(expr).toString());
        assertThat(result.path("bool").path("must").isArray()).isTrue();
    }
}
