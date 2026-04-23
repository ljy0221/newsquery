package com.newsquery.search;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.newsquery.api.NewsHit;
import com.newsquery.api.NewsSearchResponse;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class NewsSearchServiceTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void buildRetrieverBody_withStandard() throws Exception {
        ObjectNode standard = mapper.createObjectNode();
        standard.putObject("query").put("match_all", true);

        ObjectNode retriever = mapper.createObjectNode();
        retriever.set("standard", standard);

        assertThat(retriever.has("standard")).isTrue();
        assertThat(retriever.path("standard").has("query")).isTrue();
    }

    @Test
    void buildRetrieverBody_withRrf() throws Exception {
        ObjectNode knn = mapper.createObjectNode();
        knn.put("field", "content_vector");
        knn.putArray("query_vector").add(0.1f).add(0.2f);

        ObjectNode rrf = mapper.createObjectNode();
        rrf.putArray("retrievers").add(mapper.createObjectNode().set("knn", knn));

        ObjectNode retriever = mapper.createObjectNode();
        retriever.set("rrf", rrf);

        assertThat(retriever.has("rrf")).isTrue();
        assertThat(retriever.path("rrf").path("retrievers").size()).isEqualTo(1);
    }

    @Test
    void newsSearchResponse_constructAndAccess() {
        var response = new NewsSearchResponse(100L, List.of());
        assertThat(response.total()).isEqualTo(100L);
        assertThat(response.hits()).isEmpty();
    }

    @Test
    void newsSearchResponse_withHits() {
        var hit = new NewsHit(
                "doc1", "제목", "Reuters", "positive", "KR", "2024-01-01", 0.95, "http://example.com"
        );
        var response = new NewsSearchResponse(1L, List.of(hit));
        assertThat(response.total()).isEqualTo(1L);
        assertThat(response.hits()).hasSize(1);
        assertThat(response.hits().get(0).title()).isEqualTo("제목");
    }

    @Test
    void newsHit_allFields() {
        var hit = new NewsHit("1", "Title", "Source", "positive", "KR", "2024-01-01", 0.85, "http://url.com");
        assertThat(hit.id()).isEqualTo("1");
        assertThat(hit.title()).isEqualTo("Title");
        assertThat(hit.source()).isEqualTo("Source");
        assertThat(hit.sentiment()).isEqualTo("positive");
        assertThat(hit.country()).isEqualTo("KR");
        assertThat(hit.publishedAt()).isEqualTo("2024-01-01");
        assertThat(hit.score()).isEqualTo(0.85);
        assertThat(hit.url()).isEqualTo("http://url.com");
    }

    @Test
    void newsSearchResponse_multipleHits() {
        var hit1 = new NewsHit("1", "Title1", "Source1", "positive", "KR", "2024-01-01", 0.9, "url1");
        var hit2 = new NewsHit("2", "Title2", "Source2", "neutral", "US", "2024-01-02", 0.8, "url2");
        var response = new NewsSearchResponse(2L, List.of(hit1, hit2));
        assertThat(response.hits()).hasSize(2);
        assertThat(response.total()).isEqualTo(2L);
    }
}
