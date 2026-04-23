package com.newsquery.notification.channel;

import com.newsquery.notification.Notification;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class LoggingNotifier implements Notifier {

    private static final Logger logger = LoggerFactory.getLogger(LoggingNotifier.class);

    @Override
    public void send(Notification notification) {
        logger.info("[{}] {} - User: {}",
            notification.getType(),
            notification.getMessage(),
            notification.getUserId());
    }
}
