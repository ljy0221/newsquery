package com.newsquery.notification.channel;

import com.newsquery.notification.Notification;
import org.springframework.stereotype.Component;

@Component
public class ConsoleNotifier implements Notifier {

    @Override
    public void send(Notification notification) {
        System.out.println("[" + notification.getType() + "] " +
            notification.getMessage() + " (User: " + notification.getUserId() + ")");
    }
}
