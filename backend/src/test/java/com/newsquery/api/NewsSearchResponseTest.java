package com.newsquery.api;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class NewsSearchResponseTest {

    @Test
    void response_emptyHits() {
        var response = new NewsSearchResponse(0L, List.of());
        assertThat(response.total()).isEqualTo(0L);
        assertThat(response.hits()).isEmpty();
    }

    @Test
    void response_singleHit() {
        var hit = new NewsHit("1", "Title", "Source", "positive", "KR", "2024-01-01", 0.9, "url");
        var response = new NewsSearchResponse(1L, List.of(hit));
        assertThat(response.total()).isEqualTo(1L);
        assertThat(response.hits()).hasSize(1);
        assertThat(response.hits().get(0)).isEqualTo(hit);
    }

    @Test
    void response_multipleHits() {
        var hits = List.of(
                new NewsHit("1", "T1", "S1", "positive", "KR", "2024-01-01", 0.9, "url1"),
                new NewsHit("2", "T2", "S2", "neutral", "US", "2024-01-02", 0.8, "url2"),
                new NewsHit("3", "T3", "S3", "negative", "JP", "2024-01-03", 0.7, "url3")
        );
        var response = new NewsSearchResponse(100L, hits);
        assertThat(response.total()).isEqualTo(100L);
        assertThat(response.hits()).hasSize(3);
    }

    @Test
    void response_largeTotal() {
        var response = new NewsSearchResponse(1000000L, List.of());
        assertThat(response.total()).isEqualTo(1000000L);
    }

    @Test
    void response_equality() {
        var hit = new NewsHit("1", "Title", "Source", "positive", "KR", "2024-01-01", 0.9, "url");
        var response1 = new NewsSearchResponse(1L, List.of(hit));
        var response2 = new NewsSearchResponse(1L, List.of(hit));
        assertThat(response1).isEqualTo(response2);
    }

    @Test
    void response_hitsOrder() {
        var hits = List.of(
                new NewsHit("1", "First", "S1", "positive", "KR", "2024-01-01", 0.95, "url1"),
                new NewsHit("2", "Second", "S2", "neutral", "US", "2024-01-02", 0.85, "url2"),
                new NewsHit("3", "Third", "S3", "negative", "JP", "2024-01-03", 0.75, "url3")
        );
        var response = new NewsSearchResponse(3L, hits);
        assertThat(response.hits().get(0).title()).isEqualTo("First");
        assertThat(response.hits().get(1).title()).isEqualTo("Second");
        assertThat(response.hits().get(2).title()).isEqualTo("Third");
    }

    @Test
    void response_hitsScoreOrdering() {
        var hits = List.of(
                new NewsHit("1", "T1", "S1", "positive", "KR", "2024-01-01", 0.95, "url1"),
                new NewsHit("2", "T2", "S2", "positive", "KR", "2024-01-01", 0.85, "url2"),
                new NewsHit("3", "T3", "S3", "positive", "KR", "2024-01-01", 0.75, "url3")
        );
        var response = new NewsSearchResponse(3L, hits);
        assertThat(response.hits().get(0).score()).isGreaterThan(response.hits().get(1).score());
        assertThat(response.hits().get(1).score()).isGreaterThan(response.hits().get(2).score());
    }

    @Test
    void response_totalGreaterThanHitsSize() {
        var hit = new NewsHit("1", "Title", "Source", "positive", "KR", "2024-01-01", 0.9, "url");
        var response = new NewsSearchResponse(100L, List.of(hit));
        assertThat(response.total()).isGreaterThan(response.hits().size());
    }

    @Test
    void response_immutableHitsList() {
        var hits = List.of(
                new NewsHit("1", "T1", "S1", "positive", "KR", "2024-01-01", 0.9, "url1")
        );
        var response = new NewsSearchResponse(1L, hits);
        assertThat(response.hits()).hasSize(1);
    }
}
