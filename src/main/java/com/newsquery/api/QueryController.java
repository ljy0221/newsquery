package com.newsquery.api;

import com.newsquery.nql.NQLQueryParser;
import com.newsquery.scoring.RRFScorer;
import com.newsquery.search.NewsSearchService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;

@RestController
@RequestMapping("/api")
public class QueryController {

    private final NQLQueryParser nqlQueryParser;
    private final NewsSearchService newsSearchService;
    private final RRFScorer rrfScorer;

    public QueryController(NQLQueryParser nqlQueryParser,
                           NewsSearchService newsSearchService,
                           RRFScorer rrfScorer) {
        this.nqlQueryParser = nqlQueryParser;
        this.newsSearchService = newsSearchService;
        this.rrfScorer = rrfScorer;
    }

    @PostMapping("/query")
    public ResponseEntity<?> query(@RequestBody QueryRequest request) {
        if (request.nql() == null || request.nql().isBlank()) {
            return ResponseEntity.badRequest().body("nql 필드가 비어있습니다.");
        }
        try {
            var esQuery = nqlQueryParser.parseToQuery(request.nql());
            NewsSearchResponse result = newsSearchService.search(esQuery);
            return ResponseEntity.ok(result);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (IOException e) {
            return ResponseEntity.internalServerError().body("ES 검색 오류: " + e.getMessage());
        }
    }
}
