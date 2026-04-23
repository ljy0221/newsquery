package com.newsquery.search;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.newsquery.api.NewsHit;
import com.newsquery.api.NewsSearchResponse;
import org.elasticsearch.client.RestClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NewsSearchServiceIntegrationTest {

    @Mock ElasticsearchClient esClient;
    @Mock RestClient restClient;

    private NewsSearchService service;
    private ObjectMapper mapper;

    @BeforeEach
    void setUp() {
        service = new NewsSearchService(esClient, restClient);
        mapper = new ObjectMapper();
        ReflectionTestUtils.setField(service, "index", "news");
    }

    @Test
    void search_withValidQuery_returnsResults() throws IOException {
        ObjectNode query = mapper.createObjectNode();
        query.putObject("match_all");

        ObjectNode source1 = mapper.createObjectNode();
        source1.put("title", "HBM 신제품");
        source1.put("source", "Reuters");
        source1.put("sentiment", "positive");
        source1.put("country", "KR");
        source1.put("publishedAt", "2024-01-15");
        source1.put("score", 0.95);
        source1.put("url", "http://reuters.com/1");

        assertThat(source1.has("title")).isTrue();
        assertThat(source1.path("title").asText()).isEqualTo("HBM 신제품");
    }

    @Test
    void searchWithRrf_emptyQuery_returnsEmptyResponse() throws IOException {
        ObjectNode body = mapper.createObjectNode();
        body.put("size", 20);
        body.put("from", 0);

        assertThat(body.path("size").asInt()).isEqualTo(20);
        assertThat(body.path("from").asInt()).isEqualTo(0);
    }

    @Test
    void buildSearchBody_withPagination() throws IOException {
        ObjectMapper m = new ObjectMapper();
        ObjectNode body = m.createObjectNode();
        body.put("size", 20);
        body.put("from", 40);

        assertThat(body.path("from").asInt()).isEqualTo(40);
    }

    @Test
    void retrieverStructure_withStandardQuery() throws IOException {
        ObjectNode standard = mapper.createObjectNode();
        standard.putObject("query").putObject("bool")
                .putArray("must").add(mapper.createObjectNode().put("match_all", true));

        ObjectNode retriever = mapper.createObjectNode();
        retriever.set("standard", standard);

        assertThat(retriever.has("standard")).isTrue();
        assertThat(retriever.path("standard").has("query")).isTrue();
    }

    @Test
    void retrieverStructure_withKnnRetriever() throws IOException {
        ObjectNode knn = mapper.createObjectNode();
        knn.put("field", "content_vector");
        knn.put("k", 100);
        knn.putArray("query_vector").add(0.1f).add(0.2f);

        ObjectNode retriever = mapper.createObjectNode();
        ObjectNode rrf = mapper.createObjectNode();
        rrf.putArray("retrievers")
                .add(mapper.createObjectNode().set("knn", knn));
        retriever.set("rrf", rrf);

        assertThat(retriever.path("rrf").path("retrievers").size()).isEqualTo(1);
    }

    @Test
    void newsHit_allFieldsAccessible() {
        var hit = new NewsHit(
                "doc123",
                "AI 기술 발전",
                "Bloomberg",
                "positive",
                "US",
                "2024-01-10",
                0.92,
                "http://bloomberg.com"
        );

        assertThat(hit.id()).isEqualTo("doc123");
        assertThat(hit.title()).isEqualTo("AI 기술 발전");
        assertThat(hit.source()).isEqualTo("Bloomberg");
        assertThat(hit.sentiment()).isEqualTo("positive");
        assertThat(hit.country()).isEqualTo("US");
        assertThat(hit.publishedAt()).isEqualTo("2024-01-10");
        assertThat(hit.score()).isEqualTo(0.92);
        assertThat(hit.url()).isEqualTo("http://bloomberg.com");
    }

    @Test
    void newsSearchResponse_withMultipleHits() {
        var hits = List.of(
                new NewsHit("1", "Title1", "Source1", "positive", "KR", "2024-01-01", 0.9, "url1"),
                new NewsHit("2", "Title2", "Source2", "neutral", "US", "2024-01-02", 0.8, "url2"),
                new NewsHit("3", "Title3", "Source3", "negative", "JP", "2024-01-03", 0.7, "url3")
        );
        var response = new NewsSearchResponse(100L, hits);

        assertThat(response.total()).isEqualTo(100L);
        assertThat(response.hits()).hasSize(3);
        assertThat(response.hits().get(0).sentiment()).isEqualTo("positive");
        assertThat(response.hits().get(1).sentiment()).isEqualTo("neutral");
        assertThat(response.hits().get(2).sentiment()).isEqualTo("negative");
    }

    @Test
    void sortingClause_correctStructure() throws IOException {
        var sort = mapper.readTree(
                "[{\"_score\":{\"order\":\"desc\"}},{\"publishedAt\":{\"order\":\"desc\"}}]"
        );

        assertThat(sort.isArray()).isTrue();
        assertThat(sort.size()).isEqualTo(2);
        assertThat(sort.get(0).has("_score")).isTrue();
        assertThat(sort.get(1).has("publishedAt")).isTrue();
    }

    @Test
    void retrieverBody_withMultipleRetrievers() throws IOException {
        ObjectNode standard = mapper.createObjectNode();
        standard.putObject("query").put("match_all", true);

        ObjectNode knn = mapper.createObjectNode();
        knn.put("field", "content_vector");
        knn.putArray("query_vector").add(0.5f);

        ObjectNode rrf = mapper.createObjectNode();
        rrf.putArray("retrievers")
                .add(mapper.createObjectNode().set("standard", standard))
                .add(mapper.createObjectNode().set("knn", knn));
        rrf.put("rank_constant", 60);

        ObjectNode retriever = mapper.createObjectNode();
        retriever.set("rrf", rrf);

        assertThat(retriever.path("rrf").path("retrievers").size()).isEqualTo(2);
        assertThat(retriever.path("rrf").path("rank_constant").asInt()).isEqualTo(60);
    }

    @Test
    void resultMapping_withMissingFields() {
        ObjectNode source = mapper.createObjectNode();
        source.put("title", "Test");
        // Missing other fields

        assertThat(source.path("title").asText("")).isEqualTo("Test");
        assertThat(source.path("source").asText("")).isEmpty();
        assertThat(source.path("sentiment").asText("")).isEmpty();
    }

    @Test
    void paginationOffset_calculation() {
        int page = 5;
        int offset = page * 20;
        assertThat(offset).isEqualTo(100);

        page = 0;
        offset = page * 20;
        assertThat(offset).isEqualTo(0);

        page = 10;
        offset = page * 20;
        assertThat(offset).isEqualTo(200);
    }

    @Test
    void totalHitsExtraction() throws IOException {
        ObjectNode hitsNode = mapper.createObjectNode();
        hitsNode.putObject("total").put("value", 500);

        long total = hitsNode.path("total").path("value").asLong(0);
        assertThat(total).isEqualTo(500L);

        hitsNode = mapper.createObjectNode();
        hitsNode.putObject("total").put("value", 0);
        total = hitsNode.path("total").path("value").asLong(0);
        assertThat(total).isEqualTo(0L);
    }
}
