package com.newsquery.notification.channel;

import com.newsquery.notification.Notification;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Phase 6: Push 알림 채널
 * FCM(Firebase Cloud Messaging)을 통한 모바일 푸시 알림
 * 설정: fcm.api-key 속성 필요
 */
@Component
@ConditionalOnProperty(name = "fcm.api-key", matchIfMissing = true)
public class PushNotifier implements Notifier {

    // Phase 6.1: FCMClient로 교체 필요
    private static final Map<String, String> userDeviceTokens = new ConcurrentHashMap<>();

    public static void registerDeviceToken(String userId, String deviceToken) {
        userDeviceTokens.put(userId, deviceToken);
    }

    public static void unregisterDeviceToken(String userId) {
        userDeviceTokens.remove(userId);
    }

    @Override
    public void send(Notification notification) {
        String deviceToken = userDeviceTokens.get(notification.getUserId());
        if (deviceToken == null) {
            return;
        }

        try {
            sendPushNotification(deviceToken, notification);
        } catch (Exception e) {
            // Push 발송 실패가 다른 채널에 영향 X
            e.printStackTrace();
        }
    }

    private void sendPushNotification(String deviceToken, Notification notification) {
        // Phase 6.1: 실제 FCM API 호출 구현
        // POST https://fcm.googleapis.com/v1/projects/{projectId}/messages:send
        // {
        //   "message": {
        //     "token": deviceToken,
        //     "notification": {
        //       "title": notification.getType(),
        //       "body": notification.getMessage()
        //     }
        //   }
        // }
        System.out.println("[PUSH_NOTIFIER] Sending to: " + deviceToken +
            " | Type: " + notification.getType() +
            " | Message: " + notification.getMessage());
    }
}
