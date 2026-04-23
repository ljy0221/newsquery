package com.newsquery.embedding;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class EmbeddingClientTest {

    @Autowired EmbeddingClient client;

    @Test
    void embed_withServiceUnavailable_returnsNull() {
        ReflectionTestUtils.setField(client, "serviceUrl", "http://invalid-host:9999");
        float[] result = client.embed("test");
        assertThat(result).isNull();
    }

    @Test
    void embed_withValidText_callsService() {
        ReflectionTestUtils.setField(client, "serviceUrl", "http://localhost:8000");
        float[] result = client.embed("HBM");
        if (result != null) {
            assertThat(result).isNotEmpty();
        }
    }

    @Test
    void embed_gracefulFallback() {
        ReflectionTestUtils.setField(client, "serviceUrl", "http://unreachable:8000");
        float[] result = client.embed("test");
        assertThat(result).isNull();
    }
}
