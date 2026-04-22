package com.newsquery;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class NewsQueryApplicationTest {

    @Test
    void contextLoads() {
        // Spring Boot context should load successfully
        assertThat(true).isTrue();
    }

    @Test
    void applicationCanBeInstantiated() {
        assertThat(NewsQueryApplication.class).isNotNull();
    }
}
