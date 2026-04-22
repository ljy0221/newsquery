package com.newsquery.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.newsquery.embedding.EmbeddingClient;
import com.newsquery.nql.KeywordExtractor;
import com.newsquery.nql.NQLExpression;
import com.newsquery.nql.NQLQueryParser;
import com.newsquery.scoring.RRFScorer;
import com.newsquery.search.NewsSearchService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(QueryController.class)
class QueryControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @MockBean NQLQueryParser nqlQueryParser;
    @MockBean KeywordExtractor keywordExtractor;
    @MockBean EmbeddingClient embeddingClient;
    @MockBean RRFScorer rrfScorer;
    @MockBean NewsSearchService newsSearchService;

    @Test
    void postQuery_withValidNql_returns200() throws Exception {
        var request   = new QueryRequest("keyword(\"HBM\") AND sentiment == \"positive\"", 0);
        var fakeExpr  = new NQLExpression.KeywordExpr("HBM", null);
        ObjectNode fakeBoolQuery   = new ObjectMapper().createObjectNode();
        ObjectNode fakeRetriever   = new ObjectMapper().createObjectNode();

        when(nqlQueryParser.parseToExpression(any())).thenReturn(fakeExpr);
        when(nqlQueryParser.buildQuery(any())).thenReturn(fakeBoolQuery);
        when(keywordExtractor.extract(any())).thenReturn(List.of());
        when(rrfScorer.buildRetriever(any(), any(), any())).thenReturn(fakeRetriever);
        when(newsSearchService.searchWithRrf(any(), anyInt())).thenReturn(new NewsSearchResponse(0L, List.of()));

        mockMvc.perform(post("/api/query")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk());
    }

    @Test
    void postQuery_withEmptyNql_returns400() throws Exception {
        var request = new QueryRequest("", 0);

        mockMvc.perform(post("/api/query")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest());
    }
}
