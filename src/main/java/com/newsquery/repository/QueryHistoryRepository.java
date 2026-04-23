package com.newsquery.repository;

import com.newsquery.domain.QueryHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface QueryHistoryRepository extends JpaRepository<QueryHistory, String> {
    List<QueryHistory> findByUserId(String userId);

    List<QueryHistory> findByUserIdOrderByExecutedAtDesc(String userId);

    List<QueryHistory> findByUserIdAndExecutedAtAfterOrderByExecutedAtDesc(String userId, LocalDateTime startTime);

    @Query("SELECT qh FROM QueryHistory qh WHERE qh.userId = :userId AND qh.success = false ORDER BY qh.executedAt DESC")
    List<QueryHistory> findFailedQueriesByUserId(String userId);

    @Query("SELECT qh FROM QueryHistory qh WHERE qh.userId = :userId ORDER BY qh.responseTimeMs DESC LIMIT 10")
    List<QueryHistory> findTop10SlowQueriesByUserId(String userId);

    @Query("SELECT AVG(qh.responseTimeMs) FROM QueryHistory qh WHERE qh.userId = :userId")
    Double getAverageResponseTimeByUserId(String userId);
}
