package com.newsquery.repository;

import com.newsquery.domain.KeywordSubscription;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface KeywordSubscriptionRepository extends JpaRepository<KeywordSubscription, String> {
    List<KeywordSubscription> findByUserId(String userId);

    List<KeywordSubscription> findByUserIdAndIsActive(String userId, boolean isActive);

    Optional<KeywordSubscription> findByUserIdAndKeyword(String userId, String keyword);

    @Query("SELECT ks FROM KeywordSubscription ks WHERE ks.isActive = true GROUP BY ks.keyword ORDER BY COUNT(ks) DESC")
    List<KeywordSubscription> findTrendingKeywords();
}
