package com.newsquery.domain;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "user_notification_preferences")
public class UserNotificationPreference {

    @Id
    @Column(name = "id")
    private String id;

    @Column(name = "user_id", nullable = false, unique = true)
    private String userId;

    @Column(name = "email_enabled", nullable = false)
    private boolean emailEnabled;

    @Column(name = "push_enabled", nullable = false)
    private boolean pushEnabled;

    @Column(name = "console_enabled", nullable = false)
    private boolean consoleEnabled;

    @Column(name = "quiet_hours_start")
    private String quietHoursStart;

    @Column(name = "quiet_hours_end")
    private String quietHoursEnd;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public UserNotificationPreference() {}

    public UserNotificationPreference(String userId) {
        this.id = UUID.randomUUID().toString();
        this.userId = userId;
        this.emailEnabled = true;
        this.pushEnabled = true;
        this.consoleEnabled = true;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    public String getId() { return id; }
    public String getUserId() { return userId; }
    public boolean isEmailEnabled() { return emailEnabled; }
    public boolean isPushEnabled() { return pushEnabled; }
    public boolean isConsoleEnabled() { return consoleEnabled; }
    public String getQuietHoursStart() { return quietHoursStart; }
    public String getQuietHoursEnd() { return quietHoursEnd; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }

    public void setEmailEnabled(boolean emailEnabled) { this.emailEnabled = emailEnabled; }
    public void setPushEnabled(boolean pushEnabled) { this.pushEnabled = pushEnabled; }
    public void setConsoleEnabled(boolean consoleEnabled) { this.consoleEnabled = consoleEnabled; }
    public void setQuietHoursStart(String quietHoursStart) { this.quietHoursStart = quietHoursStart; }
    public void setQuietHoursEnd(String quietHoursEnd) { this.quietHoursEnd = quietHoursEnd; }
}
