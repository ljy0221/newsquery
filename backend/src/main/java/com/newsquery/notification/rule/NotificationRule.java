package com.newsquery.notification.rule;

import com.newsquery.event.QueryExecutionEvent;

public interface NotificationRule {
    boolean evaluate(QueryExecutionEvent event);
    String getRuleName();
    String generateMessage(QueryExecutionEvent event);
}
