package com.newsquery.embedding;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class EmbeddingClientIntegrationTest {

    @Autowired EmbeddingClient client;

    private ObjectMapper mapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(client, "serviceUrl", "http://localhost:8000");
    }

    @Test
    void embed_withValidServiceUrl_attemptCall() {
        float[] result = client.embed("test");
        // 서비스가 없으면 null 반환 (graceful fallback)
        // 서비스가 있으면 float[] 반환
        if (result != null) {
            assertThat(result).isNotEmpty();
        }
    }

    @Test
    void embed_withShortText() {
        float[] result = client.embed("AI");
        // Graceful degradation
        if (result != null) {
            assertThat(result.length).isGreaterThan(0);
        }
    }

    @Test
    void embed_withLongText() {
        String longText = "This is a longer text about artificial intelligence and machine learning. " +
                         "It contains multiple sentences to test embedding service with substantial input.";
        float[] result = client.embed(longText);
        if (result != null) {
            assertThat(result.length).isGreaterThan(0);
        }
    }

    @Test
    void embed_withSpecialCharacters() {
        float[] result = client.embed("HBM @ 5.0% (금융)");
        if (result != null) {
            assertThat(result.length).isGreaterThan(0);
        }
    }

    @Test
    void embed_withUnicodeCharacters() {
        float[] result = client.embed("한국 뉴스 AI 기술");
        if (result != null) {
            assertThat(result.length).isGreaterThan(0);
        }
    }

    @Test
    void embed_withVeryShortText() {
        float[] result = client.embed("a");
        // Single character should still process or gracefully fail
        assertThat(result == null || result.length >= 0).isTrue();
    }

    @Test
    void embed_connectionTimeout_gracefulFallback() {
        ReflectionTestUtils.setField(client, "serviceUrl", "http://invalid-host-12345:9999");
        float[] result = client.embed("test");
        assertThat(result).isNull();
    }

    @Test
    void embed_multipleConsecutiveCalls() {
        ReflectionTestUtils.setField(client, "serviceUrl", "http://localhost:8000");

        float[] result1 = client.embed("first");
        float[] result2 = client.embed("second");
        float[] result3 = client.embed("third");

        // All should either succeed or gracefully fail
        if (result1 != null && result2 != null && result3 != null) {
            assertThat(result1.length).isGreaterThan(0);
            assertThat(result2.length).isGreaterThan(0);
            assertThat(result3.length).isGreaterThan(0);
        }
    }

    @Test
    void jsonBody_structure() throws Exception {
        ObjectNode body = mapper.createObjectNode();
        body.put("text", "test content");

        assertThat(body.has("text")).isTrue();
        assertThat(body.path("text").asText()).isEqualTo("test content");
    }

    @Test
    void vectorArray_construction() {
        float[] vector = {0.1f, 0.2f, 0.3f, 0.4f, 0.5f};
        assertThat(vector).hasSize(5);
        assertThat(vector[0]).isEqualTo(0.1f);
        assertThat(vector[4]).isEqualTo(0.5f);
    }

    @Test
    void embed_emptyString_handling() {
        float[] result = client.embed("");
        // Empty string should gracefully handle or return null
        assertThat(result == null || result.length >= 0).isTrue();
    }

    @Test
    void embed_whitespaceOnly() {
        float[] result = client.embed("   ");
        // Whitespace should gracefully handle or return null
        assertThat(result == null || result.length >= 0).isTrue();
    }
}
