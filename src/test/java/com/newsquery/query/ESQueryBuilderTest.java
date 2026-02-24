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
        assertThat(result.path("match").path("content").asText()).isEqualTo("HBM");
    }

    @Test
    void keywordWithBoost() throws Exception {
        var expr = new NQLExpression.KeywordExpr("HBM", 1.5);
        JsonNode result = mapper.readTree(builder.build(expr).toString());
        assertThat(result.path("match").path("content").path("query").asText()).isEqualTo("HBM");
        assertThat(result.path("match").path("content").path("boost").asDouble()).isEqualTo(1.5);
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
}
