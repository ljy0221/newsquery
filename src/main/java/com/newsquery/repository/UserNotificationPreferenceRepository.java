package com.newsquery.repository;

import com.newsquery.domain.UserNotificationPreference;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserNotificationPreferenceRepository extends JpaRepository<UserNotificationPreference, String> {
    Optional<UserNotificationPreference> findByUserId(String userId);
}
