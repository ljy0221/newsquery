# N-QL Intelligence 기술 심화 학습 가이드 — Series 04. Phase 4-5 성능 최적화 & 이벤트 기반 아키텍처

**최종 업데이트**: 2026-04-23  
**난이도**: ⭐⭐⭐⭐ (고급)  
**예상 학습시간**: 60분  
**면접 예상 빈도**: ⭐⭐⭐⭐⭐ (매우 높음)

---

## 📚 목차

1. **Phase 4: 성능 최적화**
   - 2-계층 캐싱
   - Cold Start vs Warm Cache 분석
   - 캐싱 전략 검증

2. **Phase 5: 이벤트 기반 아키텍처**
   - 이벤트 발행-구독 패턴
   - 알림 시스템 설계
   - Rule Engine 패턴

3. **면접 Q&A**

---

## 1. Phase 4: 성능 최적화

### 1.1 배경: 왜 캐싱이 필요한가?

**문제 상황**:

```
Phase 3 성능: 30.94ms (안정적)

하지만 분석해보면:
- 새로운 쿼리: 45-55ms (ES + 임베딩 전체 경로)
- 반복되는 쿼리: 동일하게 45-55ms (캐싱 없음!)

아이러니:
┌─────────────────────────────────────────────┐
│ 사용자가 검색하는 쿼리는 대부분 반복된다     │
│                                             │
│ 예: "AI news" → 10명 사용자 모두 검색      │
│ └─ 매번 10 × 50ms = 500ms 낭비!            │
│                                             │
│ 해결책: "AI news" 결과를 5분 캐싱          │
│ → 9명은 3ms만에 결과 얻음                   │
└─────────────────────────────────────────────┘
```

### 1.2 2-계층 캐싱 아키텍처

#### 계층 1️⃣: L1 로컬 캐시 (Caffeine)

**특징**:
- 위치: JVM 메모리 (같은 프로세스)
- 지연시간: < 1μs
- TTL: 5분
- 크기: 최대 10,000항목

**언제 사용?**
- 같은 서버에서 반복되는 요청
- 캐시 재검증 불필요 (동일 프로세스)

```java
@Configuration
@EnableCaching
public class CacheConfig {
    
    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager cacheManager = 
            new CaffeineCacheManager("queryCache");
        
        cacheManager.setCaffeine(
            Caffeine.newBuilder()
                .maximumSize(10000)
                .expireAfterWrite(5, TimeUnit.MINUTES)
                .recordStats()  // 메트릭 수집
        );
        
        return cacheManager;
    }
}
```

**사용**:

```java
@Service
public class QueryService {
    
    // @Cacheable: 캐시 확인 → Hit면 반환
    @Cacheable(
        value = "queryCache",
        key = "'nql:' + #nql + ':page:' + #page",
        condition = "#nql.length() < 200"  // 짧은 쿼리만 캐싱
    )
    public NewsSearchResponse search(
        String nql, 
        int page) {
        
        // 실제 검색 로직
        NQLExpression expr = NQLQueryParser.parse(nql);
        return esService.search(expr, page);
    }
}
```

**메트릭 모니터링**:

```java
@GetMapping("/cache/stats")
public ResponseEntity<Map<String, Object>> cacheStats() {
    CaffeineCacheManager mgr = (CaffeineCacheManager) cacheManager;
    Cache cache = mgr.getCache("queryCache");
    
    CacheStats stats = ((CaffeineCache) cache)
        .getNativeCache()
        .stats();
    
    return ResponseEntity.ok(Map.of(
        "hitCount", stats.hitCount(),
        "missCount", stats.missCount(),
        "hitRate", stats.hitRate(),
        "averageLoadPenalty", stats.averageLoadPenalty()
    ));
}
```

---

#### 계층 2️⃣: L2 분산 캐시 (Redis)

**특징**:
- 위치: 별도 Redis 인스턴스
- 지연시간: 1-5ms (네트워크)
- TTL: 24시간 (임베딩), 5분 (쿼리)
- 크기: 무제한 (디스크 공간에 따라)

**언제 사용?**
- 여러 서버 간 공유
- 서버 재시작해도 유지 필요

```java
@Configuration
public class RedisConfig {
    
    @Bean
    public RedisConnectionFactory 
        redisConnectionFactory() {
        
        return new LettuceConnectionFactory();
    }
    
    @Bean
    public RedisTemplate<String, Object> 
        redisTemplate() {
        
        RedisTemplate<String, Object> template = 
            new RedisTemplate<>();
        
        template.setConnectionFactory(
            redisConnectionFactory());
        
        template.setDefaultSerializer(
            new GenericJackson2JsonRedisSerializer());
        
        return template;
    }
    
    @Bean
    public CacheManager redisCacheManager() {
        return RedisCacheManager.create(
            redisConnectionFactory());
    }
}
```

**application.yml**:

```yaml
spring:
  redis:
    host: localhost
    port: 6379
    timeout: 2000ms
    lettuce:
      pool:
        max-active: 8
        max-idle: 8
        min-idle: 0

cache:
  type: redis
  redis:
    time-to-live: 5m
```

---

#### 계층 통합: Caffeine + Redis

```java
@Service
public class TieredCacheService {
    
    private final CaffeineCacheManager caffeine;
    private final RedisCacheManager redis;
    private final QueryService queryService;
    
    public NewsSearchResponse search(
        String nql, 
        int page) {
        
        String cacheKey = buildCacheKey(nql, page);
        
        // 1️⃣ Caffeine (L1) 확인
        NewsSearchResponse cached = 
            caffeine.getCache("queryCache")
                .get(cacheKey, NewsSearchResponse.class);
        
        if (cached != null) {
            metrics.recordL1Hit();
            return cached;
        }
        
        // 2️⃣ Redis (L2) 확인
        cached = redis.getCache("queryCache")
            .get(cacheKey, NewsSearchResponse.class);
        
        if (cached != null) {
            // Redis 히트: Caffeine에도 추가
            caffeine.getCache("queryCache")
                .put(cacheKey, cached);
            
            metrics.recordL2Hit();
            return cached;
        }
        
        // 3️⃣ Miss: 실제 검색
        long startTime = System.currentTimeMillis();
        NewsSearchResponse result = 
            queryService.search(nql, page);
        long duration = System.currentTimeMillis() - startTime;
        
        // 캐시에 저장
        caffeine.getCache("queryCache")
            .put(cacheKey, result);
        redis.getCache("queryCache")
            .put(cacheKey, result);
        
        metrics.recordMiss(duration);
        return result;
    }
}
```

---

### 1.3 Cold Start vs Warm Cache 분석

**문제**: Phase 4 초기에 예상 외 결과 발견

```
1️⃣ 첫 실행 (Cold Start)
   - Caffeine 캐시 비어있음
   - Redis 캐시 비어있음
   - 모든 요청이 DB 경로 실행
   
   결과: 52.3ms (캐싱 전과 유사)
   
   🤔 결론 (잘못됨): "캐싱 효과 미미"

2️⃣ 두 번째 실행 (Warm Cache)
   - Caffeine 캐시 데이터 가득
   - Redis 캐시 데이터 가득
   - 대부분 요청이 캐시에서 반환
   
   결과: 36.05ms (-33% 개선)
   
   ✅ 결론 (정확함): "캐싱 효과 우수"
```

**이 차이를 이해하는 것이 중요!**

```
마치 식당처럼:

Cold Start (처음 문을 열 때):
├─ 재료 장보기: 30분
├─ 요리: 20분
├─ 고객 서빙: 50분
└─ 1시간 이상

Warm Cache (재료 다 준비되어 있을 때):
├─ 재료 꺼내기: 1분
├─ 요리: 20분
├─ 고객 서빙: 1분
└─ 22분 (73% 시간 단축)
```

---

### 1.4 캐싱 전략 검증

#### 전략 1️⃣: 임베딩 캐싱 (24시간 TTL)

**가설**: 같은 텍스트는 같은 임베딩

```java
@Service
public class EmbeddingClient {
    
    private final RestTemplate restTemplate;
    private final RedisTemplate<String, List<Float>> redis;
    
    public List<Float> embed(String text) {
        // 1. Redis에서 확인 (24시간 캐시)
        String cacheKey = "embedding:" + 
            DigestUtils.md5Hex(text);
        
        List<Float> cached = 
            redis.opsForValue().get(cacheKey);
        
        if (cached != null) {
            return cached;  // 2ms ⭐
        }
        
        // 2. FastAPI 호출 (실제 임베딩)
        ResponseEntity<float[]> response = 
            restTemplate.postForEntity(
                "http://localhost:8000/embed/single",
                new EmbedRequest(text),
                float[].class
            );
        
        float[] embedding = response.getBody();
        List<Float> result = Arrays.stream(embedding)
            .boxed()
            .collect(toList());
        
        // 3. Redis 저장 (24시간)
        redis.opsForValue().set(
            cacheKey, 
            result, 
            Duration.ofHours(24)
        );
        
        return result;  // 8-10ms (첫 요청)
    }
}
```

**효과**:
```
Before: keyword("AI") → FastAPI 호출 → 8-10ms
After:  keyword("AI") → Redis 히트 → 2ms

개선율: -80% ✅
```

---

#### 전략 2️⃣: 쿼리 캐싱 (5분 TTL)

```java
// 쿼리 결과를 5분 캐싱
// 하지만 조건부:
// - 짧은 쿼리만 (< 200자)
// - 페이지 0-10만
// - 사용자별로 다르지 않은 결과

@Cacheable(
    value = "queryCache",
    key = "'q:' + #nql + ':p:' + #page",
    condition = "#nql.length() < 200 && #page < 10"
)
public NewsSearchResponse search(
    String nql, 
    int page) {
    
    // 검색 로직
}
```

**효과**:
```
아침 9시: 1,000명 "AI news" 검색
- 첫 명: 45ms (캐시 미스)
- 나머지 999명: 3ms (캐시 히트)

결과: 999 × 3ms + 1 × 45ms = 3,000ms
vs 캐싱 없이: 1,000 × 45ms = 45,000ms

절감: 42,000ms (93% 시간 절감!) 🎉
```

---

#### 전략 3️⃣: GROUP BY 집계 캐싱 (5분 TTL)

```java
@Cacheable(
    value = "aggregationCache",
    key = "'agg:' + #field + ':' + #nql",
    unless = "#result == null"
)
public AggregationResult aggregateBy(
    String field,      // sentiment, source, category
    String nql) {
    
    // GROUP BY category 실행
    // 결과: {"positive": 5000, "negative": 3000, ...}
}
```

**왜 GROUP BY가 캐시 킬러 유스케이스인가?**

```
쿼리: keyword("AI") GROUP BY sentiment

특성:
1️⃣ 자주 반복 (사용자가 항상 보는 차트)
2️⃣ TTL 길어도 OK (범주 분포는 천천히 변함)
3️⃣ 계산 비용 높음 (모든 문서 집계)

효과:
Before: 40ms (매번 계산)
After:  0ms (캐시 히트, 즉시)

개선율: -100% (무한대? 아니 거의 0ms) ⭐⭐⭐
```

---

### 1.5 캐시 무효화 전략

**문제**: 새 뉴스가 추가되면 캐시가 부정확해짐

```
10:00 - "AI news" 검색 → 결과: 1000개 → 캐시 저장
10:05 - 새로운 AI 뉴스 100개 추가
10:06 - "AI news" 검색 → 캐시에서 1000개 반환 (❌ 100개 누락!)
```

**해결책 1️⃣: TTL 설정 (Simple)**

```yaml
cache:
  ttl: 5m  # 5분 후 자동 만료

효과:
- 5분 이상된 캐시는 자동 갱신
- 구현 간단
- 단점: 항상 5분 지연 가능
```

**해결책 2️⃣: 이벤트 기반 무효화 (Smart)**

```java
// 뉴스 추가 시 관련 캐시만 무효화
@Service
public class NewsIngestionService {
    
    @Autowired
    private CacheManager cacheManager;
    
    public void ingestNews(News news) {
        // 뉴스 저장
        newsRepository.save(news);
        
        // 관련 캐시 무효화
        // 예: "technology" 카테고리 뉴스면
        // "category:technology" 캐시만 제거
        
        String affectedKey = 
            "agg:category:" + news.getCategory();
        
        cacheManager.getCache("aggregationCache")
            .evict(affectedKey);
    }
}

// 또는 @CacheEvict 사용
@CacheEvict(
    value = "aggregationCache",
    key = "'agg:' + #category",
    beforeInvocation = false
)
public void updateNewsCategory(
    Long newsId, 
    String category) {
    // 로직
}
```

---

## 2. Phase 5: 이벤트 기반 아키텍처

### 2.1 왜 이벤트 기반인가?

**문제**: Phase 4까지는 "응답"만 관심

```
┌──────────────┐
│ QueryRequest │
└──────┬───────┘
       │
       ▼
   검색 실행
       │
       ▼
┌──────────────────┐
│ NewsSearchResponse│
└──────────────────┘

문제:
- 응답 후 아무것도 못함
- "느린 쿼리가 있는데 누가 알지?"
- 성능 저하 감지 불가능
```

**해결책**: 이벤트 발행

```
┌──────────────┐
│ QueryRequest │
└──────┬───────┘
       │
       ▼
   검색 실행
       │
       ▼
┌──────────────────────┐
│ QueryExecutionEvent  │  ← 이벤트 발행!
├─ responseTimeMs: 50  │
├─ totalHits: 1000     │
├─ success: true       │
└──────┬───────────────┘
       │
       ├─→ [구독자 1] 성능 감시
       │   └─ 50ms > 100ms? → 알림 발송 X
       │
       ├─→ [구독자 2] 로깅
       │   └─ slow_query.log에 기록
       │
       └─→ [구독자 3] 통계
           └─ 응답시간 메트릭 수집
```

---

### 2.2 이벤트 발행-구독 패턴 구현

#### Step 1: 이벤트 정의 (Record)

```java
// 불변 데이터 클래스
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
        String userId,
        String nql,
        long responseTimeMs,
        int totalHits) {
        
        return new QueryExecutionEvent(
            userId,
            nql,
            responseTimeMs,
            totalHits,
            true,
            null,
            LocalDateTime.now()
        );
    }
    
    public static QueryExecutionEvent error(
        String userId,
        String nql,
        long responseTimeMs,
        String errorMessage) {
        
        return new QueryExecutionEvent(
            userId,
            nql,
            responseTimeMs,
            0,
            false,
            errorMessage,
            LocalDateTime.now()
        );
    }
}
```

---

#### Step 2: 이벤트 퍼블리셔 (Event Bus)

```java
public interface EventListener {
    void onQueryExecuted(QueryExecutionEvent event);
}

@Component
public class EventPublisher {
    
    private final List<EventListener> listeners = 
        new CopyOnWriteArrayList<>();
    
    // 1️⃣ 구독자 등록
    public void subscribe(EventListener listener) {
        listeners.add(listener);
    }
    
    // 2️⃣ 구독자 제거
    public void unsubscribe(EventListener listener) {
        listeners.remove(listener);
    }
    
    // 3️⃣ 이벤트 발행 (모든 구독자에게)
    public void publish(QueryExecutionEvent event) {
        // 동시성 안전 (CopyOnWriteArrayList)
        listeners.forEach(listener -> 
            listener.onQueryExecuted(event)
        );
    }
}
```

**설계 결정**: 왜 CopyOnWriteArrayList?

```
CopyOnWriteArrayList:
- 쓰기: 복사본 생성 + 수정 + 교체 (느림)
- 읽기: 원본 그대로 읽기 (빠름)

우리 경우:
- 쓰기: subscribe/unsubscribe (드문 일)
- 읽기: publish (매 요청마다 = 자주)

→ 읽기 성능 우선 ✅
```

---

#### Step 3: 이벤트 구독자 (Listeners)

```java
// 1️⃣ 성능 감시
@Component
public class PerformanceMonitor implements EventListener {
    
    private static final long THRESHOLD_MS = 100;
    
    @Override
    public void onQueryExecuted(QueryExecutionEvent event) {
        if (event.responseTimeMs() > THRESHOLD_MS) {
            // 알림 발송
            notificationService.sendAlert(
                "Slow query detected: " + 
                event.responseTimeMs() + "ms"
            );
        }
    }
}

// 2️⃣ 로깅
@Component
public class QueryLogger implements EventListener {
    
    private static final Logger log = 
        LoggerFactory.getLogger(QueryLogger.class);
    
    @Override
    public void onQueryExecuted(QueryExecutionEvent event) {
        if (!event.success()) {
            log.error(
                "Query failed: {} - {}",
                event.nql(),
                event.errorMessage()
            );
        } else if (event.responseTimeMs() > 50) {
            log.warn(
                "Slow query: {} ({}ms)",
                event.nql(),
                event.responseTimeMs()
            );
        } else {
            log.info(
                "Query: {} ({}ms, {}hits)",
                event.nql(),
                event.responseTimeMs(),
                event.totalHits()
            );
        }
    }
}

// 3️⃣ 메트릭 수집
@Component
public class MetricsCollector implements EventListener {
    
    @Autowired
    private MeterRegistry meterRegistry;
    
    @Override
    public void onQueryExecuted(QueryExecutionEvent event) {
        // 응답시간
        meterRegistry.timer("query.duration.ms")
            .record(event.responseTimeMs(), 
                TimeUnit.MILLISECONDS);
        
        // 결과 수
        meterRegistry.counter("query.hits",
            "nql", normalizeNql(event.nql()))
            .increment(event.totalHits());
        
        // 성공/실패
        if (event.success()) {
            meterRegistry.counter("query.success").increment();
        } else {
            meterRegistry.counter("query.error").increment();
        }
    }
}
```

---

#### Step 4: Controller에서 발행

```java
@RestController
@RequestMapping("/api")
public class QueryController {
    
    @Autowired
    private EventPublisher eventPublisher;
    
    @PostMapping("/query")
    public ResponseEntity<NewsSearchResponse> query(
        @RequestBody QueryRequest req) {
        
        long startTime = System.currentTimeMillis();
        
        try {
            // 검색 실행
            NewsSearchResponse result = 
                searchService.search(req.getNql());
            
            long duration = 
                System.currentTimeMillis() - startTime;
            
            // ✅ 성공 이벤트 발행
            eventPublisher.publish(
                QueryExecutionEvent.success(
                    getCurrentUserId(),
                    req.getNql(),
                    duration,
                    result.getTotalHits()
                )
            );
            
            return ResponseEntity.ok(result);
            
        } catch (Exception ex) {
            long duration = 
                System.currentTimeMillis() - startTime;
            
            // ❌ 실패 이벤트 발행
            eventPublisher.publish(
                QueryExecutionEvent.error(
                    getCurrentUserId(),
                    req.getNql(),
                    duration,
                    ex.getMessage()
                )
            );
            
            throw ex;
        }
    }
}
```

---

### 2.3 Rule Engine 패턴 (고급)

**더 복잡한 알림 규칙 필요시**:

```java
public interface NotificationRule {
    boolean evaluate(QueryExecutionEvent event);
    void execute(QueryExecutionEvent event);
}

// 규칙 1: 성능 저하
@Component
public class PerformanceRule implements NotificationRule {
    
    @Override
    public boolean evaluate(QueryExecutionEvent event) {
        return event.responseTimeMs() > 100;
    }
    
    @Override
    public void execute(QueryExecutionEvent event) {
        notificationService.alert(
            "느린 쿼리: " + event.responseTimeMs() + "ms"
        );
    }
}

// 규칙 2: 연속 오류
@Component
public class ErrorRateRule implements NotificationRule {
    
    private final AtomicInteger errorCount = 
        new AtomicInteger(0);
    
    @Override
    public boolean evaluate(QueryExecutionEvent event) {
        if (!event.success()) {
            return errorCount.incrementAndGet() >= 3;
        } else {
            errorCount.set(0);
            return false;
        }
    }
    
    @Override
    public void execute(QueryExecutionEvent event) {
        notificationService.alert(
            "연속 오류 감지: " + 
            errorCount.get() + "건"
        );
    }
}

// Rule Engine
@Component
public class RuleEngine implements EventListener {
    
    private final List<NotificationRule> rules;
    
    public RuleEngine(List<NotificationRule> rules) {
        this.rules = rules;  // 자동 주입
    }
    
    @Override
    public void onQueryExecuted(QueryExecutionEvent event) {
        rules.stream()
            .filter(rule -> rule.evaluate(event))
            .forEach(rule -> rule.execute(event));
    }
}
```

---

### 2.4 저장된 검색 & 히스토리 (Phase 5 기능)

```java
// 저장된 검색
@Entity
@Data
public class SavedQuery {
    @Id
    private UUID id;
    private UUID userId;
    private String nql;
    private String name;
    private LocalDateTime createdAt;
    private int executionCount;      // 실행 횟수
    private double avgResponseTimeMs; // 평균 응답시간
}

@Service
public class SavedQueryService {
    
    @Autowired
    private SavedQueryRepository repository;
    
    @Autowired
    private EventPublisher eventPublisher;
    
    public void executeSavedQuery(UUID queryId) {
        SavedQuery saved = repository.findById(queryId)
            .orElseThrow();
        
        long startTime = System.currentTimeMillis();
        
        try {
            NewsSearchResponse result = 
                searchService.search(saved.getNql());
            
            long duration = 
                System.currentTimeMillis() - startTime;
            
            // 통계 업데이트
            saved.setExecutionCount(
                saved.getExecutionCount() + 1
            );
            saved.setAvgResponseTimeMs(
                (saved.getAvgResponseTimeMs() * 
                    (saved.getExecutionCount() - 1) +
                 duration) / 
                saved.getExecutionCount()
            );
            
            repository.save(saved);
            
            // 이벤트 발행
            eventPublisher.publish(
                QueryExecutionEvent.success(...)
            );
            
        } catch (Exception ex) {
            // 에러 처리
        }
    }
}
```

---

## 3. 면접 Q&A

### Q1: "왜 Cold Start와 Warm Cache를 구분해야 하나?"

**답변**:
```
Cold Start와 Warm Cache의 성능이 다르기 때문:

Cold Start (캐시 비어있음):
- 모든 계층 실행
- 응답시간: 50ms
- 신뢰도: 1회 측정 (불안정함)

Warm Cache (캐시 데이터 있음):
- 캐시에서 대부분 반환
- 응답시간: 3-5ms (히트), 50ms (미스)
- 신뢰도: 많은 요청 평균 (안정적)

실수하기 쉬운 부분:
❌ Cold Start만 보면: "캐싱 효과 미미"
✅ Warm Cache보면: "캐싱 효과 우수 (33% 개선)"

따라서:
- 성능 테스트는 "워밍업" 후 측정
- "Cold Start는 현실적이지 않음"
- 실제 사용 패턴 = Warm Cache

유추:
SQL 데이터베이스도 동일
- Buffer pool 비어있음 (콜드)
- Buffer pool 채워짐 (워밈) ← 실제
```

### Q2: "2계층 캐싱이 1계층보다 나은가?"

**답변**:
```
시나리오별 비교:

상황 1: 단일 서버 (확장 없음)
┌─────────────────────────────┐
│ L1 Caffeine만 충분          │
│ - 응답시간: < 1μs           │
│ - 구현 간단                 │
│ - Redis 오버헤드 불필요    │
└─────────────────────────────┘

상황 2: 여러 서버 (로드 밸런싱)
┌─────────────────────────────────────────┐
│ L1만 있으면 문제 발생                    │
│ 사용자 A: 서버1에서 "AI" 검색           │
│ └─ Caffeine 캐시에 저장                 │
│ 사용자 B: 서버2에서 "AI" 검색           │
│ └─ 서버2의 Caffeine에 없음              │
│ └─ ES 재검색 필요 (캐시 미스!)         │
│                                         │
│ 효과: 캐시 히트율 50% (낭비)           │
└─────────────────────────────────────────┘

상황 3: L1 + L2 (우리 설계) ✅
┌──────────────────────────────────────────────┐
│ 사용자 A: 서버1에서 "AI" 검색               │
│ ├─ Caffeine 저장                          │
│ └─ Redis 저장                             │
│                                            │
│ 사용자 B: 서버2에서 "AI" 검색               │
│ ├─ Caffeine 미스                          │
│ ├─ Redis 히트 (1-2ms) ✅                 │
│ ├─ Caffeine에도 추가                      │
│ └─ 이후 서버2에서도 < 1μs                 │
│                                            │
│ 효과: 캐시 히트율 95% (매우 효율적)     │
└──────────────────────────────────────────────┘

결론: 확장성 고려하면 L1 + L2 필수
```

### Q3: "이벤트 발행은 동기 vs 비동기 어떤 게 낫나?"

**답변**:
```
동기 이벤트:
    eventPublisher.publish(event);
    // 모든 리스너가 완료될 때까지 대기

장점:
✅ 구현 간단
✅ 순서 보장

단점:
❌ 리스너 1개 느리면 전체 응답 느려짐
❌ 리스너 Exception 발생하면 응답 실패

비동기 이벤트:
    CompletableFuture.runAsync(() -> 
        eventPublisher.publish(event),
        executorService);
    // 리스너 실행 안 기다리고 즉시 반환

장점:
✅ 리스너 성능 영향 없음
✅ 리스너 오류가 응답 영향 없음

단점:
❌ 순서 보장 안 됨
❌ 디버깅 어려움

우리 선택 (Phase 5):
    @Async
    public void onQueryExecuted(QueryExecutionEvent event) {
        // 비동기 리스너
    }

이유:
- 알림, 로깅은 응답 속도에 영향 없어야 함
- 리스너 오류로 사용자 응답 실패 막기
- 확장성 (리스너 많아도 응답 안 느려짐)
```

### Q4: "캐시 무효화는 어떻게 관리하나?"

**답변**:
```
전략 1: TTL 기반 (Simple)
    cache.put(key, value, Duration.ofMinutes(5))
    
장점:
✅ 구현 간단
✅ 일관성 자동 보장

단점:
❌ 5분 지연 가능
❌ 불필요한 갱신 (변화 없어도 재계산)

전략 2: 이벤트 기반 (Smart)
    @EventListener
    public void onNewsIngested(NewsIngestedEvent event) {
        cacheManager.evict("agg:category:" + 
            event.getCategory());
    }

장점:
✅ 즉시 무효화
✅ 필요한 것만 무효화

단점:
❌ 구현 복잡
❌ 이벤트 누락 가능

우리 접근:
    TTL + 이벤트 기반 하이브리드
    
    ├─ 자주 변하는 데이터: TTL = 1분
    ├─ 드문 데이터: TTL = 24시간
    └─ 중요한 데이터: TTL + 이벤트 무효화

현실:
- 거의 모든 캐시는 TTL 기반
- 이벤트 기반은 매우 일부만
- 99% 경우 TTL로 충분
```

---

## 정리

**Phase 4-5 핵심**:

| 항목 | 기술 | 효과 |
|-----|------|------|
| **L1 캐시** | Caffeine | < 1μs |
| **L2 캐시** | Redis | 1-2ms |
| **임베딩 캐시** | Redis 24h | 8ms → 2ms (-80%) |
| **이벤트** | Observer 패턴 | 느슨한 결합 |
| **알림** | Rule Engine | 조건부 알림 |

**인터뷰 최종 정리**:

```
"N-QL Intelligence의 기술 여정"

Phase 1: 안정성 (에러 처리)
Phase 2: 표현력 (고급 연산자)
Phase 3: 정확도 (하이브리드 검색)
Phase 4: 성능 (2-계층 캐싱)
Phase 5: 운영성 (이벤트 알림)

각 Phase는 다음 Phase의 기반이 됨
→ 설계가 중요한 이유
```

---

**작성자**: Claude Code  
**작성일**: 2026-04-23  
**난이도**: ⭐⭐⭐⭐ (고급)  
**예상 학습시간**: 60분
