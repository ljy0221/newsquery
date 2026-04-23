package com.newsquery.repository;

import com.newsquery.domain.SavedQuery;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SavedQueryRepository extends JpaRepository<SavedQuery, String> {
    List<SavedQuery> findByUserId(String userId);

    List<SavedQuery> findByUserIdOrderByCreatedAtDesc(String userId);

    @Query("SELECT sq FROM SavedQuery sq WHERE sq.userId = :userId AND sq.isFavorite = true ORDER BY sq.createdAt DESC")
    List<SavedQuery> findFavoritesByUserId(String userId);

    @Query("SELECT sq FROM SavedQuery sq WHERE sq.userId = :userId ORDER BY sq.executionCount DESC LIMIT 5")
    List<SavedQuery> findTop5ByUserIdOrderByExecutionCountDesc(String userId);
}
