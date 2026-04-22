package com.newsquery.api;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class QueryRequestTest {

    @Test
    void queryRequest_creation() {
        var request = new QueryRequest("keyword(\"test\")", 0);
        assertThat(request.nql()).isEqualTo("keyword(\"test\")");
        assertThat(request.page()).isEqualTo(0);
    }

    @Test
    void queryRequest_firstPage() {
        var request = new QueryRequest("*", 0);
        assertThat(request.page()).isEqualTo(0);
    }

    @Test
    void queryRequest_secondPage() {
        var request = new QueryRequest("sentiment == \"positive\"", 1);
        assertThat(request.page()).isEqualTo(1);
    }

    @Test
    void queryRequest_largePage() {
        var request = new QueryRequest("keyword(\"test\")", 100);
        assertThat(request.page()).isEqualTo(100);
    }

    @Test
    void queryRequest_complexNql() {
        var nql = "keyword(\"HBM\") * 2.0 AND sentiment != \"negative\" OR source IN [\"Reuters\", \"Bloomberg\"]";
        var request = new QueryRequest(nql, 5);
        assertThat(request.nql()).isEqualTo(nql);
        assertThat(request.page()).isEqualTo(5);
    }

    @Test
    void queryRequest_emptyNql() {
        var request = new QueryRequest("", 0);
        assertThat(request.nql()).isEmpty();
    }

    @Test
    void queryRequest_equality() {
        var request1 = new QueryRequest("test", 1);
        var request2 = new QueryRequest("test", 1);
        assertThat(request1).isEqualTo(request2);
    }

    @Test
    void queryRequest_pageCalculation() {
        var request = new QueryRequest("keyword(\"test\")", 3);
        int offset = request.page() * 20;
        assertThat(offset).isEqualTo(60);
    }

    @Test
    void queryRequest_pageZero() {
        var request = new QueryRequest("*", 0);
        int offset = request.page() * 20;
        assertThat(offset).isEqualTo(0);
    }
}
