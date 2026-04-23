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
        assertThat(result.path("rrf").path("retrievers").get(0).has("standard")).isTrue();
        assertThat(result.path("rrf").path("retrievers").get(1).has("knn")).isTrue();
        assertThat(result.path("rrf").path("retrievers").get(1).path("knn").has("k")).isTrue();
    }

    @Test
    void withoutKeyword_onlyBm25Retriever() throws Exception {
        ObjectNode boolQuery = (ObjectNode) mapper.readTree("{\"bool\":{\"must\":[]}}");
        var keywords = java.util.List.<NQLExpression.KeywordExpr>of();

        JsonNode result = scorer.buildRetriever(boolQuery, keywords, null);

        assertThat(result.path("standard").path("query")).isNotNull();
        assertThat(result.has("rrf")).isFalse();
    }

    @Test
    void withKeywordButNoVector_fallsBackToBm25() throws Exception {
        ObjectNode boolQuery = (ObjectNode) mapper.readTree("{\"bool\":{\"must\":[]}}");
        var keywords = java.util.List.of(new NQLExpression.KeywordExpr("HBM", null));

        JsonNode result = scorer.buildRetriever(boolQuery, keywords, null);

        assertThat(result.path("standard").path("query")).isNotNull();
        assertThat(result.has("rrf")).isFalse();
    }

    @Test
    void knnRetriever_hasCorrectStructure() throws Exception {
        ObjectNode boolQuery = (ObjectNode) mapper.readTree("{\"bool\":{\"must\":[]}}");
        var keywords = java.util.List.of(new NQLExpression.KeywordExpr("AI", null));
        float[] vector = new float[]{0.1f, 0.2f, 0.3f, 0.4f};

        JsonNode result = scorer.buildRetriever(boolQuery, keywords, vector);

        JsonNode knn = result.path("rrf").path("retrievers").get(1).path("knn");
        assertThat(knn.path("field").asText()).isEqualTo("content_vector");
        assertThat(knn.path("k").asInt()).isEqualTo(100);
        assertThat(knn.path("num_candidates").asInt()).isEqualTo(100);
        assertThat(knn.path("query_vector").isArray()).isTrue();
    }

    @Test
    void rankConstant_isSet() throws Exception {
        ObjectNode boolQuery = (ObjectNode) mapper.readTree("{\"bool\":{\"must\":[]}}");
        var keywords = java.util.List.of(new NQLExpression.KeywordExpr("GPU", null));
        float[] vector = new float[]{0.1f, 0.2f};

        JsonNode result = scorer.buildRetriever(boolQuery, keywords, vector);

        assertThat(result.path("rrf").path("rank_constant").asInt()).isEqualTo(60);
    }

    @Test
    void standardRetriever_hasBoolQuery() throws Exception {
        ObjectNode boolQuery = (ObjectNode) mapper.readTree("{\"bool\":{\"must\":[{\"match_all\":{}}]}}");
        var keywords = java.util.List.of(new NQLExpression.KeywordExpr("test", null));
        float[] vector = new float[]{0.5f};

        JsonNode result = scorer.buildRetriever(boolQuery, keywords, vector);

        JsonNode standard = result.path("rrf").path("retrievers").get(0).path("standard");
        assertThat(standard.path("query").path("bool")).isNotNull();
    }
}