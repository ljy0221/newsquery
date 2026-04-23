# Phase 6 학습 3: N-QL 프로젝트에 SSE 적용 - 실전 구현

## 📚 목차
1. [아키텍처 설계](#아키텍처-설계)
2. [Spring SseEmitter 상세](#spring-ssemitter-상세)
3. [서버 구현](#서버-구현)
4. [클라이언트 구현](#클라이언트-구현)
5. [통합 흐름](#통합-흐름)
6. [트러블슈팅](#트러블슈팅)

---

## 아키텍처 설계

### 🏗️ Phase 6 시스템 아키텍처

```
QueryController (API 엔드포인트)
    ↓ POST /api/query
    ↓ (NQL 파싱 → ES 검색)
    ↓
QueryExecutionEvent 발행 (success/error)
    ↓
EventPublisher (옵저버 패턴)
    ├→ RuleEngine
    │   ├→ PerformanceRule
    │   ├→ ErrorRule
    │   └→ KeywordRule
    │
    └→ NotificationService (Phase 5)
        ├→ EmailNotifier
        ├→ ConsoleNotifier
        └→ SseNotifier ✨ (NEW)
             ↓
        NotificationManager (SSE 구독 관리)
             ↓
        SseEmitter (클라이언트별)
             ↓
        [클라이언트] EventSource
             ↓
        UI 알림 표시
```

### 📊 데이터 흐름

```
1. 사용자 쿼리 실행
   QueryController.executeQuery(nql)

2. 이벤트 발행
   eventPublisher.publish(QueryExecutionEvent)

3. 규칙 평가
   RuleEngine.evaluateRules(event)

4. 규칙 통과 → Notification 생성
   Notification {
     id: UUID
     type: PERFORMANCE_ALERT | ERROR_ALERT | KEYWORD_ALERT
     message: String
     details: Map
   }

5. 채널 분기
   EmailNotifier.send(notification)     ← 이메일
   ConsoleNotifier.send(notification)   ← 콘솔 로그
   SseNotifier.send(notification)       ← SSE ✨

6. SSE 구독자에게 푸시
   NotificationManager.notify(userId, notification)
     → SseEmitter.send(event + data)

7. 클라이언트 수신
   eventSource.addEventListener('notification', handler)
     → showNotification(notification)
```

---

## Spring SseEmitter 상세

### 기초: SseEmitter란?

```java
// Spring Framework의 SSE 구현체
public class SseEmitter extends ResponseBodyEmitter {
    public void send(Object data) throws IOException;
    public void send(SendableTypeHttpMessageConverter converter, Object data) throws IOException;
    public void complete();
    public void completeWithError(Throwable ex);
    public void onTimeout(Runnable callback);
    public void onCompletion(Runnable callback);
    public void onError(Consumer<Throwable> callback);
}
```

### 중급: 타임아웃과 생명주기

```java
// 1. 생성 (타임아웃 60초)
SseEmitter emitter = new SseEmitter(60000L);

// 2. 클라이언트 연결 대기 (30초 기본 타임아웃)
@GetMapping("/stream")
public SseEmitter subscribe() {
    return emitter;
}

// 3. 타임아웃 발생 (60초 초과)
// → emitter.onTimeout() 콜백 실행
// → 클라이언트는 자동으로 재연결 시도

// 4. 데이터 전송
try {
    emitter.send(SseEmitter.event()
        .name("notification")
        .data(notification)
        .build());
} catch (IOException e) {
    // 연결 끊김
    emitter.completeWithError(e);
}

// 5. 정상 완료
emitter.complete();

// 6. 에러로 완료
emitter.completeWithError(new Exception("Error"));
```

### 심화: SseEmitter.event() 빌더

```java
// SseEmitter.event() 상세 메서드

SseEmitter.event()
    // 1. 이벤트 이름 (필수 권장)
    .name("notification")
    
    // 2. 이벤트 ID (재연결 시 Last-Event-ID)
    .id("msg-" + System.currentTimeMillis())
    
    // 3. 재연결 간격 (ms)
    .reconnectTime(3000)
    
    // 4. 실제 데이터 (JSON 또는 문자열)
    .data(notification)
    
    // 5. 주석 (하트비트 용)
    .comment("server heartbeat")
    
    // 빌드
    .build()
```

#### event() vs comment()

```java
// 방식 1: event() - 클라이언트에서 처리
emitter.send(SseEmitter.event()
    .name("notification")
    .data("Hello")
    .build());

// 클라이언트
eventSource.addEventListener('notification', e => {
    console.log(e.data);  // "Hello"
});

// 방식 2: comment() - 클라이언트에서 무시 (하트비트)
emitter.send(SseEmitter.event()
    .comment("heartbeat")
    .build());

// 클라이언트 (자동 무시, 연결만 유지)
```

#### data() 메서드 체이닝

```java
// ❌ 잘못된 예: 여러 번 호출
SseEmitter.event()
    .data("line1")
    .data("line2")
    .build()

// 실제 결과:
// data: line2  (마지막 호출만 적용)

// ✅ 올바른 예: 한 번만 호출
SseEmitter.event()
    .data("line1\nline2")  // 수동으로 줄바꿈
    .build()

// 또는 객체로 전달 (자동 JSON 변환)
SseEmitter.event()
    .data(Map.of("line1", "value1", "line2", "value2"))
    .build()
```

---

## 서버 구현

### 1️⃣ NotificationManager - SSE 구독 관리

```java
package com.newsquery.notification;

import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.springframework.stereotype.Service;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class NotificationManager {
    
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
     * @param lastEventId 마지막 이벤트 ID (재연결 시)
     * @return SseEmitter
     */
    public SseEmitter subscribe(String userId, String lastEventId) {
        // 1. SseEmitter 생성 (60초 타임아웃)
        SseEmitter emitter = new SseEmitter(60000L);
        
        // 2. 사용자의 emitter 목록에 추가
        userEmitters.computeIfAbsent(userId, k -> ConcurrentHashMap.newKeySet())
                .add(emitter);
        
        // 3. 마지막 이벤트 ID 저장
        if (lastEventId != null) {
            lastEventIds.put(userId, lastEventId);
        }
        
        // 4. 콜백 등록
        emitter.onTimeout(() -> handleTimeout(userId, emitter));
        emitter.onCompletion(() -> handleCompletion(userId, emitter));
        emitter.onError(throwable -> handleError(userId, emitter, throwable));
        
        // 5. 초기 연결 확인 메시지 (선택)
        try {
            emitter.send(SseEmitter.event()
                .name("connected")
                .data(Map.of("message", "SSE 연결 성공"))
                .id(UUID.randomUUID().toString())
                .build());
        } catch (IOException e) {
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
            return;  // 구독자 없음
        }
        
        // 이벤트 ID 생성
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
            } catch (IOException e) {
                // 연결 끊김 → 나중에 제거
                deadEmitters.add(emitter);
            }
        }
        
        // 연결 끊긴 emitter 제거
        deadEmitters.forEach(emitters::remove);
    }
    
    /**
     * 모든 사용자에게 브로드캐스트 (선택 사항)
     */
    public void broadcastNotification(Notification notification) {
        for (String userId : userEmitters.keySet()) {
            notify(userId, notification);
        }
    }
    
    /**
     * 하트비트 전송 (프록시 연결 유지)
     */
    public void sendHeartbeat(String userId) {
        Set<SseEmitter> emitters = userEmitters.get(userId);
        
        if (emitters == null) return;
        
        List<SseEmitter> deadEmitters = new ArrayList<>();
        
        for (SseEmitter emitter : emitters) {
            try {
                // 주석은 클라이언트에서 무시되지만 연결 유지
                emitter.send(SseEmitter.event()
                    .comment("server heartbeat at " + System.currentTimeMillis())
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
            }
        }
    }
    
    // 콜백 메서드
    private void handleTimeout(String userId, SseEmitter emitter) {
        // 타임아웃: 정상 동작 (클라이언트가 자동 재연결)
        // 로그만 기록
    }
    
    private void handleCompletion(String userId, SseEmitter emitter) {
        unsubscribe(userId, emitter);
    }
    
    private void handleError(String userId, SseEmitter emitter, Throwable ex) {
        unsubscribe(userId, emitter);
    }
}
```

### 2️⃣ SseNotifier - 알림 채널 구현

```java
package com.newsquery.notification;

import org.springframework.stereotype.Service;
import lombok.RequiredArgsConstructor;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class SseNotifier implements NotificationChannelNotifier {
    
    private final NotificationManager notificationManager;
    private final NotificationLogger logger;
    
    @Override
    public void send(String userId, Notification notification) {
        try {
            // 1. 사용자에게 알림 전송
            notificationManager.notify(userId, notification);
            
            // 2. 로깅
            logger.log(
                userId,
                notification.getType(),
                "SSE 발송 성공",
                notification.getMessage()
            );
            
        } catch (Exception e) {
            // 3. 에러 로깅 (메일 전송은 영향 없음)
            logger.logError(
                userId,
                notification.getType(),
                "SSE 발송 실패",
                e.getMessage()
            );
        }
    }
}
```

### 3️⃣ NotificationController - SSE 엔드포인트

```java
package com.newsquery.api;

import com.newsquery.notification.NotificationManager;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {
    
    private final NotificationManager notificationManager;
    private static final String DEFAULT_USER_ID = "anonymous";
    
    /**
     * SSE 구독 엔드포인트
     * 
     * GET /api/notifications/stream?user=anonymous&lastEventId=msg-1234
     * 
     * 응답:
     * HTTP/1.1 200 OK
     * Content-Type: text/event-stream
     * Cache-Control: no-cache
     * Connection: keep-alive
     * 
     * :heartbeat
     * event: notification
     * id: msg-1235
     * data: {...}
     */
    @GetMapping("/stream")
    public SseEmitter subscribe(
        @RequestParam(defaultValue = DEFAULT_USER_ID) String user,
        @RequestHeader(value = "Last-Event-ID", required = false) String lastEventId) {
        
        // NotificationManager에서 처리
        return notificationManager.subscribe(user, lastEventId);
    }
    
    /**
     * 테스트용 수동 알림 발송
     * POST /api/notifications/test-send
     */
    @PostMapping("/test-send")
    public ResponseEntity<?> testSend(
        @RequestParam(defaultValue = DEFAULT_USER_ID) String user,
        @RequestBody Map<String, String> payload) {
        
        Notification notification = new Notification(
            UUID.randomUUID().toString(),
            NotificationType.valueOf(payload.get("type")),
            payload.get("message")
        );
        
        notificationManager.notify(user, notification);
        
        return ResponseEntity.ok(Map.of("status", "success"));
    }
}
```

### 4️⃣ 하트비트 스케줄러 (선택)

```java
package com.newsquery.notification;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class SseHeartbeatScheduler {
    
    private final NotificationManager notificationManager;
    
    /**
     * 30초마다 모든 사용자에게 하트비트 전송
     * (프록시가 유휴 연결을 자르는 것 방지)
     */
    @Scheduled(fixedRate = 30000)
    public void sendHeartbeatToAllUsers() {
        // 현재 구현에선 사용자 목록을 추적하지 않으므로
        // 각 알림 전송 시 이미 연결 확인됨
        // (필요시 NotificationManager에 getAllUsers() 메서드 추가)
    }
}
```

---

## 클라이언트 구현

### 1️⃣ React 훅 - useSSE

```typescript
// frontend/hooks/useSSE.ts

import { useEffect, useState, useCallback } from 'react';

export interface Notification {
    id: string;
    type: 'PERFORMANCE_ALERT' | 'ERROR_ALERT' | 'KEYWORD_ALERT';
    message: string;
    details?: Record<string, any>;
}

export const useSSE = (userId: string = 'anonymous') => {
    const [notifications, setNotifications] = useState<Notification[]>([]);
    const [connectionStatus, setConnectionStatus] = useState<'connecting' | 'connected' | 'disconnected'>('connecting');
    const [error, setError] = useState<string | null>(null);

    useEffect(() => {
        // 1. EventSource 생성
        const eventSource = new EventSource(
            `/api/notifications/stream?user=${userId}`,
            { withCredentials: true }
        );

        // 2. 연결 열림
        eventSource.onopen = () => {
            setConnectionStatus('connected');
            setError(null);
            console.log('SSE 연결 성공');
        };

        // 3. 알림 수신 (이벤트 타입: 'notification')
        eventSource.addEventListener('notification', (event: MessageEvent) => {
            try {
                const notification: Notification = JSON.parse(event.data);
                setNotifications((prev) => [notification, ...prev].slice(0, 50)); // 최근 50개 유지
                console.log('알림 수신:', notification);
            } catch (e) {
                console.error('알림 파싱 에러:', e);
            }
        });

        // 4. 초기 연결 확인
        eventSource.addEventListener('connected', (event: MessageEvent) => {
            console.log('SSE 초기 연결 확인:', event.data);
        });

        // 5. 에러 처리
        eventSource.onerror = () => {
            if (eventSource.readyState === EventSource.CONNECTING) {
                setConnectionStatus('connecting');
                console.warn('SSE 재연결 중...');
            } else {
                setConnectionStatus('disconnected');
                setError('SSE 연결 실패');
                console.error('SSE 연결 끊김, 수동 재연결 필요');
                eventSource.close();
            }
        };

        // 6. 정리: 컴포넌트 언마운트 시 연결 종료
        return () => {
            eventSource.close();
        };
    }, [userId]);

    return {
        notifications,
        connectionStatus,
        error,
        clearNotifications: () => setNotifications([]),
    };
};
```

### 2️⃣ NotificationCenter 컴포넌트

```typescript
// frontend/components/NotificationCenter.tsx

import React from 'react';
import { useSSE } from '../hooks/useSSE';
import NotificationItem from './NotificationItem';
import './NotificationCenter.css';

export const NotificationCenter: React.FC = () => {
    const { notifications, connectionStatus, error, clearNotifications } = useSSE();

    return (
        <div className="notification-center">
            {/* 1. 상태 표시기 */}
            <div className={`status-indicator status-${connectionStatus}`}>
                <span className="status-dot"></span>
                <span className="status-text">
                    {connectionStatus === 'connected' && 'SSE 연결됨'}
                    {connectionStatus === 'connecting' && 'SSE 재연결 중...'}
                    {connectionStatus === 'disconnected' && 'SSE 연결 실패'}
                </span>
            </div>

            {/* 2. 에러 메시지 */}
            {error && (
                <div className="error-banner">
                    {error}
                    <button onClick={() => location.reload()}>페이지 새로고침</button>
                </div>
            )}

            {/* 3. 알림 목록 */}
            <div className="notifications-list">
                {notifications.length === 0 ? (
                    <p className="empty-state">알림이 없습니다</p>
                ) : (
                    <>
                        <button 
                            className="clear-button" 
                            onClick={clearNotifications}
                        >
                            모두 지우기
                        </button>
                        {notifications.map((notification) => (
                            <NotificationItem 
                                key={notification.id} 
                                notification={notification} 
                            />
                        ))}
                    </>
                )}
            </div>
        </div>
    );
};
```

### 3️⃣ NotificationItem 컴포넌트

```typescript
// frontend/components/NotificationItem.tsx

import React from 'react';
import { Notification } from '../hooks/useSSE';

interface Props {
    notification: Notification;
}

const NotificationItem: React.FC<Props> = ({ notification }) => {
    const getIcon = (type: string) => {
        switch (type) {
            case 'PERFORMANCE_ALERT':
                return '⚡';
            case 'ERROR_ALERT':
                return '❌';
            case 'KEYWORD_ALERT':
                return '🔑';
            default:
                return 'ℹ️';
        }
    };

    const getClassName = (type: string) => {
        return `notification-item notification-${type.toLowerCase()}`;
    };

    return (
        <div className={getClassName(notification.type)}>
            <div className="notification-header">
                <span className="icon">{getIcon(notification.type)}</span>
                <span className="type">{notification.type}</span>
                <span className="time">
                    {new Date().toLocaleTimeString()}
                </span>
            </div>
            <div className="notification-body">
                <p className="message">{notification.message}</p>
                {notification.details && (
                    <pre className="details">
                        {JSON.stringify(notification.details, null, 2)}
                    </pre>
                )}
            </div>
        </div>
    );
};

export default NotificationItem;
```

### 4️⃣ CSS 스타일링

```css
/* frontend/components/NotificationCenter.css */

.notification-center {
    max-width: 400px;
    border: 1px solid #ddd;
    border-radius: 8px;
    box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
    overflow: hidden;
    background: white;
}

/* 상태 표시기 */
.status-indicator {
    display: flex;
    align-items: center;
    gap: 8px;
    padding: 12px 16px;
    background: #f5f5f5;
    border-bottom: 1px solid #eee;
    font-size: 14px;
    font-weight: 500;
}

.status-dot {
    display: inline-block;
    width: 8px;
    height: 8px;
    border-radius: 50%;
}

.status-connected .status-dot {
    background: #4caf50;
    animation: pulse 2s infinite;
}

.status-connecting .status-dot {
    background: #ff9800;
    animation: blink 1s infinite;
}

.status-disconnected .status-dot {
    background: #f44336;
}

@keyframes pulse {
    0%, 100% { opacity: 1; }
    50% { opacity: 0.5; }
}

@keyframes blink {
    0%, 50%, 100% { opacity: 1; }
    25%, 75% { opacity: 0; }
}

/* 알림 목록 */
.notifications-list {
    max-height: 600px;
    overflow-y: auto;
}

.empty-state {
    padding: 32px 16px;
    text-align: center;
    color: #999;
}

.clear-button {
    width: 100%;
    padding: 8px;
    border: none;
    background: #f5f5f5;
    cursor: pointer;
    font-size: 12px;
    color: #666;
}

/* 알림 항목 */
.notification-item {
    padding: 12px 16px;
    border-bottom: 1px solid #eee;
    transition: background-color 0.2s;
}

.notification-item:hover {
    background: #fafafa;
}

.notification-item.notification-performance_alert {
    border-left: 4px solid #ff9800;
}

.notification-item.notification-error_alert {
    border-left: 4px solid #f44336;
}

.notification-item.notification-keyword_alert {
    border-left: 4px solid #2196f3;
}

.notification-header {
    display: flex;
    align-items: center;
    gap: 8px;
    font-size: 13px;
    margin-bottom: 8px;
}

.notification-header .icon {
    font-size: 16px;
}

.notification-header .type {
    font-weight: 600;
    color: #333;
}

.notification-header .time {
    margin-left: auto;
    color: #999;
    font-size: 12px;
}

.notification-body .message {
    margin: 0;
    color: #666;
    font-size: 14px;
    line-height: 1.4;
}

.notification-body .details {
    margin: 8px 0 0 0;
    padding: 8px;
    background: #f5f5f5;
    border-radius: 4px;
    font-size: 12px;
    max-height: 150px;
    overflow: auto;
}

/* 에러 배너 */
.error-banner {
    padding: 12px 16px;
    background: #ffebee;
    color: #c62828;
    border-bottom: 1px solid #f44336;
    display: flex;
    justify-content: space-between;
    align-items: center;
    font-size: 13px;
}

.error-banner button {
    padding: 4px 8px;
    background: #f44336;
    color: white;
    border: none;
    border-radius: 4px;
    cursor: pointer;
    font-size: 12px;
}
```

---

## 통합 흐름

### 📊 전체 데이터 흐름 다이어그램

```
┌─────────────────────────────────────────────────────────────┐
│ [클라이언트] React Frontend                                   │
├─────────────────────────────────────────────────────────────┤
│                                                               │
│  NotificationCenter.tsx                                       │
│    └→ useSSE() 훅                                             │
│       ├→ new EventSource('/api/notifications/stream')        │
│       ├→ addEventListener('notification', handler)          │
│       └→ 상태 업데이트 (notifications, status)                │
│                                                               │
│  NotificationItem.tsx                                         │
│    └→ 각 알림 렌더링                                           │
│                                                               │
└─────────────────────────────────────────────────────────────┘
                            ↑
                   HTTP/1.1 200 OK
                text/event-stream
                            │
┌─────────────────────────────────────────────────────────────┐
│ [서버] Spring Boot Backend                                   │
├─────────────────────────────────────────────────────────────┤
│                                                               │
│ NotificationController                                        │
│   GET /api/notifications/stream                              │
│     └→ notificationManager.subscribe(user, lastEventId)      │
│        ├→ new SseEmitter(60000)                              │
│        ├→ userEmitters.put(userId, emitter)                 │
│        └→ return emitter                                     │
│                                                               │
│ [비동기] 알림 발행                                             │
│   QueryController                                            │
│     POST /api/query                                          │
│       └→ eventPublisher.publish(QueryExecutionEvent)         │
│          ├→ RuleEngine.evaluateRules(event)                 │
│          │  ├→ PerformanceRule.evaluate()                   │
│          │  ├→ ErrorRule.evaluate()                         │
│          │  └→ KeywordRule.evaluate()                       │
│          │                                                   │
│          └→ NotificationService.notify(notification)        │
│             ├→ EmailNotifier.send()                         │
│             ├→ ConsoleNotifier.send()                       │
│             └→ SseNotifier.send()                           │
│                  └→ notificationManager.notify(userId, notification)
│                     └→ emitter.send(event + data)           │
│                                                               │
└─────────────────────────────────────────────────────────────┘
```

### 🔄 실행 순서 (타이밍)

```
t0: 사용자 페이지 접속
    → NotificationCenter 마운트
    → useSSE() 훅 실행
    → new EventSource() 생성
    → GET /api/notifications/stream

t1: 서버가 stream 응답
    → SseEmitter 생성
    → 클라이언트에 200 OK + text/event-stream

t2: 클라이언트 SSE 연결 확립
    → eventSource.onopen 실행
    → connectionStatus = 'connected'

t3: 사용자가 쿼리 입력 및 실행
    → POST /api/query
    → NQL 파싱 → ES 검색

t4: 쿼리 완료, 이벤트 발행
    → eventPublisher.publish(QueryExecutionEvent)

t5: 규칙 평가 (RuleEngine)
    → 조건 매칭 확인

t6: Notification 생성
    → id, type, message 생성

t7: 채널 분기 (병렬)
    → EmailNotifier.send()
    → ConsoleNotifier.send()
    → SseNotifier.send()

t8: SSE 전송
    → notificationManager.notify(userId, notification)
    → emitter.send(event)

t9: 클라이언트 수신
    → eventSource.onmessage / addEventListener('notification')
    → JSON.parse(event.data)
    → setNotifications() 상태 업데이트

t10: UI 렌더링
    → NotificationItem 컴포넌트 추가
    → 사용자가 알림 확인
```

---

## 트러블슈팅

### 문제 1: 연결이 자동으로 종료됨

#### 증상
```
SSE 연결 후 5-10초 뒤 자동 종료
→ 클라이언트: readyState === EventSource.CLOSED (2)
```

#### 원인
- Nginx/Apache 프록시의 기본 타임아웃 (보통 30-60초)
- 데이터 전송이 없으면 프록시가 유휴 연결 자르기

#### 해결
**1단계: 하트비트 추가**
```java
@Scheduled(fixedRate = 30000)  // 30초마다
public void sendHeartbeat() {
    emitter.send(SseEmitter.event()
        .comment("heartbeat")
        .build());
}
```

**2단계: 프록시 설정 (Nginx)**
```nginx
location /api/notifications/stream {
    proxy_pass http://backend;
    proxy_http_version 1.1;
    proxy_set_header Connection "";
    
    # 타임아웃 증가
    proxy_read_timeout 300s;
    proxy_send_timeout 300s;
    
    # 버퍼링 비활성화 (SSE 즉시 전송)
    proxy_buffering off;
    proxy_cache off;
}
```

### 문제 2: 메시지 손실 (Last-Event-ID)

#### 증상
```
클라이언트 재연결 후 놓친 메시지 미수신
```

#### 원인
- Last-Event-ID 헤더를 서버에서 처리하지 않음
- 이벤트 ID가 증분되지 않음

#### 해결
```java
@GetMapping("/api/notifications/stream")
public SseEmitter subscribe(
    @RequestHeader(value = "Last-Event-ID", required = false) String lastEventId) {
    
    if (lastEventId != null) {
        // lastEventId 이후의 메시지 재전송
        List<Notification> missed = 
            notificationService.getNotificationsAfter(userId, lastEventId);
        
        for (Notification n : missed) {
            emitter.send(SseEmitter.event()
                .id(n.getId())
                .data(n)
                .build());
        }
    }
    
    return emitter;
}
```

### 문제 3: 메모리 누수 (Dead Emitters)

#### 증상
```
시간 경과에 따라 메모리 증가
→ 종료된 emitter가 계속 메모리 점유
```

#### 원인
- IOException 발생한 emitter를 제거하지 않음
- 타임아웃 emitter를 정리하지 않음

#### 해결
```java
public void notify(String userId, Notification notification) {
    Set<SseEmitter> emitters = userEmitters.get(userId);
    List<SseEmitter> deadEmitters = new ArrayList<>();
    
    for (SseEmitter emitter : emitters) {
        try {
            emitter.send(...);
        } catch (IOException e) {
            // ❌ emitter를 그냥 놔두지 말고
            // ✅ 나중에 제거할 목록에 추가
            deadEmitters.add(emitter);
        }
    }
    
    // ✅ 배치로 제거 (ConcurrentModificationException 방지)
    deadEmitters.forEach(emitters::remove);
}
```

### 문제 4: CORS 에러

#### 증상
```
브라우저 콘솔:
Access to XMLHttpRequest at 'http://backend:8080/api/notifications/stream'
from origin 'http://localhost:3000' has been blocked by CORS policy
```

#### 원인
- SSE도 CORS 적용 필요
- `withCredentials: true` 사용 시 추가 헤더 필요

#### 해결
```java
@Configuration
public class WebConfig implements WebMvcConfigurer {
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/notifications/stream")
            .allowedOrigins("http://localhost:3000")
            .allowedMethods("GET")
            .allowedHeaders("*")
            .allowCredentials(true)  // ✅ 필수
            .maxAge(3600);
    }
}
```

### 문제 5: 텍스트 에인코딩 이슈

#### 증상
```
클라이언트에서 한글이 깨짐
event.data: "???"
```

#### 원인
- UTF-8 인코딩이 명시되지 않음
- Jackson 기본 인코딩 문제

#### 해결
```java
@GetMapping("/api/notifications/stream")
public SseEmitter subscribe() {
    SseEmitter emitter = new SseEmitter(60000L);
    
    // 응답 헤더에 charset 명시
    // 자동 처리되지만, 명시적으로 추가 가능
    
    return emitter;
}

// 또는 서블릿 설정
@Configuration
public class WebConfig {
    @Bean
    public CharacterEncodingFilter characterEncodingFilter() {
        CharacterEncodingFilter filter = new CharacterEncodingFilter();
        filter.setEncoding("UTF-8");
        filter.setForceEncoding(true);
        return filter;
    }
}
```

---

## 📌 체크리스트

### 구현 완료 체크
- [ ] NotificationManager 서비스 구현
- [ ] SseNotifier 채널 구현
- [ ] NotificationController SSE 엔드포인트
- [ ] React useSSE 훅
- [ ] NotificationCenter & NotificationItem 컴포넌트
- [ ] CSS 스타일링
- [ ] CORS 설정
- [ ] 하트비트 스케줄러 (선택)

### 테스트 체크
- [ ] SSE 연결 성공 확인
- [ ] 알림 수신 확인
- [ ] 자동 재연결 확인
- [ ] Last-Event-ID 재연결 복구
- [ ] 메모리 누수 테스트
- [ ] 동시 사용자 100+ 부하 테스트

### 배포 체크
- [ ] Nginx 프록시 설정
- [ ] 타임아웃 값 검증
- [ ] CORS 프로덕션 설정
- [ ] 모니터링 (메모리, 연결 수)

---

## 📚 다음 단계

이제 Phase 6를 실제로 구현하겠습니다:
1. **NotificationManager** 구현
2. **SseNotifier** 통합
3. **클라이언트** React 컴포넌트
4. **테스트** 및 검증
5. **학습 문서** 정리
