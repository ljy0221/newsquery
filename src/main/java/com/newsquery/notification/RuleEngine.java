package com.newsquery.notification;

import com.newsquery.event.QueryExecutionEvent;
import com.newsquery.notification.rule.NotificationRule;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class RuleEngine {

    private final List<NotificationRule> rules = new ArrayList<>();

    public RuleEngine(List<NotificationRule> rules) {
        this.rules.addAll(rules);
    }

    public List<Notification> evaluate(QueryExecutionEvent event) {
        List<Notification> notifications = new ArrayList<>();
        for (NotificationRule rule : rules) {
            if (rule.evaluate(event)) {
                notifications.add(new Notification(
                    event.userId(),
                    rule.getRuleName(),
                    rule.generateMessage(event),
                    event.executedAt()
                ));
            }
        }
        return notifications;
    }
}
