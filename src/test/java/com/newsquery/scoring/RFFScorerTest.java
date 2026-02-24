package com.newsquery.scoring;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.newsquery.nql.NQLExpression;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class RRFScorerTest {

    private final RRFScorer scorer = new RRFScorer();
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void withKeyword_includesKnnRetriever() throws Exception {
        ObjectNode boolQuery = (ObjectNode) mapper.readTree("{\"bool\":{\"must\":[]}}");
        var keywords = java.util.List.of(new NQLExpression.KeywordExpr("HBM", null));
        float[] vector = new float[]{0.1f, 0.2f, 0.3f};

        JsonNode result = scorer.buildRetriever(boolQuery, keywords, vector);

        assertThat(result.path("rrf").path("retrievers").isArray()).isTrue();
        assertThat(result.path("rrf").path("retrievers").size()).isEqualTo(2);
        assertThat(result.path("rrf").path("rank_constant").asInt()).isEqualTo(60);
    }

    @Test
    void withoutKeyword_onlyBm25Retriever() throws Exception {
        ObjectNode boolQuery = (ObjectNode) mapper.readTree("{\"bool\":{\"must\":[]}}");
        var keywords = java.util.List.<NQLExpression.KeywordExpr>of();

        JsonNode result = scorer.buildRetriever(boolQuery, keywords, null);

        assertThat(result.path("standard").path("query")).isNotNull();
        assertThat(result.has("rrf")).isFalse();
    }
}