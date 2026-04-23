# Phase 6 학습 2: SSE (Server-Sent Events) 기초부터 심화까지

## 📚 목차
1. [SSE 개념](#sse-개념)
2. [EventSource API](#eventsource-api)
3. [메시지 형식](#메시지-형식)
4. [자동 재연결 메커니즘](#자동-재연결-메커니즘)
5. [에러 처리 전략](#에러-처리-전략)
6. [성능 최적화](#성능-최적화)
7. [고급 패턴](#고급-패턴)

---

## SSE 개념

### 🎯 SSE란 무엇인가?

**SSE (Server-Sent Events)**: HTTP를 기반으로 한 **단방향 푸시 기술**

```
기존 HTTP: Request → Response → 연결 종료
SSE:       Request → Response (계속 열림) → 다양한 이벤트 스트리밍
```

### 📊 SSE vs WebSocket 동작 원리

#### HTTP/1.1 Keep-Alive와의 차이
```
Keep-Alive:
클라이언트: GET /data
서버: 200 OK + data
(연결 유지)
클라이언트: GET /data2
서버: 200 OK + data2
(연결 종료)

SSE:
클라이언트: GET /stream
서버: 200 OK (Content-Type: text/event-stream)
     (연결 유지된 상태에서 지속적으로 데이터 전송)
     event: message1
     data: {...}
     event: message2
     data: {...}
```

### 💡 왜 SSE인가?

1. **HTTP 기반**: 표준 웹 프로토콜
2. **자동 재연결**: 브라우저 자동 처리
3. **간단한 API**: `new EventSource()` 한 줄
4. **메모리 효율**: 연결당 ~4KB
5. **스케일링**: 10,000+ 동시 사용자 가능

---

## EventSource API

### 기초: EventSource 객체 생성

#### 가장 간단한 형태
```javascript
// 1. EventSource 생성 (자동 연결)
const eventSource = new EventSource('/api/notifications/stream');

// 2. 메시지 수신
eventSource.onmessage = (event) => {
    console.log('메시지 받음:', event.data);
};

// 3. 에러 처리
eventSource.onerror = (error) => {
    console.error('SSE 에러:', error);
};
```

### 중급: 이벤트 타입 구분

```javascript
// 특정 이벤트 타입 리스닝
eventSource.addEventListener('notification', (event) => {
    const notification = JSON.parse(event.data);
    console.log('알림:', notification.message);
});

eventSource.addEventListener('heartbeat', (event) => {
    console.log('서버 응답 확인:', event.data);
});

// 모든 이벤트 캐치
eventSource.onmessage = (event) => {
    console.log('기본 이벤트:', event.data);
};
```

### 고급: EventSource 객체 상세

#### EventSource의 속성 (Property)

```javascript
const eventSource = new EventSource('/stream');

// 1. readyState: 연결 상태
console.log(eventSource.readyState);
// 0 = CONNECTING (연결 중)
// 1 = OPEN (연결됨)
// 2 = CLOSED (종료됨)

// 2. url: 연결한 URL
console.log(eventSource.url); // '/stream'

// 3. withCredentials: 쿠키/인증 포함 여부
const es = new EventSource('/stream', {withCredentials: true});
console.log(es.withCredentials); // true
```

#### EventSource의 메서드

```javascript
// 1. addEventListener(type, listener)
eventSource.addEventListener('notification', handler);

// 2. removeEventListener(type, listener)
eventSource.removeEventListener('notification', handler);

// 3. close(): 연결 종료 (자동 재연결 중지)
eventSource.close();
```

### 심화: 생성자 옵션

```javascript
// 옵션 있이 생성
const eventSource = new EventSource('/api/stream', {
    withCredentials: true  // 쿠키/인증 정보 포함 (CORS)
});

// withCredentials 상세
// false (기본): 쿠키 미포함
//   - 요청: Cookie 헤더 없음
//   - 응답: Set-Cookie 무시
//
// true: 쿠키 포함
//   - 요청: Cookie 헤더 포함 (CORS 시 필수)
//   - 응답: Set-Cookie 처리
//   - 서버는 Access-Control-Allow-Credentials: true 필요
```

### 이벤트 객체 (Event)

```javascript
eventSource.addEventListener('notification', (event) => {
    console.log(event.type);         // 'notification'
    console.log(event.data);         // 데이터 문자열 (JSON 포함)
    console.log(event.lastEventId);  // 마지막 이벤트 ID
    console.log(event.origin);       // 출처 (보안)
    console.log(event.ports);        // SharedWorker 포트
    console.log(event.source);       // EventSource 객체 자신
});
```

---

## 메시지 형식

### 기초: 메시지 구조

SSE는 **간단한 텍스트 기반 프로토콜**입니다:

```
[field]: [value]\n
[field]: [value]\n
\n (빈 줄 = 메시지 완성)
```

### 필드 상세

#### 1. `event` - 이벤트 타입
```
// 서버 (Java)
emitter.send(SseEmitter.event()
    .name("notification")
    .build());

// 클라이언트 (JavaScript)
eventSource.addEventListener('notification', (event) => {
    console.log(event.data);
});

// 프로토콜
event: notification
data: ...
```

**규칙**:
- 기본값: `message` (onmessage 핸들러)
- 커스텀 이름: `notification`, `alert`, `update` 등
- 같은 이벤트 타입은 같은 리스너에서 받음

#### 2. `data` - 실제 데이터
```
// 단일 줄
data: hello world

// 여러 줄 (각 줄마다 data: 필수)
data: line 1
data: line 2
data: line 3
// 결과: "line 1\nline 2\nline 3"

// JSON
data: {"type": "ALERT", "message": "Performance degraded"}

// 빈 data 허용
data:
```

**중요**: 여러 줄은 자동으로 `\n`으로 합쳐집니다:
```javascript
// 서버
emitter.send(SseEmitter.event()
    .data("line 1")
    .data("line 2")
    .build());

// 클라이언트에서
event.data === "line 1\nline 2"
```

#### 3. `id` - 이벤트 고유 ID
```
// 서버 (Java)
emitter.send(SseEmitter.event()
    .id("msg-1234")
    .data("...")
    .build());

// 프로토콜
id: msg-1234
data: {"type": "ALERT"}
```

**용도**:
```javascript
// 연결 끊김 후 재연결
// Last-Event-ID 헤더: "msg-1234"
// → 서버에서 "msg-1235"부터 전송 (놓친 메시지 복구)

eventSource.addEventListener('notification', (event) => {
    console.log('마지막 이벤트 ID:', event.lastEventId);
    // "msg-1234" → 서버는 이를 통해 놓친 메시지 처리
});
```

#### 4. `retry` - 자동 재연결 간격
```
// 서버
emitter.send(SseEmitter.event()
    .reconnectTime(3000)  // 3초
    .data("...")
    .build());

// 프로토콜
retry: 3000
data: ...
```

**동작**:
```
연결 끊김 발생:
→ 브라우저 자동 대기 (3000ms)
→ 자동으로 GET /stream?Last-Event-ID=msg-1234
→ 재연결 성공
```

**규칙**:
- 최솟값: 0ms (즉시 재연결)
- 최댓값: 브라우저 제한 없음 (권장 3000-5000ms)
- 기본값: 1000ms (retry 없으면)

#### 5. 주석 `:` - 서버 상태 확인
```
// 서버 (하트비트)
:server is alive\n

// 프로토콜
: 2024-04-23 10:30:15 - Server healthy
: Next event in 5 seconds...
```

**용도**:
```
- 연결 유지 신호 (프록시가 연결 자르는 것 방지)
- 서버 상태 모니터링
- 클라이언트의 타임아웃 방지
```

### 실제 메시지 흐름

```
// 1. 초기 연결
GET /api/notifications/stream HTTP/1.1
Host: localhost:8080

// 2. 서버 응답 (계속 유지)
HTTP/1.1 200 OK
Content-Type: text/event-stream
Cache-Control: no-cache
Connection: keep-alive

// 3. 첫 번째 메시지 (하트비트)
: server heartbeat
\n

// 4. 두 번째 메시지 (실제 알림)
event: notification
id: 1001
data: {"type": "PERFORMANCE_ALERT", "value": 105.3}
\n

// 5. 세 번째 메시지 (재연결 간격 변경)
retry: 5000
event: notification
id: 1002
data: {"type": "ERROR_ALERT", "message": "Connection lost"}
\n
```

---

## 자동 재연결 메커니즘

### 기초: 기본 재연결

```javascript
const eventSource = new EventSource('/stream');

eventSource.onerror = (error) => {
    if (eventSource.readyState === EventSource.CLOSED) {
        console.log('연결 종료됨 (수동)');
    } else if (eventSource.readyState === EventSource.CONNECTING) {
        console.log('재연결 중... (자동)');
        // 브라우저가 자동으로 대기하고 재연결
    }
};
```

### 중급: 재연결 프로세스

```
클라이언트가 연결 또는 오류 감지:
  ↓
1. 재연결 간격 대기 (retry 필드 또는 기본 1초)
  ↓
2. Last-Event-ID 헤더 포함하여 재연결 시도
  ↓
3. 200 OK 수신 → 연결 재개
   404 또는 에러 → 재시도 (기본 3회)
```

#### 클라이언트 관점
```javascript
const eventSource = new EventSource('/stream');

eventSource.onopen = (event) => {
    console.log('SSE 연결 시작, readyState:', eventSource.readyState);
    // readyState = 1 (OPEN)
};

eventSource.onerror = (event) => {
    if (eventSource.readyState === EventSource.CONNECTING) {
        console.log('자동 재연결 중...');
    } else {
        console.log('연결 영구 종료');
        eventSource.close();
    }
};
```

### 심화: Last-Event-ID 재연결

```
시나리오: 네트워크 끊김, 메시지 손실

서버 메시지 시퀀스:
event: notification
id: msg-101
data: {"seq": 1}

event: notification
id: msg-102
data: {"seq": 2}  ← 클라이언트 수신 전 네트워크 끊김

event: notification
id: msg-103
data: {"seq": 3}

클라이언트 재연결 요청:
GET /stream HTTP/1.1
Last-Event-ID: msg-101
```

#### 서버에서 처리 (Java)
```java
@GetMapping("/api/notifications/stream")
public SseEmitter subscribe(
    @RequestParam String userId,
    @RequestHeader(value = "Last-Event-ID", required = false) String lastEventId) {
    
    SseEmitter emitter = new SseEmitter(60000L);
    
    // 마지막 이벤트 이후 미전송 메시지 재전송
    if (lastEventId != null) {
        List<Notification> missedNotifications = 
            notificationService.getNotificationsAfter(userId, lastEventId);
        
        for (Notification notif : missedNotifications) {
            try {
                emitter.send(SseEmitter.event()
                    .id(notif.getId())
                    .name("notification")
                    .data(notif)
                    .build());
            } catch (IOException e) {
                emitter.completeWithError(e);
            }
        }
    }
    
    // 이후 새 메시지
    eventPublisher.subscribe(userId, emitter);
    return emitter;
}
```

---

## 에러 처리 전략

### 기초: onerror 핸들러

```javascript
const eventSource = new EventSource('/stream');

eventSource.onerror = (error) => {
    console.error('SSE 에러:', error);
    console.log('readyState:', eventSource.readyState);
    // 0: CONNECTING (재연결 중)
    // 1: OPEN (연결됨)
    // 2: CLOSED (종료됨)
};
```

### 중급: 에러 분류 및 처리

```javascript
eventSource.onerror = (error) => {
    const state = eventSource.readyState;
    
    if (state === EventSource.CONNECTING) {
        // 1. 일시적 네트워크 에러
        console.warn('임시 연결 문제, 자동 재연결 진행 중...');
        // 브라우저가 자동 처리
        
    } else if (state === EventSource.CLOSED) {
        // 2. 영구적 에러 (서버 400/401/403 등)
        console.error('서버가 연결 거부 (401 등), 재로그인 필요');
        eventSource.close();
        // 사용자에게 재로그인 요청
        redirectToLogin();
        
    } else {
        // 3. 예상 밖의 상태
        console.error('알 수 없는 에러');
        eventSource.close();
    }
};
```

### 심화: 재연결 전략

#### 전략 1: 무한 재연결 + 사용자 알림
```javascript
let reconnectAttempts = 0;
const MAX_RECONNECT_ATTEMPTS = 10;

function connectSSE() {
    const eventSource = new EventSource('/stream');
    
    eventSource.onerror = (error) => {
        if (eventSource.readyState === EventSource.CONNECTING) {
            reconnectAttempts++;
            
            if (reconnectAttempts > MAX_RECONNECT_ATTEMPTS) {
                console.error('재연결 포기');
                eventSource.close();
                showNotification('서버 연결 실패, 페이지 새로고침 필요');
            } else {
                const waitTime = Math.min(1000 * Math.pow(2, reconnectAttempts), 30000);
                console.log(`${waitTime}ms 후 재연결 시도...`);
                setTimeout(() => connectSSE(), waitTime);
            }
        }
    };
    
    eventSource.addEventListener('notification', (event) => {
        reconnectAttempts = 0; // 성공 시 카운터 리셋
    });
}
```

#### 전략 2: 자동 폴백 (SSE → Polling)
```javascript
let useSSE = true;

function initializeNotifications() {
    if (useSSE && EventSource) {
        connectSSE();
    } else {
        connectPolling();
    }
}

function connectSSE() {
    const eventSource = new EventSource('/stream');
    
    eventSource.onerror = (error) => {
        if (eventSource.readyState === EventSource.CLOSED) {
            console.warn('SSE 실패, Polling으로 폴백');
            useSSE = false;
            eventSource.close();
            connectPolling();
        }
    };
}

function connectPolling() {
    setInterval(async () => {
        const response = await fetch('/api/notifications');
        const notifications = await response.json();
        notifications.forEach(n => showNotification(n));
    }, 5000);
}
```

---

## 성능 최적화

### 기초: 메시지 크기 최적화

#### 데이터 포맷 선택

```javascript
// ❌ 나쁜 예: 과도한 데이터
data: {
  "notification_id": "abc123",
  "notification_type": "PERFORMANCE_ALERT",
  "severity_level": "WARNING",
  "user_id": "anonymous",
  "timestamp": "2024-04-23T10:30:15.123Z",
  "message": "Query response time exceeded threshold",
  "details": {...}
}
// 크기: ~400 bytes

// ✅ 좋은 예: 최적화된 데이터
data: {"id":"abc123","t":1,"v":105.3,"m":"Query slow"}
// 크기: ~60 bytes (-85%)
```

#### 필드 축약 (약어 사용)
```javascript
// 서버 (Java)
emitter.send(SseEmitter.event()
    .data(new CompactNotification(
        id,           // "id" → "id"
        type,         // "type" → "t" (1=PERF, 2=ERROR, 3=KEYWORD)
        value,        // "value" → "v"
        message       // "message" → "m"
    ))
    .build());

// 클라이언트 (JavaScript)
eventSource.addEventListener('notification', (event) => {
    const n = JSON.parse(event.data);
    switch(n.t) {
        case 1: showPerformanceAlert(n); break;
        case 2: showErrorAlert(n); break;
        case 3: showKeywordAlert(n); break;
    }
});
```

### 중급: 이벤트 배치 처리

```java
// ❌ 나쁜 패턴: 개별 send
for (Notification notif : notifications) {
    emitter.send(SseEmitter.event()
        .data(notif)
        .build());  // 5개 알림 = 5번 send() → 5개 메시지
}

// ✅ 좋은 패턴: 배치 처리
List<Notification> batch = notifications.stream()
    .limit(10)  // 10개씩 묶음
    .collect(Collectors.toList());

emitter.send(SseEmitter.event()
    .data(batch)
    .build());  // 50개 알림 = 5번 send() → 5개 메시지 (1/10 감소)
```

### 심화: 연결 타임아웃 및 하트비트

#### 타임아웃 전략
```java
@GetMapping("/api/notifications/stream")
public SseEmitter subscribe(@RequestParam String userId) {
    // 타임아웃: 60초 (프록시가 연결 자르기 전에 재연결)
    SseEmitter emitter = new SseEmitter(60000L);
    
    // 타임아웃 콜백
    emitter.onTimeout(() -> {
        logger.info("SSE 타임아웃, 클라이언트가 재연결할 것");
        // 정상 동작: 클라이언트는 자동 재연결
    });
    
    // 완료 콜백
    emitter.onCompletion(() -> {
        logger.info("SSE 연결 완료/종료");
        eventPublisher.unsubscribe(userId, emitter);
    });
    
    // 에러 콜백
    emitter.onError(throwable -> {
        logger.error("SSE 에러", throwable);
        eventPublisher.unsubscribe(userId, emitter);
    });
    
    // 하트비트: 30초마다 (프록시 연결 유지)
    scheduleHeartbeat(emitter, userId);
    
    return emitter;
}

private void scheduleHeartbeat(SseEmitter emitter, String userId) {
    executorService.scheduleAtFixedRate(() -> {
        try {
            // 주석은 대역폭 적고 프록시는 인식 (연결 유지)
            emitter.send(SseEmitter.event()
                .comment("heartbeat")
                .build());
        } catch (IOException e) {
            logger.debug("Heartbeat 실패, 연결 종료됨");
        }
    }, 30, 30, TimeUnit.SECONDS);
}
```

#### 클라이언트 타임아웃 처리
```javascript
let lastMessageTime = Date.now();
const TIMEOUT_MS = 90000; // 90초

eventSource.addEventListener('notification', (event) => {
    lastMessageTime = Date.now();
});

// 주기적 타임아웃 체크
setInterval(() => {
    if (Date.now() - lastMessageTime > TIMEOUT_MS) {
        console.warn('SSE 타임아웃, 수동 재연결');
        eventSource.close();
        setTimeout(() => location.reload(), 1000);
    }
}, 30000);
```

---

## 고급 패턴

### 패턴 1: 사용자별 이벤트 필터링

```java
// 서버: 사용자별 구독 관리
@Service
public class NotificationManager {
    private Map<String, Set<SseEmitter>> userEmitters = 
        new ConcurrentHashMap<>();
    
    public void subscribe(String userId, SseEmitter emitter) {
        userEmitters.computeIfAbsent(userId, k -> ConcurrentHashMap.newKeySet())
            .add(emitter);
    }
    
    public void notify(String userId, Notification notification) {
        Set<SseEmitter> emitters = userEmitters.get(userId);
        if (emitters != null) {
            for (SseEmitter emitter : emitters) {
                try {
                    emitter.send(SseEmitter.event()
                        .name("notification")
                        .data(notification)
                        .build());
                } catch (IOException e) {
                    emitters.remove(emitter);
                }
            }
        }
    }
}
```

### 패턴 2: 브로드캐스트 (모든 사용자)

```java
public void broadcastNotification(Notification notification) {
    for (Set<SseEmitter> emitters : userEmitters.values()) {
        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event()
                    .name("broadcast")
                    .data(notification)
                    .build());
            } catch (IOException e) {
                emitters.remove(emitter);
            }
        }
    }
}
```

### 패턴 3: 이벤트 우선순위

```javascript
// 클라이언트: 우선순위에 따른 표시
eventSource.addEventListener('notification', (event) => {
    const n = JSON.parse(event.data);
    
    if (n.priority === 'CRITICAL') {
        showErrorNotification(n);  // 빨간색, 소리 경고
    } else if (n.priority === 'HIGH') {
        showWarningNotification(n); // 주황색
    } else {
        showInfoNotification(n);    // 파란색
    }
});
```

---

## 📌 정리

### SSE의 핵심
1. **단방향**: 서버 → 클라이언트 푸시
2. **HTTP 기반**: 프록시, 방화벽 친화
3. **자동 재연결**: 브라우저 자동 처리
4. **간단한 API**: EventSource + 이벤트 리스너
5. **효율적**: 메모리 4KB/연결, 대역폭 최소

### 다음 문서 미리보기
- **PHASE_6_LEARNING_03**: 프로젝트 적용
  - Spring SseEmitter 실전 구현
  - NotificationService 통합
  - 실제 코드 예제
  - 테스트 전략
