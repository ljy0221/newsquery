package com.newsquery.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.newsquery.embedding.EmbeddingClient;
import com.newsquery.nql.KeywordExtractor;
import com.newsquery.nql.NQLQueryParser;
import com.newsquery.scoring.RRFScorer;
import com.newsquery.search.NewsSearchService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(QueryController.class)
class QueryControllerIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @MockBean NQLQueryParser nqlQueryParser;
    @MockBean KeywordExtractor keywordExtractor;
    @MockBean EmbeddingClient embeddingClient;
    @MockBean RRFScorer rrfScorer;
    @MockBean NewsSearchService newsSearchService;

    @Test
    void fullQueryFlow_withKeywordAndFilters() throws Exception {
        var request = new QueryRequest("keyword(\"HBM\") AND sentiment == \"positive\"", 0);
        var fakeExpr = new com.newsquery.nql.NQLExpression.KeywordExpr("HBM", null);
        ObjectNode fakeBoolQuery = new ObjectMapper().createObjectNode();
        ObjectNode fakeRetriever = new ObjectMapper().createObjectNode();

        when(nqlQueryParser.parseToExpression(any())).thenReturn(fakeExpr);
        when(nqlQueryParser.buildQuery(any())).thenReturn(fakeBoolQuery);
        when(keywordExtractor.extract(any())).thenReturn(List.of());
        when(rrfScorer.buildRetriever(any(), any(), any())).thenReturn(fakeRetriever);
        when(newsSearchService.searchWithRrf(any(), anyInt()))
                .thenReturn(new NewsSearchResponse(5L, List.of(
                        new NewsHit("1", "HBM 신제품 출시", "Reuters", "positive", "KR", "2024-01-15", 0.95, "http://reuters.com")
                )));

        mockMvc.perform(post("/api/query")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.total").value(5))
            .andExpect(jsonPath("$.hits[0].title").value("HBM 신제품 출시"));
    }

    @Test
    void fullQueryFlow_withMultipleResults() throws Exception {
        var request = new QueryRequest("keyword(\"AI\")", 0);
        var fakeExpr = new com.newsquery.nql.NQLExpression.KeywordExpr("AI", null);
        ObjectNode fakeBoolQuery = new ObjectMapper().createObjectNode();
        ObjectNode fakeRetriever = new ObjectMapper().createObjectNode();

        when(nqlQueryParser.parseToExpression(any())).thenReturn(fakeExpr);
        when(nqlQueryParser.buildQuery(any())).thenReturn(fakeBoolQuery);
        when(keywordExtractor.extract(any()))
                .thenReturn(List.of(new com.newsquery.nql.NQLExpression.KeywordExpr("AI", null)));
        when(embeddingClient.embed(any())).thenReturn(new float[]{0.1f, 0.2f, 0.3f});
        when(rrfScorer.buildRetriever(any(), any(), any())).thenReturn(fakeRetriever);
        when(newsSearchService.searchWithRrf(any(), anyInt()))
                .thenReturn(new NewsSearchResponse(3L, List.of(
                        new NewsHit("1", "AI 기술 발전", "Bloomberg", "positive", "US", "2024-01-10", 0.92, "url1"),
                        new NewsHit("2", "AI 규제 논의", "Reuters", "neutral", "EU", "2024-01-12", 0.85, "url2"),
                        new NewsHit("3", "AI 투자 증가", "TechCrunch", "positive", "US", "2024-01-14", 0.88, "url3")
                )));

        mockMvc.perform(post("/api/query")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.total").value(3))
            .andExpect(jsonPath("$.hits.length()").value(3));
    }

    @Test
    void fullQueryFlow_withPagination() throws Exception {
        var request = new QueryRequest("keyword(\"GPU\")", 2);
        var fakeExpr = new com.newsquery.nql.NQLExpression.KeywordExpr("GPU", null);
        ObjectNode fakeBoolQuery = new ObjectMapper().createObjectNode();
        ObjectNode fakeRetriever = new ObjectMapper().createObjectNode();

        when(nqlQueryParser.parseToExpression(any())).thenReturn(fakeExpr);
        when(nqlQueryParser.buildQuery(any())).thenReturn(fakeBoolQuery);
        when(keywordExtractor.extract(any())).thenReturn(List.of());
        when(rrfScorer.buildRetriever(any(), any(), any())).thenReturn(fakeRetriever);
        when(newsSearchService.searchWithRrf(any(), anyInt()))
                .thenReturn(new NewsSearchResponse(100L, List.of()));

        mockMvc.perform(post("/api/query")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.total").value(100));
    }

    @Test
    void queryResponse_structureValidation() throws Exception {
        var request = new QueryRequest("*", 0);
        var fakeExpr = new com.newsquery.nql.NQLExpression.MatchAllExpr();
        ObjectNode fakeBoolQuery = new ObjectMapper().createObjectNode();
        ObjectNode fakeRetriever = new ObjectMapper().createObjectNode();

        when(nqlQueryParser.parseToExpression(any())).thenReturn(fakeExpr);
        when(nqlQueryParser.buildQuery(any())).thenReturn(fakeBoolQuery);
        when(keywordExtractor.extract(any())).thenReturn(List.of());
        when(rrfScorer.buildRetriever(any(), any(), any())).thenReturn(fakeRetriever);
        when(newsSearchService.searchWithRrf(any(), anyInt()))
                .thenReturn(new NewsSearchResponse(10L, List.of(
                        new NewsHit("doc1", "Title", "Source", "neutral", "KR", "2024-01-01", 0.8, "http://example.com")
                )));

        mockMvc.perform(post("/api/query")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.total").exists())
            .andExpect(jsonPath("$.hits").isArray())
            .andExpect(jsonPath("$.hits[0].id").exists())
            .andExpect(jsonPath("$.hits[0].title").exists())
            .andExpect(jsonPath("$.hits[0].source").exists())
            .andExpect(jsonPath("$.hits[0].sentiment").exists())
            .andExpect(jsonPath("$.hits[0].score").exists());
    }
}
