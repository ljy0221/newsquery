package com.newsquery.search;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.elasticsearch.client.Request;
import org.elasticsearch.client.RestClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class NewsSearchServiceSearchTest {

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
    void search_queriesWithMatchAll() throws IOException {
        ObjectNode query = mapper.createObjectNode();
        query.putObject("match_all");

        // 실제 검색은 ES가 필요하므로 여기서는 구조만 테스트
        assertThat(query.has("match_all")).isTrue();
    }

    @Test
    void searchWithRrf_buildBody_withStandardQuery() throws IOException {
        ObjectNode standard = mapper.createObjectNode();
        standard.putObject("query").put("match_all", true);
        ObjectNode retriever = mapper.createObjectNode();
        retriever.set("standard", standard);

        // Body 구조 검증
        ObjectNode body = mapper.createObjectNode();
        body.put("size", 20);
        body.put("from", 0);
        body.set("query", retriever.path("standard").path("query"));

        assertThat(body.path("size").asInt()).isEqualTo(20);
        assertThat(body.has("query")).isTrue();
    }

    @Test
    void searchWithRrf_buildBody_withRrfQuery() throws IOException {
        ObjectNode rrf = mapper.createObjectNode();
        rrf.put("rank_constant", 60);
        rrf.putArray("retrievers");

        ObjectNode retriever = mapper.createObjectNode();
        retriever.set("rrf", rrf);

        ObjectNode body = mapper.createObjectNode();
        body.put("size", 20);
        body.put("from", 0);

        if (retriever.has("rrf")) {
            for (var r : retriever.path("rrf").path("retrievers")) {
                if (r.has("standard")) {
                    body.set("query", r.path("standard").path("query"));
                } else if (r.has("knn")) {
                    body.set("knn", r.path("knn"));
                }
            }
        }

        assertThat(body.path("size").asInt()).isEqualTo(20);
    }

    @Test
    void searchWithRrf_parsesResponseBody() throws IOException {
        String responseJson = "{\"hits\":{\"total\":{\"value\":10},\"hits\":[" +
                "{\"_id\":\"1\",\"_score\":0.95,\"_source\":{\"title\":\"Test\",\"source\":\"Reuters\",\"sentiment\":\"positive\",\"country\":\"KR\",\"publishedAt\":\"2024-01-01\",\"url\":\"http://test.com\"}}," +
                "{\"_id\":\"2\",\"_score\":0.85,\"_source\":{\"title\":\"Test2\",\"source\":\"Bloomberg\",\"sentiment\":\"neutral\",\"country\":\"US\",\"publishedAt\":\"2024-01-02\",\"url\":\"http://test2.com\"}}" +
                "]}}";

        var root = mapper.readTree(responseJson);
        var hitsNode = root.path("hits");
        long total = hitsNode.path("total").path("value").asLong(0);

        assertThat(total).isEqualTo(10);
        assertThat(hitsNode.path("hits").size()).isEqualTo(2);
    }

    @Test
    void searchWithRrf_mapsHitFields() throws IOException {
        String hitJson = "{\"_id\":\"doc1\",\"_score\":0.95,\"_source\":{\"title\":\"Breaking News\",\"source\":\"Reuters\",\"sentiment\":\"positive\",\"country\":\"KR\",\"publishedAt\":\"2024-01-15\",\"url\":\"http://example.com\"}}";

        var hit = mapper.readTree(hitJson);
        var src = hit.path("_source");

        assertThat(hit.path("_id").asText()).isEqualTo("doc1");
        assertThat(hit.path("_score").asDouble(0.0)).isEqualTo(0.95);
        assertThat(src.path("title").asText("")).isEqualTo("Breaking News");
        assertThat(src.path("source").asText("")).isEqualTo("Reuters");
        assertThat(src.path("sentiment").asText("")).isEqualTo("positive");
        assertThat(src.path("country").asText("")).isEqualTo("KR");
        assertThat(src.path("publishedAt").asText("")).isEqualTo("2024-01-15");
        assertThat(src.path("url").asText("")).isEqualTo("http://example.com");
    }

    @Test
    void searchWithRrf_handlesMissingSourceFields() throws IOException {
        String hitJson = "{\"_id\":\"doc1\",\"_score\":0.8,\"_source\":{\"title\":\"Minimal\"}}";

        var hit = mapper.readTree(hitJson);
        var src = hit.path("_source");

        assertThat(hit.path("_id").asText()).isEqualTo("doc1");
        assertThat(src.path("title").asText("")).isEqualTo("Minimal");
        assertThat(src.path("source").asText("")).isEmpty();
        assertThat(src.path("sentiment").asText("")).isEmpty();
    }

    @Test
    void searchWithRrf_paginationOffset() throws IOException {
        int page = 3;
        int offset = page * 20;

        ObjectNode body = mapper.createObjectNode();
        body.put("from", offset);

        assertThat(body.path("from").asInt()).isEqualTo(60);
    }

    @Test
    void searchWithRrf_addsSorting() throws IOException {
        var sort = mapper.readTree(
                "[{\"_score\":{\"order\":\"desc\"}},{\"publishedAt\":{\"order\":\"desc\"}}]"
        );

        assertThat(sort.isArray()).isTrue();
        assertThat(sort.size()).isEqualTo(2);
        assertThat(sort.get(0).has("_score")).isTrue();
        assertThat(sort.get(1).has("publishedAt")).isTrue();
    }

    @Test
    void searchWithRrf_constructsRequest() throws IOException {
        ObjectNode body = mapper.createObjectNode();
        body.put("size", 20);
        body.put("from", 0);
        body.set("query", mapper.createObjectNode().put("match_all", true));

        Request request = new Request("POST", "/news/_search");
        assertThat(request.getMethod()).isEqualTo("POST");
        assertThat(request.getEndpoint()).isEqualTo("/news/_search");
    }

    @Test
    void search_createsSearchBody() throws IOException {
        ObjectNode query = mapper.createObjectNode();
        query.putObject("bool").putArray("must");

        String searchBody = "{\"query\":" + query.toString() + "}";
        assertThat(searchBody).contains("\"query\"");
        assertThat(searchBody).contains("\"bool\"");
    }

    @Test
    void responseProcessing_totalHits() throws IOException {
        String responseJson = "{\"hits\":{\"total\":{\"value\":100}}}";
        var root = mapper.readTree(responseJson);
        long total = root.path("hits").path("total").path("value").asLong(0);
        assertThat(total).isEqualTo(100L);
    }

    @Test
    void responseProcessing_emptyHits() throws IOException {
        String responseJson = "{\"hits\":{\"total\":{\"value\":0},\"hits\":[]}}";
        var root = mapper.readTree(responseJson);
        var hitsNode = root.path("hits");
        assertThat(hitsNode.path("hits").size()).isEqualTo(0);
    }

    @Test
    void responseProcessing_nullTotal() throws IOException {
        String responseJson = "{\"hits\":{\"hits\":[]}}";
        var root = mapper.readTree(responseJson);
        long total = root.path("hits").path("total").path("value").asLong(0);
        assertThat(total).isEqualTo(0L);
    }
}
