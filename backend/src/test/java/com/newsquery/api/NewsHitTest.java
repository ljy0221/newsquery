package com.newsquery.api;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class NewsHitTest {

    @Test
    void newsHit_creation_allFields() {
        var hit = new NewsHit(
                "id123",
                "Breaking News",
                "Reuters",
                "positive",
                "KR",
                "2024-01-15",
                0.95,
                "http://example.com"
        );

        assertThat(hit.id()).isEqualTo("id123");
        assertThat(hit.title()).isEqualTo("Breaking News");
        assertThat(hit.source()).isEqualTo("Reuters");
        assertThat(hit.sentiment()).isEqualTo("positive");
        assertThat(hit.country()).isEqualTo("KR");
        assertThat(hit.publishedAt()).isEqualTo("2024-01-15");
        assertThat(hit.score()).isEqualTo(0.95);
        assertThat(hit.url()).isEqualTo("http://example.com");
    }

    @Test
    void newsHit_equality() {
        var hit1 = new NewsHit("1", "Title", "Source", "positive", "KR", "2024-01-01", 0.9, "url");
        var hit2 = new NewsHit("1", "Title", "Source", "positive", "KR", "2024-01-01", 0.9, "url");

        assertThat(hit1).isEqualTo(hit2);
    }

    @Test
    void newsHit_emptyFields() {
        var hit = new NewsHit("1", "", "", "", "", "", 0.0, "");
        assertThat(hit.title()).isEmpty();
        assertThat(hit.source()).isEmpty();
    }

    @Test
    void newsHit_maxScore() {
        var hit = new NewsHit("1", "Title", "Source", "positive", "KR", "2024-01-01", 1.0, "url");
        assertThat(hit.score()).isEqualTo(1.0);
    }

    @Test
    void newsHit_minScore() {
        var hit = new NewsHit("1", "Title", "Source", "positive", "KR", "2024-01-01", 0.0, "url");
        assertThat(hit.score()).isEqualTo(0.0);
    }

    @Test
    void newsHit_differentCountries() {
        var krHit = new NewsHit("1", "T", "S", "neutral", "KR", "2024-01-01", 0.5, "url");
        var usHit = new NewsHit("2", "T", "S", "neutral", "US", "2024-01-01", 0.5, "url");
        var jpHit = new NewsHit("3", "T", "S", "neutral", "JP", "2024-01-01", 0.5, "url");

        assertThat(krHit.country()).isEqualTo("KR");
        assertThat(usHit.country()).isEqualTo("US");
        assertThat(jpHit.country()).isEqualTo("JP");
    }

    @Test
    void newsHit_sentiments() {
        var pos = new NewsHit("1", "T", "S", "positive", "KR", "2024-01-01", 0.5, "url");
        var neu = new NewsHit("2", "T", "S", "neutral", "KR", "2024-01-01", 0.5, "url");
        var neg = new NewsHit("3", "T", "S", "negative", "KR", "2024-01-01", 0.5, "url");

        assertThat(pos.sentiment()).isEqualTo("positive");
        assertThat(neu.sentiment()).isEqualTo("neutral");
        assertThat(neg.sentiment()).isEqualTo("negative");
    }
}
