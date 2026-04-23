package com.newsquery.config;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import static org.assertj.core.api.Assertions.assertThat;

class WebConfigTest {

    @Test
    void webConfig_instantiation() {
        var config = new WebConfig();
        assertThat(config).isNotNull();
        assertThat(config).isInstanceOf(WebConfig.class);
        assertThat(config).isInstanceOf(WebMvcConfigurer.class);
    }

    @Test
    void webConfig_implementsWebMvcConfigurer() {
        var config = new WebConfig();
        assertThat(config).isInstanceOf(WebMvcConfigurer.class);
    }
}
