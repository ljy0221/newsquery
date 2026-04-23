# 이벤트 기반 아키텍처를 통한 알림 시스템 설계 및 구현 보고서

## 1. 개요

본 보고서는 N-QL Intelligence 프로젝트의 Phase 5에서 구현한 **이벤트 기반 알림 시스템(Event-Driven Notification System)**의 설계, 구현, 그리고 학습 내용을 정리한 문서입니다.

### 1.1 목표
- 쿼리 실행 결과에 따라 실시간으로 사용자에게 알림을 전달
- 느슨한 결합(Loose Coupling)을 통한 확장 가능한 아키텍처 구현
- Rule Engine 패턴을 활용한 선언적 알림 규칙 정의

### 1.2 구현 범위
- **3가지 알림 타입**: 성능 저하, 오류, 키워드 관심사
- **2가지 채널**: 콘솔 로깅, SLF4J 로깅
- **메모리 기반 저장소**: 프로토타입 단계의 in-memory 구현

---

## 2. 아키텍처 설계

### 2.1 전체 흐름

```
┌─────────────────┐
│ QueryController │
│  query() 메서드  │
└────────┬────────┘
         │
         ├─→ NQL 파싱 & ES 검색 실행
         │
         ├─→ 성공 시 QueryExecutionEvent.success() 발행
         │   또는 실패 시 QueryExecutionEvent.error() 발행
         │
         └─→ EventPublisher.publish(event)
                    │
                    ├─→ NotificationService.onEvent()
                    │   └─→ RuleEngine.evaluate(event)
                    │       ├─→ PerformanceRule
                    │       ├─→ ErrorRule
                    │       └─→ KeywordRule
                    │
                    └─→ Notifiers에 전송
                        ├─→ ConsoleNotifier
                        └─→ LoggingNotifier
```

### 2.2 핵심 컴포넌트 역할

| 컴포넌트 | 책임 | 설계 패턴 |
|---------|------|---------|
| **QueryExecutionEvent** | 쿼리 실행 정보를 담는 이벤트 데이터 | Record (불변) |
| **EventPublisher** | 이벤트 구독자 관리 및 발행 | Observer 패턴 |
| **RuleEngine** | 모든 규칙을 평가하여 알림 결정 | Strategy 패턴 |
| **NotificationRule** | 개별 알림 규칙 정의 (인터페이스) | Strategy 패턴 |
| **NotificationService** | 규칙 평가 후 알림 발송 조정 | Coordinator 패턴 |
| **Notifier** | 알림 채널 추상화 | Strategy 패턴 |

---

## 3. 구현 상세

### 3.1 Event 계층 (com.newsquery.event)

#### 3.1.1 QueryExecutionEvent (Record)
```java
public record QueryExecutionEvent(
    String userId,
    String nql,
    long responseTimeMs,
    int totalHits,
    boolean success,
    String errorMessage,
    LocalDateTime executedAt
)
```

**특징**:
- Java 16+ Record 사용으로 불변성 보장
- 팩토리 메서드 `success()`, `error()` 제공
- `executedAt` 자동 기록

**이점**:
- 보일러플레이트 코드 제거
- `equals()`, `hashCode()`, `toString()` 자동 생성
- 데이터 클래스로서의 명확한 의도 표현

#### 3.1.2 EventPublisher (Observer 패턴)
```java
public class EventPublisher {
    private final List<EventListener> listeners = new CopyOnWriteArrayList<>();
    
    public void subscribe(EventListener listener)
    public void unsubscribe(EventListener listener)
    public void publish(QueryExecutionEvent event)
}
```

**설계 결정**:
- `CopyOnWriteArrayList` 사용 → 동시성 보장 + 읽기 성능 (읽기가 많은 경우)
- 내부 `EventListener` 인터페이스 → 느슨한 결합
- 예외 처리: 한 리스너의 실패가 다른 리스너에 영향 X

### 3.2 Rule 계층 (com.newsquery.notification.rule)

#### 3.2.1 NotificationRule 인터페이스
```java
public interface NotificationRule {
    boolean evaluate(QueryExecutionEvent event);
    String getRuleName();
    String generateMessage(QueryExecutionEvent event);
}
```

**설계 의도**:
- 새 규칙 추가 시 인터페이스만 구현 → 개방-폐쇄 원칙(OCP) 준수
- `getRuleName()`: 로깅/디버깅용
- `generateMessage()`: 사용자 친화적 메시지 생성

#### 3.2.2 PerformanceRule (구체적 구현)

```java
@Component
public class PerformanceRule implements NotificationRule {
    private static final double PERFORMANCE_THRESHOLD_RATIO = 1.5; // 50%
    
    @Override
    public boolean evaluate(QueryExecutionEvent event) {
        // 1. 성공한 쿼리만 평가
        if (!event.success()) return false;
        
        // 2. 동일 NQL의 과거 통계 조회
        var stats = savedQueryService.getQueryStats(
            event.userId(), 
            event.nql()
        );
        
        // 3. 평균 응답시간 대비 150% 이상 = 이상
        Double avgResponseTime = (Double) stats.get("avg_response_time_ms");
        return event.responseTimeMs() > avgResponseTime * PERFORMANCE_THRESHOLD_RATIO;
    }
}
```

**학습 포인트**:
- **상태 기반 규칙**: SavedQueryService에서 과거 통계를 읽어 비교
- **안전한 처리**: try-catch로 예외 처리 → 규칙 실패가 시스템 중단 X
- **확장성**: 임계값(1.5)을 상수로 정의하여 나중에 설정 가능하게 구조화

#### 3.2.3 ErrorRule

```java
@Component
public class ErrorRule implements NotificationRule {
    @Override
    public boolean evaluate(QueryExecutionEvent event) {
        return !event.success() && event.errorMessage() != null;
    }
}
```

**특징**:
- 가장 단순한 규칙 → 조건 검사만 수행
- Stateless: 외부 서비스 호출 불필요

#### 3.2.4 KeywordRule (복합 로직)

```java
@Component
public class KeywordRule implements NotificationRule {
    private final KeywordSubscriptionService keywordSubscriptionService;
    
    @Override
    public boolean evaluate(QueryExecutionEvent event) {
        // 1. 성공한 쿼리 + 결과 있음
        if (!event.success() || event.totalHits() == 0) return false;
        
        // 2. 사용자의 구독 키워드 목록 조회
        List<String> subscriptions = keywordSubscriptionService
            .getSubscribedKeywords(event.userId());
        
        // 3. NQL에 구독 키워드가 포함되어 있는지 확인
        String nqlLower = event.nql().toLowerCase();
        return subscriptions.stream()
            .anyMatch(kw -> nqlLower.contains(kw.toLowerCase()));
    }
}
```

**설계 특징**:
- **사용자 맞춤형**: 각 사용자의 구독 키워드를 개별적으로 관리
- **대소문자 무시**: 사용 편의성 향상
- **Stream API**: 함수형 프로그래밍으로 가독성 높음

### 3.3 Rule Engine (Strategy 패턴 조합)

```java
@Component
public class RuleEngine {
    private final List<NotificationRule> rules = new ArrayList<>();
    
    public RuleEngine(List<NotificationRule> rules) {
        this.rules.addAll(rules);
    }
    
    public List<Notification> evaluate(QueryExecutionEvent event) {
        List<Notification> notifications = new ArrayList<>();
        for (NotificationRule rule : rules) {
            if (rule.evaluate(event)) {
                notifications.add(new Notification(
                    event.userId(),
                    rule.getRuleName(),
                    rule.generateMessage(event),
                    event.executedAt()
                ));
            }
        }
        return notifications;
    }
}
```

**설계 패턴 분석**:
- **Strategy 패턴**: 각 규칙은 독립적인 전략
- **조합 가능성**: 하나의 이벤트에서 여러 규칙이 동시에 트리거될 수 있음
- **확장성**: 새 규칙 추가는 `RuleEngine` 수정 불필요 (Spring이 자동 주입)

### 3.4 Notification Service 계층

#### 3.4.1 Notification (도메인 모델)
```java
public class Notification {
    private final String id;
    private final String userId;
    private final String type;
    private final String message;
    private final LocalDateTime createdAt;
    private boolean read;
}
```

**책임**:
- 알림 데이터 모델
- 읽음/안읽음 상태 관리

#### 3.4.2 NotificationService (Coordinator 패턴)

```java
@Service
public class NotificationService implements EventPublisher.EventListener {
    private final RuleEngine ruleEngine;
    private final List<Notifier> notifiers;
    private final Map<String, List<Notification>> notificationStore;
    
    @Override
    public void onEvent(QueryExecutionEvent event) {
        // 1. 규칙 평가 → 알림 생성
        List<Notification> notifications = ruleEngine.evaluate(event);
        
        // 2. 각 알림을 저장 + 채널로 발송
        for (Notification notification : notifications) {
            saveNotification(notification);      // 저장소
            sendNotification(notification);      // 채널
        }
    }
    
    private void sendNotification(Notification notification) {
        for (Notifier notifier : notifiers) {
            try {
                notifier.send(notification);  // 각 채널에 전송
            } catch (Exception e) {
                // 채널 실패가 다른 채널에 영향 X
                e.printStackTrace();
            }
        }
    }
}
```

**중요 설계**:
- **Coordinator 역할**: RuleEngine(규칙) + Notifiers(채널) 조정
- **EventListener 구현**: Spring EventPublisher에 자동 구독
- **다채널 발송**: 여러 채널에 동시 발송 (채널 간 독립성)
- **장애 격리**: 한 채널 실패가 전체 시스템 영향 X

### 3.5 Notifier 채널 계층

#### 3.5.1 Notifier 인터페이스
```java
public interface Notifier {
    void send(Notification notification);
}
```

#### 3.5.2 ConsoleNotifier
```java
@Component
public class ConsoleNotifier implements Notifier {
    @Override
    public void send(Notification notification) {
        System.out.println("[" + notification.getType() + "] " +
            notification.getMessage() + 
            " (User: " + notification.getUserId() + ")");
    }
}
```

**용도**: 개발/테스트 환경

#### 3.5.3 LoggingNotifier
```java
@Component
public class LoggingNotifier implements Notifier {
    private static final Logger logger = LoggerFactory.getLogger(...);
    
    @Override
    public void send(Notification notification) {
        logger.info("[{}] {} - User: {}",
            notification.getType(),
            notification.getMessage(),
            notification.getUserId());
    }
}
```

**용도**: 프로덕션 환경 + 모니터링

### 3.6 QueryController 통합

```java
@RestController
public class QueryController {
    private final EventPublisher eventPublisher;
    
    @PostMapping("/api/query")
    public ResponseEntity<?> query(@RequestBody QueryRequest request) {
        try {
            // ... 검색 실행 ...
            
            long totalDuration = System.currentTimeMillis() - startTime;
            
            // 성공 이벤트 발행 → NotificationService 트리거
            eventPublisher.publish(
                QueryExecutionEvent.success(
                    DEFAULT_USER_ID, 
                    request.nql(), 
                    totalDuration, 
                    (int) result.total()
                )
            );
            return ResponseEntity.ok(result);
            
        } catch (IllegalArgumentException e) {
            // 오류 이벤트 발행
            eventPublisher.publish(
                QueryExecutionEvent.error(
                    DEFAULT_USER_ID, 
                    request.nql(), 
                    "NQL 문법 오류: " + e.getMessage()
                )
            );
            // ...
        }
    }
}
```

---

## 4. 설계 패턴 분석

### 4.1 Observer 패턴 (EventPublisher)

| 구성요소 | 역할 |
|---------|------|
| **Subject** | EventPublisher |
| **Observer** | EventPublisher.EventListener 구현체 (NotificationService) |
| **Concrete Subject** | QueryController (publish 호출) |

**이점**:
- QueryController가 알림 시스템의 존재를 알 필요 X
- 새로운 리스너 추가 시 기존 코드 수정 불필요

### 4.2 Strategy 패턴 (Rule Engine)

```
RuleEngine
    │
    ├─ NotificationRule (전략 인터페이스)
    │   ├─ PerformanceRule
    │   ├─ ErrorRule
    │   └─ KeywordRule
```

**구현 방식**:
```java
for (NotificationRule rule : rules) {  // 각 전략을 순회
    if (rule.evaluate(event)) { ... }
}
```

**이점**:
- 런타임에 규칙 추가/변경 가능
- 각 규칙은 독립적으로 개발/테스트 가능
- OCP (Open-Closed Principle) 준수

### 4.3 Coordinator 패턴 (NotificationService)

```
NotificationService
    │
    ├─ RuleEngine (규칙 평가)
    │   └─ rules[ ]
    │
    └─ Notifiers[ ] (발송 채널)
        ├─ ConsoleNotifier
        └─ LoggingNotifier
```

**역할**:
- 규칙 평가 결과를 받아서 → 알림 객체 생성
- 알림을 모든 채널로 전달 → 채널 간 독립성 보장

---

## 5. 확장 가능성 분석

### 5.1 새로운 규칙 추가 방법

**예: TrendingRule (인기 검색 알림)**

```java
@Component
public class TrendingRule implements NotificationRule {
    @Override
    public boolean evaluate(QueryExecutionEvent event) {
        // 최근 1시간 동일 NQL 실행 횟수 > 10
        return getTrendCount(event.nql()) > 10;
    }
    
    @Override
    public String getRuleName() {
        return "TRENDING_QUERY";
    }
    
    @Override
    public String generateMessage(QueryExecutionEvent event) {
        return "🔥 인기 검색: " + event.nql();
    }
}
```

**추가 작업**:
1. 클래스 작성 및 `@Component` 등록
2. 끝 → RuleEngine이 자동으로 주입됨

### 5.2 새로운 채널 추가 방법

**예: EmailNotifier**

```java
@Component
public class EmailNotifier implements Notifier {
    private final EmailService emailService;
    
    @Override
    public void send(Notification notification) {
        String email = getUserEmail(notification.getUserId());
        emailService.send(email, notification.getMessage());
    }
}
```

**추가 작업**:
1. Notifier 구현
2. EmailService 의존성 주입
3. 끝 → NotificationService가 자동으로 포함함

---

## 6. 성능 및 확장성 고려사항

### 6.1 현재 구현의 한계

| 항목 | 현재 | 개선 방향 |
|-----|------|---------|
| **저장소** | In-memory Map | PostgreSQL + Redis 캐시 |
| **확장성** | 단일 서버 | Kafka 기반 분산 처리 |
| **채널** | 콘솔/SLF4J | Email, WebSocket, Push 추가 |
| **규칙 저장** | 코드에 하드코딩 | DB 기반 동적 규칙 관리 |

### 6.2 대규모 시스템으로의 전환

```
QueryController
    ├─ EventPublisher.publish()
    └─ Kafka Topic (notification-events)
            │
            ├─ Notification Consumer 1
            ├─ Notification Consumer 2
            └─ Notification Consumer N
                    │
                    ├─ RuleEngine 평가
                    ├─ DB 저장
                    └─ 다중 채널 발송
```

**이점**:
- 이벤트 발행/소비 비동기화
- 알림 처리 확장 가능
- 장애 격리 (Consumer 실패 시에도 Query는 정상)

---

## 7. 테스트 전략

### 7.1 단위 테스트 (규칙)

```java
@Test
void performanceRule_whenResponseTimeExceedsThreshold_returnsTrue() {
    // Arrange
    QueryExecutionEvent event = QueryExecutionEvent.success(
        "user1", "keyword(AI)", 150, 100  // 150ms
    );
    // SavedQueryService 모킹 → avg=100ms
    
    // Act
    boolean result = performanceRule.evaluate(event);
    
    // Assert
    assertTrue(result);  // 150 > 100*1.5
}
```

### 7.2 통합 테스트 (RuleEngine + NotificationService)

```java
@Test
void ruleEngine_whenMultipleRulesMatch_returnsMultipleNotifications() {
    // 1. PerformanceRule 조건 만족
    // 2. KeywordRule 조건 만족
    // 결과: 2개의 알림 생성
}
```

### 7.3 엔드투엔드 테스트

```java
@SpringBootTest
class NotificationIntegrationTest {
    @Test
    void fullFlow_queryExecution_triggersNotifications() {
        // 1. POST /api/query 실행
        // 2. QueryExecutionEvent 발행 확인
        // 3. Notification 저장소에 기록 확인
        // 4. ConsoleNotifier 출력 확인 (MockOut)
    }
}
```

---

## 8. 결론 및 교훈

### 8.1 핵심 학습점

1. **이벤트 기반 아키텍처**
   - 시스템 간 느슨한 결합 실현
   - 비동기 처리의 기반

2. **Strategy 패턴의 강력함**
   - 런타임 동적 동작 변경 가능
   - 새 기능 추가 시 기존 코드 수정 불필요

3. **Spring의 의존성 주입**
   - List<Interface> 주입으로 모든 구현체 자동 포함
   - 리플렉션 기반 다형성 활용

4. **안전한 멀티스레드 처리**
   - CopyOnWriteArrayList로 읽기 성능 + 동시성 보장
   - try-catch로 개별 리스너/채널 장애 격리

### 8.2 향후 개선 사항

1. **데이터 영속성**: PostgreSQL 연동
2. **실시간 전송**: WebSocket 기반 알림
3. **동적 규칙**: DB에서 규칙 조건 로드
4. **분산 처리**: Kafka/RabbitMQ 기반 이벤트 스트림
5. **모니터링**: Prometheus 메트릭 추가

### 8.3 최종 평가

이벤트 기반 알림 시스템은:
- ✅ **확장 가능**: 새 규칙/채널 추가 용이
- ✅ **느슨한 결합**: 컴포넌트 간 의존성 최소화
- ✅ **장애 격리**: 한 채널 실패가 다른 채널에 영향 X
- ✅ **테스트 용이**: 각 규칙/채널 독립적 테스트 가능

Phase 5 완성 후 **프로덕션 준비 단계**에서 Kafka + DB 영속성을 추가하면 enterprise-grade 알림 시스템으로 전환 가능합니다.

---

## 참고 자료

- **디자인 패턴**: Observer, Strategy, Coordinator 패턴
- **Java 기능**: Record (Java 16+), Stream API, CopyOnWriteArrayList
- **Spring Framework**: Event Listener, Dependency Injection, @Component
- **동시성**: Thread-safe Collection, Exception Handling in Async

