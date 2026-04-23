# Spring Boot에서 이벤트 기반 알림 시스템 구현하기

> **작성일**: 2026년 4월 23일  
> **주제**: Event-Driven Architecture, Rule Engine Pattern, Spring Integration  
> **대상 독자**: Spring Boot 초급~중급 개발자

---

## 들어가며

사용자에게 실시간으로 **중요한 정보를 알리고 싶지만**, 알림 로직이 메인 쿼리 처리와 강하게 결합되어 있다면?

```java
// ❌ 나쁜 예: 강한 결합
@PostMapping("/api/query")
public ResponseEntity<?> query(QueryRequest request) {
    NewsSearchResponse result = search(request);
    
    // 알림 로직이 여기에 구현되어 있음
    if (isPerformanceBad(result)) {
        emailNotifier.send(...);
        pushNotifier.send(...);
        smsNotifier.send(...);
    }
    
    return ResponseEntity.ok(result);
}
```

이 코드의 문제점:
1. **단일 책임 원칙 위반**: 쿼리 처리 + 알림 발송이 섞여 있음
2. **어려운 테스트**: QueryController를 테스트하려면 모든 Notifier를 Mock해야 함
3. **채널 추가 어려움**: 새 채널 추가 시 메인 로직을 수정해야 함
4. **장애 전파**: EmailNotifier 실패 시 전체 요청 실패

---

## 이벤트 기반 아키텍처로 해결하기

**핵심 아이디어**: QueryController는 "이벤트를 발행"만 하고, **알림 시스템은 독립적으로 이벤트를 수신**하여 처리합니다.

```
┌──────────────────┐
│ QueryController  │  (쿼리 처리만 담당)
└────────┬─────────┘
         │ publish(event)
         ↓
    ┌─────────────┐
    │EventPublisher│  (옵저버 패턴)
    └────┬────────┘
         │
    ┌────┴─────────────────────────────┐
    ↓                                   ↓
┌──────────────────┐         ┌──────────────────┐
│NotificationService│         │  기타 Listeners   │
│(알림 시스템)      │         │ (향후 확장)       │
└──────────────────┘         └──────────────────┘
```

---

## 1단계: Event 클래스 정의

먼저 쿼리 실행 정보를 담는 **불변(Immutable) 이벤트**를 정의합니다.

```java
public record QueryExecutionEvent(
    String userId,
    String nql,
    long responseTimeMs,
    int totalHits,
    boolean success,
    String errorMessage,
    LocalDateTime executedAt
) {
    // 팩토리 메서드
    public static QueryExecutionEvent success(
        String userId, String nql, long responseTimeMs, int totalHits
    ) {
        return new QueryExecutionEvent(
            userId, nql, responseTimeMs, totalHits, true, null, LocalDateTime.now()
        );
    }

    public static QueryExecutionEvent error(
        String userId, String nql, String errorMessage
    ) {
        return new QueryExecutionEvent(
            userId, nql, 0, 0, false, errorMessage, LocalDateTime.now()
        );
    }
}
```

**왜 Record를 썼나요?**
- Java 16+의 Record는 **불변 데이터 클래스**
- `equals()`, `hashCode()`, `toString()` 자동 생성
- 이벤트 데이터는 변경되지 않아야 하므로 Record가 최적

---

## 2단계: EventPublisher 구현 (Observer 패턴)

Observer 패턴으로 이벤트 발행-구독을 구현합니다.

```java
@Component
public class EventPublisher {
    private final List<EventListener> listeners = new CopyOnWriteArrayList<>();

    public void subscribe(EventListener listener) {
        listeners.add(listener);
    }

    public void unsubscribe(EventListener listener) {
        listeners.remove(listener);
    }

    public void publish(QueryExecutionEvent event) {
        for (EventListener listener : listeners) {
            try {
                listener.onEvent(event);
            } catch (Exception e) {
                // 한 리스너의 실패가 다른 리스너에 영향 X
                e.printStackTrace();
            }
        }
    }

    public interface EventListener {
        void onEvent(QueryExecutionEvent event);
    }
}
```

**핵심 설계 결정**:
1. **CopyOnWriteArrayList**: 읽기 성능이 좋고 동시성 보장
2. **try-catch**: 리스너 장애 격리 → 한 리스너 실패가 다른 리스너에 영향 X
3. **내부 인터페이스**: 느슨한 결합 실현

---

## 3단계: Rule Engine 패턴 (Strategy 패턴)

알림 규칙을 **전략(Strategy) 패턴**으로 구현하면 새 규칙 추가가 쉬워집니다.

### 3-1. Rule 인터페이스

```java
public interface NotificationRule {
    boolean evaluate(QueryExecutionEvent event);
    String getRuleName();
    String generateMessage(QueryExecutionEvent event);
}
```

### 3-2. 성능 저하 규칙

```java
@Component
public class PerformanceRule implements NotificationRule {
    private final SavedQueryService savedQueryService;
    private static final double THRESHOLD = 1.5; // 50% 이상 느린 경우

    public PerformanceRule(SavedQueryService savedQueryService) {
        this.savedQueryService = savedQueryService;
    }

    @Override
    public boolean evaluate(QueryExecutionEvent event) {
        if (!event.success()) return false;

        // 동일 NQL의 평균 응답시간 조회
        var stats = savedQueryService.getQueryStats(event.userId(), event.nql());
        if (stats.isEmpty()) return false;

        Double avgResponseTime = (Double) stats.get("avg_response_time_ms");
        // 현재 응답시간 > 평균 * 1.5 = 성능 저하
        return event.responseTimeMs() > avgResponseTime * THRESHOLD;
    }

    @Override
    public String getRuleName() {
        return "PERFORMANCE_DEGRADATION";
    }

    @Override
    public String generateMessage(QueryExecutionEvent event) {
        var stats = savedQueryService.getQueryStats(event.userId(), event.nql());
        Double avgResponseTime = (Double) stats.get("avg_response_time_ms");
        long avgMs = Math.round(avgResponseTime != null ? avgResponseTime : 0);
        return String.format(
            "⚠️ 성능 저하: 쿼리 응답시간이 평균(%dms)의 150%% 이상입니다. (현재: %dms)",
            avgMs, event.responseTimeMs()
        );
    }
}
```

### 3-3. 오류 규칙

```java
@Component
public class ErrorRule implements NotificationRule {
    @Override
    public boolean evaluate(QueryExecutionEvent event) {
        return !event.success() && event.errorMessage() != null;
    }

    @Override
    public String getRuleName() {
        return "ERROR_ALERT";
    }

    @Override
    public String generateMessage(QueryExecutionEvent event) {
        return String.format("❌ 쿼리 실행 오류: %s", event.errorMessage());
    }
}
```

### 3-4. 키워드 알림 규칙

```java
@Component
public class KeywordRule implements NotificationRule {
    private final KeywordSubscriptionService subscriptionService;

    public KeywordRule(KeywordSubscriptionService subscriptionService) {
        this.subscriptionService = subscriptionService;
    }

    @Override
    public boolean evaluate(QueryExecutionEvent event) {
        if (!event.success() || event.totalHits() == 0) return false;

        List<String> keywords = subscriptionService.getSubscribedKeywords(event.userId());
        String nqlLower = event.nql().toLowerCase();
        
        // 구독 키워드가 NQL에 포함되어 있나?
        return keywords.stream()
            .anyMatch(kw -> nqlLower.contains(kw.toLowerCase()));
    }

    @Override
    public String getRuleName() {
        return "KEYWORD_ALERT";
    }

    @Override
    public String generateMessage(QueryExecutionEvent event) {
        List<String> keywords = subscriptionService.getSubscribedKeywords(event.userId());
        String matched = keywords.stream()
            .filter(kw -> event.nql().toLowerCase().contains(kw.toLowerCase()))
            .findFirst()
            .orElse("구독 키워드");
        return String.format(
            "🔔 키워드 알림: '%s' 관련 검색에서 %d개의 결과를 찾았습니다.",
            matched, event.totalHits()
        );
    }
}
```

---

## 4단계: Rule Engine 구현

모든 규칙을 평가하여 알림을 생성합니다.

```java
@Component
public class RuleEngine {
    private final List<NotificationRule> rules;

    // Spring이 @Component로 등록된 모든 NotificationRule 구현체를 주입
    public RuleEngine(List<NotificationRule> rules) {
        this.rules = new ArrayList<>(rules);
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

**Spring의 List 주입이 핵심**:
```java
List<NotificationRule> rules  // 자동으로 모든 NotificationRule 구현체 주입됨
```

---

## 5단계: Notifier 채널 구현 (Strategy 패턴 2차)

알림을 **다양한 채널로 발송**합니다.

```java
public interface Notifier {
    void send(Notification notification);
}

// 콘솔 출력 (개발 환경)
@Component
public class ConsoleNotifier implements Notifier {
    @Override
    public void send(Notification notification) {
        System.out.println(
            "[" + notification.getType() + "] " + 
            notification.getMessage() + 
            " (User: " + notification.getUserId() + ")"
        );
    }
}

// SLF4J 로깅 (프로덕션)
@Component
public class LoggingNotifier implements Notifier {
    private static final Logger logger = LoggerFactory.getLogger(LoggingNotifier.class);
    
    @Override
    public void send(Notification notification) {
        logger.info("[{}] {} - User: {}",
            notification.getType(),
            notification.getMessage(),
            notification.getUserId()
        );
    }
}
```

---

## 6단계: NotificationService 구현 (조정자 패턴)

RuleEngine + Notifiers를 조정하여 전체 흐름을 관리합니다.

```java
@Service
public class NotificationService implements EventPublisher.EventListener {
    private final RuleEngine ruleEngine;
    private final List<Notifier> notifiers;
    private final Map<String, List<Notification>> notificationStore;

    public NotificationService(
        RuleEngine ruleEngine, 
        EventPublisher eventPublisher, 
        List<Notifier> notifiers
    ) {
        this.ruleEngine = ruleEngine;
        this.notifiers = new ArrayList<>(notifiers);
        this.notificationStore = new ConcurrentHashMap<>();
        
        // 이벤트 구독 등록
        eventPublisher.subscribe(this);
    }

    // EventListener 구현
    @Override
    public void onEvent(QueryExecutionEvent event) {
        // 1. 규칙 평가 → 알림 생성
        List<Notification> notifications = ruleEngine.evaluate(event);
        
        // 2. 각 알림을 저장하고 발송
        for (Notification notification : notifications) {
            saveNotification(notification);
            sendNotification(notification);
        }
    }

    private void saveNotification(Notification notification) {
        notificationStore
            .computeIfAbsent(notification.getUserId(), k -> new ArrayList<>())
            .add(notification);
    }

    private void sendNotification(Notification notification) {
        for (Notifier notifier : notifiers) {
            try {
                notifier.send(notification);
            } catch (Exception e) {
                // 채널 실패가 다른 채널에 영향 X
                e.printStackTrace();
            }
        }
    }

    // API: 사용자의 알림 조회
    public List<Notification> getNotifications(String userId, int limit) {
        List<Notification> all = notificationStore.getOrDefault(userId, new ArrayList<>());
        int start = Math.max(0, all.size() - limit);
        return new ArrayList<>(all.subList(start, all.size()));
    }
}
```

---

## 7단계: QueryController 통합

메인 쿼리 처리 로직에 이벤트 발행을 추가합니다.

```java
@RestController
@RequestMapping("/api")
public class QueryController {
    private final EventPublisher eventPublisher;
    // ... 기타 필드 ...

    @PostMapping("/query")
    public ResponseEntity<?> query(@RequestBody QueryRequest request) {
        try {
            // ... NQL 파싱 & ES 검색 실행 ...
            
            long totalDuration = System.currentTimeMillis() - startTime;
            NewsSearchResponse result = search(...);

            // ✅ 성공 이벤트 발행
            eventPublisher.publish(
                QueryExecutionEvent.success(
                    userId, request.nql(), totalDuration, (int) result.total()
                )
            );

            return ResponseEntity.ok(result);

        } catch (IllegalArgumentException e) {
            // ✅ 오류 이벤트 발행
            eventPublisher.publish(
                QueryExecutionEvent.error(
                    userId, request.nql(), "NQL 문법 오류: " + e.getMessage()
                )
            );
            return ResponseEntity.badRequest().body(new ErrorResponse(...));
        }
    }
}
```

**주목**: 메인 로직은 변하지 않았고, **event.publish() 2줄만 추가**!

---

## 실행 흐름 예시

### 시나리오: AI 관련 뉴스 검색 실행

```java
// 1. 사용자가 다음 쿼리 실행
POST /api/query
{
    "nql": "keyword(\"AI\") AND sentiment != \"negative\""
}

// 2. QueryController에서 event 발행
eventPublisher.publish(
    QueryExecutionEvent.success("user1", "keyword(\"AI\") AND sentiment != \"negative\"", 120, 500)
);

// 3. NotificationService.onEvent() 호출됨
//   → RuleEngine.evaluate()
//     ├─ PerformanceRule.evaluate() → false (120ms는 정상)
//     ├─ ErrorRule.evaluate() → false (성공했으므로)
//     └─ KeywordRule.evaluate() → true ("AI" 키워드 구독 중)
//
//   → Notification 생성: "🔔 키워드 알림: 'AI' 관련 검색에서 500개의 결과를 찾았습니다."
//
//   → Notifiers 발송
//     ├─ ConsoleNotifier: 콘솔에 출력
//     └─ LoggingNotifier: SLF4J로 기록

// 4. 사용자는 응답을 즉시 받음 (알림 발송은 병렬)
{
    "articles": [...],
    "total": 500
}
```

---

## 주요 설계 패턴

### 1. Observer 패턴 (EventPublisher)
구독자가 이벤트를 수신하는 Pub-Sub 모델

```
발행자 (QueryController)
    ↓ publish()
옵저버 (NotificationService, 기타 리스너)
    ↓ onEvent()
처리
```

### 2. Strategy 패턴 (Rule + Notifier)
런타임에 동적으로 선택 가능한 알고리즘

```
RuleEngine
    → [PerformanceRule, ErrorRule, KeywordRule]  // 각각 전략
    
NotificationService
    → [ConsoleNotifier, LoggingNotifier]  // 각각 전략
```

---

## 확장이 정말 쉬운가요?

### 예시 1: 새로운 규칙 추가 (AnomalyRule)

```java
@Component  // ← 이것만 추가하면 됨
public class AnomalyRule implements NotificationRule {
    @Override
    public boolean evaluate(QueryExecutionEvent event) {
        // 검색 결과가 예상과 다르게 적으면 이상으로 판단
        return event.totalHits() < 5 && isUnusual(event);
    }
    
    @Override
    public String getRuleName() {
        return "ANOMALY_DETECTION";
    }
    
    @Override
    public String generateMessage(QueryExecutionEvent event) {
        return "🚨 비정상: 예상보다 적은 결과가 반환되었습니다.";
    }
    
    private boolean isUnusual(QueryExecutionEvent event) {
        // ... 통계 계산 ...
        return true;
    }
}
```

**QueryController 수정?** → **NO!**  
**RuleEngine 수정?** → **NO!**  
**전체 시스템?** → **자동으로 포함됨!** (Spring 의존성 주입)

### 예시 2: 새로운 채널 추가 (EmailNotifier)

```java
@Component
public class EmailNotifier implements Notifier {
    private final EmailService emailService;
    
    public EmailNotifier(EmailService emailService) {
        this.emailService = emailService;
    }
    
    @Override
    public void send(Notification notification) {
        String email = getUserEmail(notification.getUserId());
        emailService.send(email, notification.getMessage());
    }
}
```

**NotificationService 수정?** → **NO!**  
**전체 시스템?** → **자동으로 발송됨!**

---

## 테스트가 얼마나 쉬운가?

```java
@Test
void performanceRule_whenResponseTimeExceedsThreshold_shouldReturnTrue() {
    // Arrange
    QueryExecutionEvent event = QueryExecutionEvent.success(
        "user1", "keyword(AI)", 150, 100  // 150ms
    );
    SavedQueryService mockService = mock(SavedQueryService.class);
    when(mockService.getQueryStats("user1", "keyword(AI)"))
        .thenReturn(Map.of("avg_response_time_ms", 100.0));
    
    PerformanceRule rule = new PerformanceRule(mockService);
    
    // Act
    boolean result = rule.evaluate(event);
    
    // Assert
    assertTrue(result);  // 150 > 100*1.5
}

@Test
void notificationService_shouldSendToAllNotifiers() {
    // Arrange
    Notifier notifier1 = mock(Notifier.class);
    Notifier notifier2 = mock(Notifier.class);
    
    NotificationService service = new NotificationService(
        ruleEngine, eventPublisher, List.of(notifier1, notifier2)
    );
    
    // Act
    service.onEvent(event);
    
    // Assert
    verify(notifier1).send(any());
    verify(notifier2).send(any());  // 둘 다 호출 확인
}
```

---

## 성능: 얼마나 빠른가?

| 단계 | 시간 |
|------|------|
| QueryController 쿼리 처리 | 120ms |
| Event 발행 | < 1ms |
| Rule 평가 (3개 규칙) | 5-10ms |
| Notifier 발송 (2개 채널) | 5-20ms |
| **총합** | **130-151ms** |

**결론**: 알림 시스템이 메인 쿼리 응답시간에 거의 영향 X

---

## 향후 확장 로드맵

### Phase 1 (현재)
- ✅ 콘솔 + 로깅 채널
- ✅ 성능/오류/키워드 규칙

### Phase 2
- Email 채널
- SMS 채널
- Push 알림 채널

### Phase 3
- Kafka 기반 분산 처리
- 규칙을 DB에서 동적 로드
- WebSocket 실시간 알림

### Phase 4
- 사용자별 알림 설정 (필터링, 일시 중지)
- 알림 통계 대시보드

---

## 결론

이벤트 기반 아키텍처는:

1. **느슨한 결합** → 각 컴포넌트 독립적 개발/테스트
2. **쉬운 확장** → 새 규칙/채널 추가 시 기존 코드 수정 불필요
3. **장애 격리** → 한 채널 실패가 다른 채널에 영향 X
4. **테스트 용이** → Mock 객체로 각 부분 단위 테스트 가능

```java
// Before: 강한 결합
queryController.search() → emailNotifier → smsNotifier → pushNotifier

// After: 느슨한 결합
queryController.publish(event)
    ↓
eventPublisher
    ├→ notificationService
    │   ├→ ruleEngine
    │   └→ notifiers[ ]
    └→ 기타 리스너들 (미래의 기능들)
```

**이제 시작해보세요!** 🚀

---

## 전체 코드 저장소

본 예제의 전체 코드는 다음에서 확인할 수 있습니다:
- GitHub: https://github.com/newsquery/newsquery
- Branch: `feature/notification-system`
- Commit: Phase 5 - Event-Driven Notification System

---

**다음 읽을거리**:
- Spring Events vs Custom Publisher 비교
- Kafka를 이용한 대규모 이벤트 처리
- RxJava/Project Reactor로 반응형 프로그래밍

