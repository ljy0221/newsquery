package com.newsquery.api;

import com.newsquery.domain.QueryHistory;
import com.newsquery.domain.SavedQuery;
import com.newsquery.service.SavedQueryService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Phase 5: 저장된 검색 API
 * 사용자가 자주 사용하는 NQL 쿼리 관리
 */
@RestController
@RequestMapping("/api/queries/saved")
public class SavedQueryController {

    private final SavedQueryService savedQueryService;
    private final static String DEFAULT_USER_ID = "anonymous";  // 프로토타입용

    public SavedQueryController(SavedQueryService savedQueryService) {
        this.savedQueryService = savedQueryService;
    }

    /**
     * 검색 저장
     * POST /api/queries/saved
     */
    @PostMapping
    public ResponseEntity<SavedQuery> save(@RequestBody SaveQueryRequest request) {
        if (request.nql() == null || request.nql().isBlank()) {
            return ResponseEntity.badRequest().build();
        }

        SavedQuery query = savedQueryService.save(
                DEFAULT_USER_ID,
                request.nql(),
                request.name(),
                request.description()
        );

        return ResponseEntity.ok(query);
    }

    /**
     * 저장된 검색 목록
     * GET /api/queries/saved
     */
    @GetMapping
    public ResponseEntity<List<SavedQuery>> list() {
        List<SavedQuery> queries = savedQueryService.findByUserId(DEFAULT_USER_ID);
        return ResponseEntity.ok(queries);
    }

    /**
     * 즐겨찾기 검색만
     * GET /api/queries/saved/favorites
     */
    @GetMapping("/favorites")
    public ResponseEntity<List<SavedQuery>> favorites() {
        List<SavedQuery> queries = savedQueryService.findFavoritesByUserId(DEFAULT_USER_ID);
        return ResponseEntity.ok(queries);
    }

    /**
     * 저장된 검색 상세 조회
     * GET /api/queries/saved/{id}
     */
    @GetMapping("/{id}")
    public ResponseEntity<?> getById(@PathVariable String id) {
        return savedQueryService.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * 저장된 검색 삭제
     * DELETE /api/queries/saved/{id}
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(@PathVariable String id) {
        if (savedQueryService.findById(id).isPresent()) {
            savedQueryService.delete(id);
            return ResponseEntity.ok().build();
        }
        return ResponseEntity.notFound().build();
    }

    /**
     * 검색 실행 (저장된 검색으로)
     * POST /api/queries/saved/{id}/execute
     */
    @PostMapping("/{id}/execute")
    public ResponseEntity<?> executeById(@PathVariable String id) {
        return savedQueryService.findById(id)
                .map(query -> ResponseEntity.ok(Map.of("nql", query.getNql())))
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * 검색 히스토리 조회
     * GET /api/queries/history?limit=100
     */
    @GetMapping("/history")
    public ResponseEntity<List<QueryHistory>> history(
            @RequestParam(defaultValue = "100") int limit) {
        List<QueryHistory> histories = savedQueryService.getHistory(DEFAULT_USER_ID, limit);
        return ResponseEntity.ok(histories);
    }

    /**
     * 인기 검색어
     * GET /api/queries/trending?limit=10
     */
    @GetMapping("/trending")
    public ResponseEntity<List<Map<String, Object>>> trending(
            @RequestParam(defaultValue = "10") int limit) {
        List<Map<String, Object>> trending = savedQueryService.getTrendingQueries(DEFAULT_USER_ID, limit);
        return ResponseEntity.ok(trending);
    }

    /**
     * 검색 통계
     * GET /api/queries/stats?nql=...
     */
    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> stats(@RequestParam String nql) {
        Map<String, Object> stats = savedQueryService.getQueryStats(DEFAULT_USER_ID, nql);
        return stats.isEmpty() ? ResponseEntity.notFound().build() : ResponseEntity.ok(stats);
    }

    /**
     * 요청 DTO
     */
    public record SaveQueryRequest(String nql, String name, String description) {}
}
