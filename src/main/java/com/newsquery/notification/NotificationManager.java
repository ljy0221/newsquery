package com.newsquery.notification;

import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * SSE (Server-Sent Events) 구독 관리
 *
 * 사용자별 SseEmitter를 관리하고, 알림을 브로드캐스트합니다.
 * - 연결 수명: 60초 타임아웃
 * - 메모리: 사용자당 ~4KB (HTTP 연결만 유지)
 * - 자동 재연결: 브라우저가 Last-Event-ID 헤더로 자동 처리
 */
@Service
public class NotificationManager {

    private static final Logger logger = LoggerFactory.getLogger(NotificationManager.class);
    private static final long EMITTER_TIMEOUT = 60000L; // 60초

    // userId → {emitters}
    private final Map<String, Set<SseEmitter>> userEmitters =
        new ConcurrentHashMap<>();

    // 사용자의 마지막 이벤트 ID (재연결 복구용)
    private final Map<String, String> lastEventIds =
        new ConcurrentHashMap<>();

    /**
     * SSE 구독 등록
     *
     * @param userId 사용자 ID
     * @param lastEventId 마지막 이벤트 ID (재연결 시, null 가능)
     * @return SseEmitter (클라이언트에게 응답)
     */
    public SseEmitter subscribe(String userId, String lastEventId) {
        // 1. SseEmitter 생성 (60초 타임아웃)
        SseEmitter emitter = new SseEmitter(EMITTER_TIMEOUT);

        // 2. 사용자의 emitter 목록에 추가
        userEmitters.computeIfAbsent(userId, k -> ConcurrentHashMap.newKeySet())
                .add(emitter);

        logger.debug("SSE 구독 시작: userId={}, 활성 구독자={}",
            userId, userEmitters.get(userId).size());

        // 3. 마지막 이벤트 ID 저장
        if (lastEventId != null) {
            lastEventIds.put(userId, lastEventId);
        }

        // 4. 콜백 등록
        emitter.onTimeout(() -> handleTimeout(userId, emitter));
        emitter.onCompletion(() -> handleCompletion(userId, emitter));
        emitter.onError(throwable -> handleError(userId, emitter, throwable));

        // 5. 초기 연결 확인 메시지
        try {
            emitter.send(SseEmitter.event()
                .name("connected")
                .data(Map.of("message", "SSE 연결 성공", "userId", userId))
                .id(UUID.randomUUID().toString())
                .build());

            logger.debug("SSE 초기 메시지 전송 완료: userId={}", userId);
        } catch (IOException e) {
            logger.debug("SSE 초기 메시지 전송 실패: userId={}, error={}", userId, e.getMessage());
            userEmitters.get(userId).remove(emitter);
        }

        return emitter;
    }

    /**
     * 특정 사용자에게 알림 전송
     *
     * @param userId 사용자 ID
     * @param notification 알림 객체
     */
    public void notify(String userId, Notification notification) {
        Set<SseEmitter> emitters = userEmitters.get(userId);

        if (emitters == null || emitters.isEmpty()) {
            logger.debug("구독자 없음: userId={}", userId);
            return;
        }

        // 이벤트 ID 생성 및 저장
        String eventId = notification.getId();
        lastEventIds.put(userId, eventId);

        // 모든 emitter에 전송
        List<SseEmitter> deadEmitters = new ArrayList<>();

        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event()
                    .name("notification")
                    .id(eventId)
                    .data(notification)
                    .reconnectTime(3000)  // 3초 자동 재연결
                    .build());

                logger.debug("알림 발송 성공: userId={}, notificationId={}",
                    userId, eventId);

            } catch (IOException e) {
                logger.debug("알림 발송 실패 (연결 끊김): userId={}, error={}",
                    userId, e.getMessage());
                deadEmitters.add(emitter);
            }
        }

        // 연결 끊긴 emitter 배치 제거 (ConcurrentModificationException 방지)
        deadEmitters.forEach(emitters::remove);

        if (!deadEmitters.isEmpty()) {
            logger.debug("Dead emitter 제거됨: userId={}, count={}",
                userId, deadEmitters.size());
        }
    }

    /**
     * 모든 사용자에게 브로드캐스트 (선택)
     *
     * @param notification 알림 객체
     */
    public void broadcastNotification(Notification notification) {
        logger.info("브로드캐스트 시작: 구독자={}", userEmitters.keySet().size());

        for (String userId : userEmitters.keySet()) {
            notify(userId, notification);
        }
    }

    /**
     * 하트비트 전송 (프록시 연결 유지)
     * 주석은 클라이언트에서 무시되지만 연결을 유지합니다.
     *
     * @param userId 사용자 ID (선택, null이면 모든 사용자)
     */
    public void sendHeartbeat(String userId) {
        Set<SseEmitter> emitters = userEmitters.get(userId);

        if (emitters == null || emitters.isEmpty()) {
            return;
        }

        List<SseEmitter> deadEmitters = new ArrayList<>();

        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event()
                    .comment("heartbeat at " + System.currentTimeMillis())
                    .build());

            } catch (IOException e) {
                deadEmitters.add(emitter);
            }
        }

        deadEmitters.forEach(emitters::remove);
    }

    /**
     * 사용자 구독 해제 (수동)
     */
    public void unsubscribe(String userId, SseEmitter emitter) {
        Set<SseEmitter> emitters = userEmitters.get(userId);

        if (emitters != null) {
            emitters.remove(emitter);

            if (emitters.isEmpty()) {
                userEmitters.remove(userId);
                lastEventIds.remove(userId);
                logger.debug("사용자 구독 완전 해제: userId={}", userId);
            }
        }
    }

    /**
     * 현재 활성 구독자 수 조회 (모니터링용)
     */
    public int getActiveSubscriberCount() {
        return userEmitters.values().stream()
            .mapToInt(Set::size)
            .sum();
    }

    /**
     * 사용자별 구독자 수 조회
     */
    public int getSubscriberCountForUser(String userId) {
        Set<SseEmitter> emitters = userEmitters.get(userId);
        return emitters != null ? emitters.size() : 0;
    }

    // ===== 콜백 메서드 =====

    private void handleTimeout(String userId, SseEmitter emitter) {
        logger.debug("SSE 타임아웃: userId={} (클라이언트가 재연결할 것)", userId);
        // 정상 동작: 클라이언트가 자동으로 재연결 시도
    }

    private void handleCompletion(String userId, SseEmitter emitter) {
        logger.debug("SSE 완료: userId={}", userId);
        unsubscribe(userId, emitter);
    }

    private void handleError(String userId, SseEmitter emitter, Throwable ex) {
        logger.debug("SSE 에러: userId={}, error={}", userId, ex.getMessage());
        unsubscribe(userId, emitter);
    }
}
