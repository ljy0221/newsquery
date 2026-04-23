package com.newsquery.service;

import com.newsquery.domain.QueryHistory;
import com.newsquery.domain.SavedQuery;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Phase 5: 저장된 검색 서비스
 * 인메모리 구현 (프로토타입)
 * 프로덕션에서는 PostgreSQL 연동 필요
 */
@Service
public class SavedQueryService {

    // 프로토타입: 인메모리 저장소
    private final Map<String, SavedQuery> savedQueries = new ConcurrentHashMap<>();
    private final Map<String, List<QueryHistory>> queryHistories = new ConcurrentHashMap<>();

    /**
     * 새 검색 저장
     */
    public SavedQuery save(String userId, String nql, String name, String description) {
        SavedQuery query = new SavedQuery(userId, nql, name, description);
        savedQueries.put(query.getId(), query);
        return query;
    }

    /**
     * 저장된 검색 조회
     */
    public Optional<SavedQuery> findById(String id) {
        return Optional.ofNullable(savedQueries.get(id));
    }

    /**
     * 사용자의 모든 저장된 검색
     */
    public List<SavedQuery> findByUserId(String userId) {
        return savedQueries.values().stream()
                .filter(q -> q.getUserId().equals(userId))
                .sorted((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()))
                .collect(Collectors.toList());
    }

    /**
     * 즐겨찾기 검색만
     */
    public List<SavedQuery> findFavoritesByUserId(String userId) {
        return findByUserId(userId).stream()
                .filter(SavedQuery::isFavorite)
                .collect(Collectors.toList());
    }

    /**
     * 저장된 검색 삭제
     */
    public boolean delete(String id) {
        return savedQueries.remove(id) != null;
    }

    /**
     * 검색 히스토리 기록
     */
    public void recordHistory(String userId, String nql, double responseTimeMs, int totalHits) {
        QueryHistory history = new QueryHistory(userId, nql, responseTimeMs, totalHits);
        queryHistories
                .computeIfAbsent(userId, k -> Collections.synchronizedList(new ArrayList<>()))
                .add(history);
    }

    /**
     * 검색 히스토리 기록 (오류)
     */
    public void recordHistoryError(String userId, String nql, String errorMessage) {
        QueryHistory history = new QueryHistory(userId, nql, errorMessage);
        queryHistories
                .computeIfAbsent(userId, k -> Collections.synchronizedList(new ArrayList<>()))
                .add(history);
    }

    /**
     * 사용자의 검색 히스토리 조회 (최근 순)
     */
    public List<QueryHistory> getHistory(String userId, int limit) {
        return queryHistories.getOrDefault(userId, Collections.emptyList())
                .stream()
                .sorted((a, b) -> b.getExecutedAt().compareTo(a.getExecutedAt()))
                .limit(limit)
                .collect(Collectors.toList());
    }

    /**
     * 인기 검색어 조회
     */
    public List<Map<String, Object>> getTrendingQueries(String userId, int limit) {
        return queryHistories.getOrDefault(userId, Collections.emptyList())
                .stream()
                .filter(QueryHistory::isSuccess)
                .collect(Collectors.groupingBy(
                        QueryHistory::getNql,
                        Collectors.counting()
                ))
                .entrySet().stream()
                .sorted((a, b) -> b.getValue().compareTo(a.getValue()))
                .limit(limit)
                .map(entry -> {
                    Map<String, Object> result = new HashMap<>();
                    result.put("nql", entry.getKey());
                    result.put("count", entry.getValue());
                    return result;
                })
                .collect(Collectors.toList());
    }

    /**
     * 검색 통계 조회
     */
    public Map<String, Object> getQueryStats(String userId, String nql) {
        List<QueryHistory> histories = queryHistories.getOrDefault(userId, Collections.emptyList())
                .stream()
                .filter(h -> h.getNql().equals(nql) && h.isSuccess())
                .collect(Collectors.toList());

        if (histories.isEmpty()) {
            return new HashMap<>();
        }

        double[] times = histories.stream()
                .mapToDouble(QueryHistory::getResponseTimeMs)
                .toArray();
        double avg = Arrays.stream(times).average().orElse(0.0);
        double min = Arrays.stream(times).min().orElse(0.0);
        double max = Arrays.stream(times).max().orElse(0.0);

        Map<String, Object> stats = new HashMap<>();
        stats.put("nql", nql);
        stats.put("execution_count", histories.size());
        stats.put("avg_response_time_ms", Math.round(avg * 100.0) / 100.0);
        stats.put("min_response_time_ms", Math.round(min * 100.0) / 100.0);
        stats.put("max_response_time_ms", Math.round(max * 100.0) / 100.0);
        stats.put("last_executed_at", histories.get(histories.size() - 1).getExecutedAt());

        return stats;
    }

    /**
     * 전체 저장된 검색 개수
     */
    public int getTotalSavedQueryCount() {
        return savedQueries.size();
    }

    /**
     * 전체 히스토리 개수
     */
    public int getTotalHistoryCount() {
        return queryHistories.values().stream().mapToInt(List::size).sum();
    }
}
