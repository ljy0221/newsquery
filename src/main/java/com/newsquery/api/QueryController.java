package com.newsquery.api;

import com.newsquery.nql.NQLQueryParser;
import com.newsquery.scoring.RRFScorer;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class QueryController {

    private final NQLQueryParser nqlQueryParser;
    private final RRFScorer rrfScorer;

    public QueryController(NQLQueryParser nqlQueryParser, RRFScorer rrfScorer) {
        this.nqlQueryParser = nqlQueryParser;
        this.rrfScorer = rrfScorer;
    }

    @PostMapping("/query")
    public ResponseEntity<?> query(@RequestBody QueryRequest request) {
        if (request.nql() == null || request.nql().isBlank()) {
            return ResponseEntity.badRequest().body("nql 필드가 비어있습니다.");
        }
        try {
            var esQuery = nqlQueryParser.parseToQuery(request.nql());
            // TODO: ES 실행 연동 (현재는 생성된 쿼리 반환)
            return ResponseEntity.ok(esQuery);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }
}