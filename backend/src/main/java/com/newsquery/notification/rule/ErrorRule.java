package com.newsquery.notification.rule;

import com.newsquery.event.QueryExecutionEvent;
import org.springframework.stereotype.Component;

@Component
public class ErrorRule implements NotificationRule {

    @Override
    public boolean evaluate(QueryExecutionEvent event) {
        return !event.success() && event.errorMessage() != null && !event.errorMessage().isBlank();
    }

    @Override
    public String getRuleName() {
        return "ERROR_ALERT";
    }

    @Override
    public String generateMessage(QueryExecutionEvent event) {
        return String.format("❌ 쿼리 실행 오류: %s", event.errorMessage());
    }
}
