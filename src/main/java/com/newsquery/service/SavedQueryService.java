package com.newsquery.service;

import com.newsquery.domain.QueryHistory;
import com.newsquery.domain.SavedQuery;
import com.newsquery.repository.QueryHistoryRepository;
import com.newsquery.repository.SavedQueryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Transactional
public class SavedQueryService {

    private final SavedQueryRepository savedQueryRepository;
    private final QueryHistoryRepository queryHistoryRepository;

    public SavedQueryService(SavedQueryRepository savedQueryRepository, QueryHistoryRepository queryHistoryRepository) {
        this.savedQueryRepository = savedQueryRepository;
        this.queryHistoryRepository = queryHistoryRepository;
    }

    public SavedQuery save(String userId, String nql, String name, String description) {
        SavedQuery query = new SavedQuery(userId, nql, name, description);
        return savedQueryRepository.save(query);
    }

    @Transactional(readOnly = true)
    public Optional<SavedQuery> findById(String id) {
        return savedQueryRepository.findById(id);
    }

    @Transactional(readOnly = true)
    public List<SavedQuery> findByUserId(String userId) {
        return savedQueryRepository.findByUserIdOrderByCreatedAtDesc(userId);
    }

    @Transactional(readOnly = true)
    public List<SavedQuery> findFavoritesByUserId(String userId) {
        return savedQueryRepository.findFavoritesByUserId(userId);
    }

    public void delete(String id) {
        savedQueryRepository.deleteById(id);
    }

    @Transactional(readOnly = true)
    public SavedQuery findById(String userId, String queryId) {
        return savedQueryRepository.findById(queryId)
                .filter(q -> q.getUserId().equals(userId))
                .orElse(null);
    }

    public void updateFavorite(String id, boolean isFavorite) {
        savedQueryRepository.findById(id).ifPresent(query -> {
            query.setFavorite(isFavorite);
            savedQueryRepository.save(query);
        });
    }

    public void recordHistory(String userId, String nql, double responseTimeMs, long totalHits) {
        QueryHistory history = new QueryHistory(userId, nql, responseTimeMs, totalHits, true, null);
        queryHistoryRepository.save(history);
    }

    public void recordHistoryError(String userId, String nql, String errorMessage) {
        QueryHistory history = new QueryHistory(userId, nql, 0, 0, false, errorMessage);
        queryHistoryRepository.save(history);
    }

    @Transactional(readOnly = true)
    public List<QueryHistory> getHistory(String userId, int limit) {
        List<QueryHistory> histories = queryHistoryRepository.findByUserIdOrderByExecutedAtDesc(userId);
        return histories.stream().limit(limit).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> getTrendingQueries(String userId, int limit) {
        List<QueryHistory> histories = queryHistoryRepository.findByUserIdOrderByExecutedAtDesc(userId);
        return histories.stream()
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

    @Transactional(readOnly = true)
    public Map<String, Object> getQueryStats(String userId, String nql) {
        List<QueryHistory> histories = queryHistoryRepository.findByUserIdOrderByExecutedAtDesc(userId)
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

    @Transactional(readOnly = true)
    public long getTotalSavedQueryCount() {
        return savedQueryRepository.count();
    }

    @Transactional(readOnly = true)
    public long getTotalHistoryCount() {
        return queryHistoryRepository.count();
    }
}
