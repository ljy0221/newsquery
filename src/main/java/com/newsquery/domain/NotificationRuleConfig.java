package com.newsquery.domain;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "notification_rule_configs")
public class NotificationRuleConfig {

    @Id
    @Column(name = "id")
    private String id;

    @Column(name = "rule_type", nullable = false)
    private String ruleType;

    @Column(name = "enabled", nullable = false)
    private boolean enabled;

    @Column(name = "condition_json", columnDefinition = "TEXT")
    private String conditionJson;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public NotificationRuleConfig() {}

    public NotificationRuleConfig(String ruleType, boolean enabled, String conditionJson) {
        this.id = UUID.randomUUID().toString();
        this.ruleType = ruleType;
        this.enabled = enabled;
        this.conditionJson = conditionJson;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    public String getId() { return id; }
    public String getRuleType() { return ruleType; }
    public boolean isEnabled() { return enabled; }
    public String getConditionJson() { return conditionJson; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }

    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public void setConditionJson(String conditionJson) { this.conditionJson = conditionJson; }
}
