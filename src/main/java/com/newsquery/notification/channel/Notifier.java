package com.newsquery.notification.channel;

import com.newsquery.notification.Notification;

public interface Notifier {
    void send(Notification notification);
}
