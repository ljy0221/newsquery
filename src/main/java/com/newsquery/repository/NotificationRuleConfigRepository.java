package com.newsquery.repository;

import com.newsquery.domain.NotificationRuleConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface NotificationRuleConfigRepository extends JpaRepository<NotificationRuleConfig, String> {
    List<NotificationRuleConfig> findByRuleTypeAndEnabled(String ruleType, boolean enabled);

    List<NotificationRuleConfig> findByEnabled(boolean enabled);
}
