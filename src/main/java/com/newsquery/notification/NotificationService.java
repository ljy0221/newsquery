package com.newsquery.notification;

import com.newsquery.event.EventPublisher;
import com.newsquery.event.QueryExecutionEvent;
import com.newsquery.notification.channel.Notifier;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class NotificationService implements EventPublisher.EventListener {

    private final RuleEngine ruleEngine;
    private final List<Notifier> notifiers;
    private final Map<String, List<Notification>> notificationStore = new ConcurrentHashMap<>();

    public NotificationService(RuleEngine ruleEngine, EventPublisher eventPublisher, List<Notifier> notifiers) {
        this.ruleEngine = ruleEngine;
        this.notifiers = new ArrayList<>(notifiers);
        eventPublisher.subscribe(this);
    }

    @Override
    public void onEvent(QueryExecutionEvent event) {
        List<Notification> notifications = ruleEngine.evaluate(event);
        for (Notification notification : notifications) {
            saveNotification(notification);
            sendNotification(notification);
        }
    }

    private void saveNotification(Notification notification) {
        notificationStore
            .computeIfAbsent(notification.getUserId(), k -> new ArrayList<>())
            .add(notification);
    }

    private void sendNotification(Notification notification) {
        for (Notifier notifier : notifiers) {
            try {
                notifier.send(notification);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    public List<Notification> getNotifications(String userId, int limit) {
        List<Notification> userNotifications = notificationStore.getOrDefault(userId, new ArrayList<>());
        int start = Math.max(0, userNotifications.size() - limit);
        return new ArrayList<>(userNotifications.subList(start, userNotifications.size()));
    }

    public void markAsRead(String userId, String notificationId) {
        notificationStore.getOrDefault(userId, new ArrayList<>())
            .stream()
            .filter(n -> n.getId().equals(notificationId))
            .findFirst()
            .ifPresent(Notification::markAsRead);
    }
}
