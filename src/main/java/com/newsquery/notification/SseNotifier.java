package com.newsquery.notification;

import com.newsquery.notification.channel.Notifier;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * SSE 알림 채널 구현
 *
 * NotificationService의 알림을 클라이언트의 SSE 연결로 전송합니다.
 * Phase 6에서 EmailNotifier, ConsoleNotifier와 함께 작동합니다.
 */
@Service
public class SseNotifier implements Notifier {

    private static final Logger logger = LoggerFactory.getLogger(SseNotifier.class);

    private final NotificationManager notificationManager;

    public SseNotifier(NotificationManager notificationManager) {
        this.notificationManager = notificationManager;
    }

    /**
     * SSE로 알림 전송 (Notifier 인터페이스 구현)
     *
     * @param notification 알림 객체
     */
    @Override
    public void send(Notification notification) {
        // NotificationService에서 호출: notification.getUserId()를 사용
        String userId = notification.getUserId();
        sendToUser(userId, notification);
    }

    /**
     * 특정 사용자에게 SSE 알림 전송
     *
     * @param userId 사용자 ID
     * @param notification 알림 객체
     */
    public void sendToUser(String userId, Notification notification) {
        try {
            // NotificationManager를 통해 사용자에게 푸시
            notificationManager.notify(userId, notification);

            logger.debug("SSE 알림 발송: userId={}, type={}, message={}",
                userId, notification.getType(), notification.getMessage());

        } catch (Exception e) {
            logger.error("SSE 알림 발송 실패: userId={}, type={}, error={}",
                userId, notification.getType(), e.getMessage());
            // 예외 발생해도 다른 채널(이메일 등)에는 영향 없음
        }
    }

    /**
     * 모든 사용자에게 브로드캐스트
     */
    public void broadcastNotification(Notification notification) {
        try {
            notificationManager.broadcastNotification(notification);

            logger.info("SSE 브로드캐스트 발송: type={}", notification.getType());

        } catch (Exception e) {
            logger.error("SSE 브로드캐스트 실패: type={}, error={}",
                notification.getType(), e.getMessage(), e);
        }
    }

    /**
     * 현재 활성 구독자 수 조회 (모니터링용)
     */
    public int getActiveSubscriberCount() {
        return notificationManager.getActiveSubscriberCount();
    }
}
