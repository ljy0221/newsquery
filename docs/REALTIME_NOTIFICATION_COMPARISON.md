# 실시간 알림 구현: WebSocket vs SSE vs Polling 비교

## 📊 3가지 방식 비교표

| 항목 | WebSocket | SSE (Server-Sent Events) | Polling |
|------|-----------|--------------------------|---------|
| **통신 방식** | 양방향 (full-duplex) | 단방향 서버→클라이언트 | 클라이언트가 주기적 요청 |
| **연결 방식** | TCP 영구 연결 | HTTP 스트림 (영구 연결) | HTTP 단순 요청-응답 |
| **지연시간** | ⭐⭐⭐ 극저지연 (ms) | ⭐⭐⭐ 극저지연 (ms) | ⭐ 높은 지연 (초 단위) |
| **대역폭** | 적음 (헤더 오버헤드 최소) | 적음 (HTTP 스트림) | ⭐ 많음 (반복 요청) |
| **서버 부하** | ⭐ 높음 (연결 유지) | ⭐⭐ 중간 | ⭐⭐⭐ 낮음 |
| **클라이언트 복잡도** | ⭐⭐ 보통 (라이브러리 필요) | ⭐⭐⭐ 낮음 (EventSource) | ⭐⭐⭐ 낮음 |
| **브라우저 지원** | 모던 브라우저 | 모던 브라우저 | 모든 브라우저 |
| **프록시/로드밸런서** | ⭐ 주의 필요 | ⭐ 주의 필요 | ✅ 안정적 |
| **자동 재연결** | 수동 구현 필요 | 자동 재연결 지원 | 자동 (시간 초과 처리) |
| **용도 예시** | 채팅, 협업 도구, 게임 | 알림, 실시간 뉴스 피드 | 상태 확인, 낮은 우선순위 |

---

## 🎯 각 방식 상세 분석

### 1️⃣ **WebSocket** - 양방향 실시간 통신

#### 장점
- **극저지연**: 양방향 영구 연결로 ms 단위 지연
- **효율성**: 한 번 연결 후 오버헤드 최소
- **실시간 양방향**: 서버→클라이언트, 클라이언트→서버 동시 가능

#### 단점
- **서버 부하**: 모든 클라이언트와 연결 유지 필요
  - 10,000 사용자 = 10,000개 TCP 연결 유지 (메모리, CPU)
  - **프로토콜 전환 오버헤드**: HTTP → WebSocket (핸드셰이크)
- **프록시 문제**: 일부 기업 방화벽/프록시가 WebSocket 차단
- **상태 관리 복잡**: 연결 끊김, 재연결, 동기화 등 처리 필요
- **클라이언트 로직**: 자동 재연결 구현 필수

#### 최적 사용 사례
```
📱 채팅 애플리케이션
🎮 멀티플레이 게임
👥 협업 도구 (Google Docs 같은 실시간 편집)
📊 실시간 데이터 대시보드 (트레이딩, 모니터링)
```

#### 구현 예시 (Spring)
```java
// server
@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {
    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(new NotificationHandler(), "/ws/notifications")
                .setAllowedOrigins("http://localhost:3000");
    }
}

// client (React)
const ws = new WebSocket('ws://localhost:8080/ws/notifications');
ws.onmessage = (event) => {
    const notification = JSON.parse(event.data);
    showNotification(notification);
};
```

---

### 2️⃣ **SSE** (Server-Sent Events) - 단방향 스트림

#### 장점
- **브라우저 표준**: EventSource API (HTML5 표준)
- **자동 재연결**: 연결 끊기면 자동으로 재연결 시도 (5초 기본)
- **간단한 구현**: 클라이언트 코드 매우 간단
- **HTTP 기반**: 프록시, 로드밸런서와 호환성 좋음
- **서버 부하 중간**: WebSocket보다 낮음
- **자동 재시도**: `retry: 3000` 헤더로 3초마다 자동 재연결

#### 단점
- **단방향만 가능**: 서버→클라이언트만 (클라이언트→서버는 AJAX 별도)
- **IE 미지원**: 구형 브라우저 지원 안 함
- **HTTP/1.1 제한**: 브라우저당 최대 6개 연결 (도메인당)
  - 만약 같은 도메인의 다른 탭에서도 SSE 쓰면 6개 초과 시 대기

#### 최적 사용 사례
```
🔔 푸시 알림 (우리 프로젝트!)
📰 실시간 뉴스 피드
📊 실시간 모니터링 대시보드
💬 1:다 방송 메시지
📈 라이브 스트리밍 (비디오 아님, 데이터)
```

#### 구현 예시 (Spring Boot)
```java
// server
@GetMapping("/api/notifications/stream")
public SseEmitter subscribeToNotifications(@RequestParam String userId) {
    SseEmitter emitter = new SseEmitter(60000L); // 60초 타임아웃
    
    // 이벤트 발행 시
    eventPublisher.addListener(notification -> {
        try {
            emitter.send(SseEmitter.event()
                .id(notification.getId())
                .name("notification")
                .data(notification)
                .reconnectTime(3000)
                .build());
        } catch (IOException e) {
            emitter.completeWithError(e);
        }
    });
    
    return emitter;
}

// client (React)
useEffect(() => {
    const eventSource = new EventSource('/api/notifications/stream?userId=anonymous');
    
    eventSource.addEventListener('notification', (event) => {
        const notification = JSON.parse(event.data);
        showNotification(notification);
    });
    
    eventSource.onerror = () => {
        console.log('SSE 연결 끊김, 자동 재연결 시도...');
    };
    
    return () => eventSource.close();
}, []);
```

---

### 3️⃣ **Polling** - 주기적 요청

#### 장점
- **구현 극히 간단**: 기존 REST API 그대로 사용
- **모든 브라우저 지원**: IE 포함
- **프록시/방화벽 친화적**: 일반 HTTP GET
- **서버 리소스 예측 가능**: 주기 명확
- **디버깅 쉬움**: 일반 AJAX 요청

#### 단점
- **지연시간 높음**: Polling 간격에 따라 5~30초 (알림 감지 지연)
- **대역폭 낭비**: 알림 없어도 계속 요청
- **서버 부하**: 주기적 요청 * 사용자 수 = 높은 QPS
  - 100명 사용자, 5초 간격 = 20 req/s 지속
- **배터리 소모**: 모바일에서 배터리 빠름
- **확장성 낮음**: 사용자 증가하면 서버 부하 선형 증가

#### 최적 사용 사례
```
⏱️ 상태 확인 (5~10초 간격)
📊 실시간성 낮은 대시보드
🌐 구형 브라우저 지원 필수
🔌 불안정한 네트워크 환경
```

#### 구현 예시 (Spring Boot)
```java
// server
@GetMapping("/api/notifications")
public ResponseEntity<List<Notification>> getNotifications(
    @RequestParam String userId,
    @RequestParam(defaultValue = "0") long since) {
    List<Notification> notifications = service.getNotificationsSince(userId, since);
    return ResponseEntity.ok(notifications);
}

// client (React)
useEffect(() => {
    const interval = setInterval(async () => {
        const response = await fetch(`/api/notifications?userId=anonymous&since=${lastFetchTime}`);
        const notifications = await response.json();
        notifications.forEach(n => showNotification(n));
        setLastFetchTime(Date.now());
    }, 5000); // 5초마다 폴링
    
    return () => clearInterval(interval);
}, []);
```

---

## 🏆 N-QL Intelligence 프로젝트에 최적의 선택은?

### 📌 권장: **SSE** (Server-Sent Events)

#### 이유
1. **알림 특성상 단방향**: 
   - 서버 → 클라이언트: 규칙 평가 결과 (성능 저하, 오류 등)
   - 클라이언트 → 서버: 필요 시 AJAX로 구독 변경 (드물음)

2. **비용 효율**:
   - WebSocket보다 서버 부하 ⬇️ (연결당 메모리 ~1KB vs 수KB)
   - Polling보다 대역폭 ⬇️ (이벤트 발생 시에만 전송)

3. **구현 간단**:
   ```
   SSE: 30줄 코드
   WebSocket: 100줄 코드 (재연결, 오류 처리 포함)
   Polling: 50줄 코드 (하지만 비효율)
   ```

4. **확장성**:
   - 현재 10~100명 사용자 → SSE로 충분
   - 나중에 10,000명 → 그 때 Kafka + WebSocket으로 업그레이드
   
5. **호환성**:
   - 대부분 모던 브라우저 지원
   - 필요시 polling fallback 가능 (polyfill)

#### 아키텍처
```
QueryController 
  → QueryExecutionEvent 발행
    → EventPublisher (관찰자 패턴)
      → RuleEngine (규칙 평가)
        → NotificationService
          → [EmailNotifier] (이메일)
          → [ConsoleNotifier] (콘솔)
          → [SseNotifier] ✨ (SSE 클라이언트)
             ↓
          WebClient.post("/api/notify-sse")
            ↓
          SseEmitter.send(event)
```

---

## 💡 구현 전략

### Phase 6.1: SSE + Email (권장)
```
✅ Email: NotificationService → EmailNotifier (SMTP)
✅ SSE: SseEmitter API → 브라우저 EventSource
✅ 시간 소요: 3~4시간
✅ 학습 가치: HTTP 스트림, Spring SseEmitter, 자동 재연결
```

### Phase 6.2: WebSocket (선택)
```
⚠️ 시간 소요: 6~8시간 (복잡도 높음)
⚠️ 배우기 좋지만, 현 프로젝트에선 필수 아님
⚠️ 필요시 나중에 추가 가능
```

### Polling (비추천)
```
❌ 대역폭 낭비
❌ 지연시간 높음
❌ 배터리 소모
❌ 확장성 낮음
```

---

## 🎓 각 방식 학습 난이도

| 기술 | 난이도 | 학습시간 | 개념 깊이 |
|------|--------|---------|----------|
| Polling | ⭐ 쉬움 | 1시간 | AJAX, 타이머 |
| SSE | ⭐⭐ 보통 | 2시간 | HTTP 스트림, 이벤트 소싱 |
| WebSocket | ⭐⭐⭐ 어려움 | 4시간 | TCP, 양방향 통신, 상태 관리 |

---

## 🚀 최종 제안

**당신의 프로젝트 특성상 SSE가 최적입니다:**

1. ✅ **알림 시스템**: 단방향 (서버 → 클라이언트)
2. ✅ **비용**: 서버 리소스 효율적
3. ✅ **구현**: 스프링 SseEmitter로 20줄이면 충분
4. ✅ **호환성**: 모던 브라우저 기본 지원
5. ✅ **확장**: 필요 시 나중에 WebSocket으로 업그레이드 가능

**SSE로 구현 후, 나중에 필요하면 WebSocket으로 교체하는 것이 기술적으로도 경제적으로도 현명합니다.**
