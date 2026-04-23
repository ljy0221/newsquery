package com.newsquery.notification.channel;

import com.newsquery.notification.Notification;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Phase 6: WebSocket 실시간 알림 채널 (향후 구현)
 * 클라이언트에게 실시간으로 알림을 전송
 *
 * 현재: 로깅만 수행 (spring-boot-starter-websocket 의존성 추가 필요)
 * Phase 6.1: 실제 WebSocket 구현
 */
@Component
@ConditionalOnProperty(name = "websocket.enabled", havingValue = "true", matchIfMissing = false)
public class WebSocketNotifier implements Notifier {

    private static final Map<String, Object> sessions = new ConcurrentHashMap<>();

    public static void registerSession(String userId, Object session) {
        sessions.put(userId, session);
    }

    public static void unregisterSession(String userId) {
        sessions.remove(userId);
    }

    @Override
    public void send(Notification notification) {
        Object session = sessions.get(notification.getUserId());
        if (session == null) {
            return;
        }

        try {
            System.out.println("[WEBSOCKET_NOTIFIER] Sending to: " + notification.getUserId() +
                " | Type: " + notification.getType() +
                " | Message: " + notification.getMessage());
        } catch (Exception e) {
            unregisterSession(notification.getUserId());
            e.printStackTrace();
        }
    }

    public static int getConnectedUserCount() {
        return sessions.size();
    }
}
