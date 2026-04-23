# N-QL Intelligence 기술 심화 학습 가이드 — Series 02. Phase 1 에러 처리 & 모니터링

**최종 업데이트**: 2026-04-23  
**난이도**: ⭐⭐ (초급 ~ 중급)  
**예상 학습시간**: 40분  
**면접 예상 빈도**: ⭐⭐⭐⭐ (높음)

---

## 📚 목차

1. **Phase 1 개요 & 목표**
2. **예외 처리 전략 (@ExceptionHandler)**
3. **에러 응답 표준화 (ErrorResponse)**
4. **모니터링 아키텍처 (Prometheus + Grafana)**
5. **실전 면접 Q&A**

---

## 1. Phase 1 개요 & 목표

### 1.1 Phase 1의 역할

```
프로젝트 시작
    ↓
Phase 1: 기반 구축 🏗️
├─ 에러 처리 (안정성)
├─ 모니터링 (가시성)
└─ 성능 측정 (측정 능력)
    ↓
Phase 2-5: 기능 구현 (위의 기반 위에서)
```

**Goal**: 본격적인 기능 개발 전에 "운영 인프라" 완성

| 항목 | 목표 | 이유 |
|------|------|------|
| **에러 처리** | 100% 구조화된 응답 | 클라이언트가 에러를 프로그래밍적으로 처리 가능 |
| **모니터링** | 실시간 메트릭 | 성능 저하 조기 감지 |
| **로깅** | 구조화된 로그 | 버그 디버깅 및 감사 추적 |

---

## 2. 예외 처리 전략

### 2.1 문제 상황

**AS-IS (예외 처리 없음)**:
```java
@PostMapping("/api/query")
public ResponseEntity<?> query(@RequestBody QueryRequest req) {
    // NQL 파싱 실패 → 500 Internal Server Error
    NQLExpression expr = NQLQueryParser.parse(req.getNql());
    
    // ES 연결 실패 → 500 Internal Server Error  
    List<News> results = elasticsearch.search(expr);
    
    // 임베딩 서비스 다운 → 500 Internal Server Error
    float[] vector = embeddingClient.embed(expr.getKeywords());
    
    return ResponseEntity.ok(results);
}
```

**문제**:
```
❌ 모든 에러가 500 (Internal Server Error)
❌ 클라이언트는 어떤 에러인지 알 수 없음
❌ UI에서 적절한 메시지 표시 불가능
```

### 2.2 해결책: GlobalExceptionHandler

**아이디어**: Spring의 `@ExceptionHandler`로 중앙집중식 처리

```java
@RestControllerAdvice
public class GlobalExceptionHandler {
    
    // 1️⃣ 파싱 에러 (400 Bad Request)
    @ExceptionHandler(ParseException.class)
    public ResponseEntity<ErrorResponse> handleParseException(
        ParseException ex, 
        HttpServletRequest request) {
        
        return ResponseEntity
            .badRequest()
            .body(ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(400)
                .error("PARSE_ERROR")
                .message(ex.getMessage())
                .path(request.getRequestURI())
                .build());
    }
    
    // 2️⃣ Elasticsearch 에러 (503 Service Unavailable)
    @ExceptionHandler(ElasticsearchException.class)
    public ResponseEntity<ErrorResponse> handleElasticsearchException(
        ElasticsearchException ex, 
        HttpServletRequest request) {
        
        return ResponseEntity
            .status(503)
            .body(ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(503)
                .error("ELASTICSEARCH_ERROR")
                .message("검색 엔진이 일시적으로 사용 불가능합니다")
                .path(request.getRequestURI())
                .build());
    }
    
    // 3️⃣ 예상 불가 에러 (500 Internal Server Error)
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneralException(
        Exception ex, 
        HttpServletRequest request) {
        
        logger.error("Unexpected error: ", ex);
        
        return ResponseEntity
            .status(500)
            .body(ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(500)
                .error("INTERNAL_ERROR")
                .message("예상치 못한 오류가 발생했습니다")
                .path(request.getRequestURI())
                .build());
    }
}
```

**이점**:
```
✅ 에러마다 적절한 HTTP 상태 코드
✅ 구조화된 JSON 응답
✅ 여러 컨트롤러에서 재사용 가능
✅ 중앙에서 관리 (코드 중복 제거)
```

---

### 2.3 ErrorResponse 표준화

**설계 원칙**: REST API 표준 준수

```java
public class ErrorResponse {
    
    private LocalDateTime timestamp;  // 언제 발생했는가?
    private int status;               // HTTP 상태 코드
    private String error;             // 에러 타입 (프로그래밍)
    private String message;           // 에러 메시지 (사용자용)
    private String path;              // 어느 경로에서?
    private List<FieldError> details; // 필드별 검증 에러 (선택)
    
    // Builder pattern for flexible construction
    public static ErrorResponseBuilder builder() { ... }
}

// 예시
{
    "timestamp": "2026-04-23T10:15:30",
    "status": 400,
    "error": "PARSE_ERROR",
    "message": "유효하지 않은 NQL 쿼리: 예상치 못한 ')'",
    "path": "/api/query"
}
```

**클라이언트에서의 활용**:

```typescript
// TypeScript/JavaScript 예시
interface ErrorResponse {
    timestamp: string;
    status: number;
    error: string;
    message: string;
    path: string;
}

async function searchNews(nql: string) {
    try {
        const response = await fetch("/api/query", {
            method: "POST",
            body: JSON.stringify({ nql })
        });
        
        if (!response.ok) {
            const error: ErrorResponse = await response.json();
            
            // 에러 타입별 처리
            switch (error.error) {
                case "PARSE_ERROR":
                    showToast("쿼리 문법이 잘못되었습니다");
                    highlightSyntaxError(error.message);
                    break;
                    
                case "ELASTICSEARCH_ERROR":
                    showToast("검색 엔진이 잠시 작동하지 않습니다");
                    suggestRetry();
                    break;
                    
                default:
                    showToast("예상치 못한 오류가 발생했습니다");
            }
            return;
        }
        
        const data = await response.json();
        renderResults(data);
    } catch (err) {
        // 네트워크 오류
    }
}
```

---

### 2.4 구체적인 에러 타입 정의

```java
// 1️⃣ NQL 파싱 에러
public class NQLParseException extends RuntimeException {
    private final int line;
    private final int column;
    private final String context;
    
    public NQLParseException(
        String message, 
        int line, 
        int column, 
        String context) {
        super(message);
        this.line = line;
        this.column = column;
        this.context = context;
    }
}

// 2️⃣ Elasticsearch 연결 에러
public class ElasticsearchUnavailableException 
    extends RuntimeException {
    
    private final int retryCount;
    
    public ElasticsearchUnavailableException(
        String message, 
        int retryCount) {
        super(message);
        this.retryCount = retryCount;
    }
}

// 3️⃣ 벡터 임베딩 실패
public class EmbeddingServiceException extends RuntimeException {
    private final String keyword;
    private final long durationMs;
    
    public EmbeddingServiceException(
        String message, 
        String keyword, 
        long durationMs) {
        super(message);
        this.keyword = keyword;
        this.durationMs = durationMs;
    }
}
```

**ExceptionHandler 확장**:

```java
@RestControllerAdvice
public class GlobalExceptionHandler {
    
    @ExceptionHandler(NQLParseException.class)
    public ResponseEntity<ErrorResponse> handleNQLParseException(
        NQLParseException ex, HttpServletRequest request) {
        
        return ResponseEntity
            .badRequest()
            .body(ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(400)
                .error("NQL_PARSE_ERROR")
                .message(String.format(
                    "쿼리 문법 오류 (라인 %d, 칼럼 %d): %s",
                    ex.getLine(), 
                    ex.getColumn(), 
                    ex.getMessage()))
                .path(request.getRequestURI())
                .build());
    }
    
    @ExceptionHandler(EmbeddingServiceException.class)
    public ResponseEntity<ErrorResponse> handleEmbeddingException(
        EmbeddingServiceException ex, HttpServletRequest request) {
        
        // Graceful degradation: 임베딩 없이 BM25로 진행
        return ResponseEntity
            .ok()
            .body(SearchResponse.builder()
                .results(fallbackToBm25(ex.getKeyword()))
                .warning("벡터 임베딩 서비스가 일시적으로 "
                    + "사용 불가능합니다. BM25 검색만 수행됩니다")
                .build());
    }
}
```

---

## 3. 모니터링 아키텍처

### 3.1 모니터링이 필요한 이유

**시나리오**:
```
"우리 서비스가 느리다"는 문제 보고 받음
  ↓
어디가 느린가?
  ├─ NQL 파싱? (보통 1-2ms)
  ├─ Elasticsearch? (15-20ms)
  ├─ 벡터 임베딩? (8-10ms)
  └─ 네트워크? (2-3ms)
  
모니터링이 없으면: 추측만 할 수 있음 ❌
모니터링이 있으면: 정확한 데이터로 진단 ✅
```

### 3.2 메트릭 수집 (Micrometer + Prometheus)

**구현**:

```java
@Component
public class QueryMetrics {
    
    private final MeterRegistry meterRegistry;
    
    public QueryMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }
    
    // 1️⃣ Timer: 응답시간 측정
    public void recordQueryTime(long durationMs) {
        meterRegistry.timer("query.duration.ms")
            .record(durationMs, TimeUnit.MILLISECONDS);
    }
    
    // 2️⃣ Counter: 이벤트 횟수
    public void incrementSuccessCount() {
        meterRegistry.counter("query.success.total").increment();
    }
    
    public void incrementErrorCount(String errorType) {
        meterRegistry.counter("query.error.total", 
            "type", errorType).increment();
    }
    
    // 3️⃣ Gauge: 현재 상태
    private final AtomicInteger activeQueries = 
        new AtomicInteger(0);
    
    public void recordActiveQueries(int count) {
        meterRegistry.gauge("query.active", activeQueries);
    }
    
    // 4️⃣ 컴포넌트별 시간 측정
    public void recordParsingTime(long durationMs) {
        meterRegistry.timer("query.parsing.ms")
            .record(durationMs, TimeUnit.MILLISECONDS);
    }
    
    public void recordEmbeddingTime(long durationMs) {
        meterRegistry.timer("query.embedding.ms")
            .record(durationMs, TimeUnit.MILLISECONDS);
    }
    
    public void recordElasticsearchTime(long durationMs) {
        meterRegistry.timer("query.elasticsearch.ms")
            .record(durationMs, TimeUnit.MILLISECONDS);
    }
}
```

**Controller에서 사용**:

```java
@RestController
@RequestMapping("/api")
public class QueryController {
    
    private final QueryMetrics metrics;
    private final QueryService service;
    
    @PostMapping("/query")
    public ResponseEntity<?> query(@RequestBody QueryRequest req) {
        long startTime = System.currentTimeMillis();
        
        try {
            // 파싱 시간 측정
            long parseStart = System.currentTimeMillis();
            NQLExpression expr = NQLQueryParser.parse(req.getNql());
            metrics.recordParsingTime(System.currentTimeMillis() - parseStart);
            
            // 임베딩 시간 측정
            long embedStart = System.currentTimeMillis();
            float[] vector = embeddingClient.embed(expr);
            metrics.recordEmbeddingTime(System.currentTimeMillis() - embedStart);
            
            // ES 시간 측정
            long esStart = System.currentTimeMillis();
            NewsSearchResponse result = service.search(expr, vector);
            metrics.recordElasticsearchTime(System.currentTimeMillis() - esStart);
            
            // 전체 시간 측정
            long totalDuration = System.currentTimeMillis() - startTime;
            metrics.recordQueryTime(totalDuration);
            metrics.incrementSuccessCount();
            
            return ResponseEntity.ok(result);
            
        } catch (NQLParseException ex) {
            metrics.incrementErrorCount("parse_error");
            throw ex;
        }
    }
}
```

---

### 3.3 Prometheus 설정

**application.yml**:

```yaml
management:
  endpoints:
    web:
      exposure:
        include: prometheus, health, metrics
  
  metrics:
    export:
      prometheus:
        enabled: true
    tags:
      application: n-ql-intelligence
      version: 1.0.0
    distribution:
      percentiles-histogram:
        query.duration.ms: true
        query.parsing.ms: true
        query.embedding.ms: true
        query.elasticsearch.ms: true
```

**prometheus.yml** (Prometheus 서버 설정):

```yaml
global:
  scrape_interval: 15s
  evaluation_interval: 15s

scrape_configs:
  - job_name: 'n-ql-intelligence'
    static_configs:
      - targets: ['localhost:8080']
    metrics_path: '/actuator/prometheus'
```

**메트릭 확인**:

```bash
# 로컬 확인
curl http://localhost:8080/actuator/prometheus

# 출력 예시
# HELP query_duration_ms_seconds_max  
# TYPE query_duration_ms_seconds_max gauge
query_duration_ms_seconds_max 0.110  # 최대: 110ms

# HELP query_duration_ms_seconds  
# TYPE query_duration_ms_seconds summary
query_duration_ms_seconds_sum 12.5   # 합계: 12.5초
query_duration_ms_seconds_count 200  # 횟수: 200회

# 평균: 12.5 / 200 = 62.5ms
```

---

### 3.4 Grafana 대시보드

**Grafana 대시보드 쿼리**:

```promql
// 1️⃣ 평균 응답시간 (지난 1시간)
rate(query_duration_ms_seconds_sum[1h]) / 
rate(query_duration_ms_seconds_count[1h])

// 2️⃣ P95 응답시간
histogram_quantile(0.95, query_duration_ms_seconds_bucket)

// 3️⃣ 초당 요청 수 (QPS)
rate(query_success_total[1m])

// 4️⃣ 에러율
rate(query_error_total[1m]) / 
(rate(query_success_total[1m]) + rate(query_error_total[1m]))

// 5️⃣ 컴포넌트별 시간 비교
{
    parsing: rate(query_parsing_ms_seconds_sum[1m]) / 
            rate(query_parsing_ms_seconds_count[1m]),
    embedding: rate(query_embedding_ms_seconds_sum[1m]) / 
              rate(query_embedding_ms_seconds_count[1m]),
    elasticsearch: rate(query_elasticsearch_ms_seconds_sum[1m]) / 
                  rate(query_elasticsearch_ms_seconds_count[1m])
}
```

**Grafana 시각화**:

```
┌─────────────────────────────────────────────────┐
│          N-QL Intelligence 대시보드               │
├─────────────────────────────────────────────────┤
│                                                 │
│  평균 응답시간: 62.5ms    ↓ 15% (지난 주 대비)   │
│  P95 응답시간: 110ms      ↑ 5% (추이)           │
│  성공률: 99.8%             ↓ 0.1%               │
│  QPS: 150 req/s           ↑ 20 req/s           │
│                                                 │
│  [그래프 1: 응답시간 추이]                       │
│  ms                                             │
│  100 |     *                                    │
│   80 | *  *  *                                  │
│   60 | *  *  *  * *                             │
│   40 | *  *  *  * *  *                          │
│      └─────────────────────────────→ 시간      │
│                                                 │
│  [그래프 2: 컴포넌트별 시간]                     │
│  Parsing:         1-2ms (2%)                   │
│  Embedding:       8-10ms (15%)                 │
│  Elasticsearch:   15-20ms (30%)                │
│  Cache Hit:       1-3ms (50%)                  │
│  Network:         2-3ms (5%)                   │
│                                                 │
│  [그래프 3: 에러율]                              │
│  정상: 99.8%                                    │
│  Parse Error: 0.15%                            │
│  ES Error: 0.05%                               │
│                                                 │
└─────────────────────────────────────────────────┘
```

---

### 3.5 알림 규칙 (Alert Rules)

**prometheus-rules.yml**:

```yaml
groups:
  - name: n-ql-alerts
    interval: 30s
    rules:
      # 1️⃣ 응답시간 악화
      - alert: HighLatency
        expr: |
          histogram_quantile(0.95, 
            query_duration_ms_seconds_bucket) > 200
        for: 5m
        annotations:
          summary: "응답시간이 200ms 초과"
          description: |
            P95 응답시간: 
            {{ $value }}ms
      
      # 2️⃣ 에러율 증가
      - alert: HighErrorRate
        expr: |
          rate(query_error_total[5m]) > 0.01
        for: 2m
        annotations:
          summary: "에러율이 1% 초과"
          description: |
            현재 에러율: 
            {{ $value | humanizePercentage }}
      
      # 3️⃣ ES 서비스 다운
      - alert: ElasticsearchDown
        expr: |
          rate(query_error_total{type="elasticsearch"}[5m]) > 0.5
        for: 1m
        annotations:
          summary: "Elasticsearch 장애 가능성"
```

---

## 4. 실전 모니터링 활용

### 4.1 성능 저하 진단 (Real Case)

**상황**: "오늘 오후 2시부터 응답이 느려졌다"

**진단 단계**:

```
1️⃣ Grafana에서 전체 응답시간 확인
   └─ 2시부터 62.5ms → 150ms (2.4배 저하)

2️⃣ 각 컴포넌트 시간 확인
   ├─ Parsing: 1-2ms (변화 없음)
   ├─ Embedding: 8-10ms (변화 없음)
   ├─ Elasticsearch: 15-20ms → 80-100ms (⚠️ 급증!)
   └─ 캐싱: 1-3ms (변화 없음)

3️⃣ 결론: Elasticsearch 응답 시간 악화
   └─ 원인 추정:
      - 인덱스 크기 증가?
      - 샤드 균형 문제?
      - GC pause?

4️⃣ 해결책
   ├─ ES 인덱스 최적화
   ├─ 샤드 리밸런싱
   └─ JVM 튜닝
```

---

### 4.2 메트릭 기반 의사결정

**예시: 캐싱 효과 측정**

```
Before 캐싱:
┌─────────────────────┐
│ 응답시간: 62.5ms    │
└─────────────────────┘

After 캐싱:
┌─────────────────────┐
│ 캐시 히트율: 50%    │
│ - Hit: 3ms          │
│ - Miss: 100ms       │
│ 평균: 3×0.5 + 100×0.5 = 51.5ms |
│ 개선: -18% ✅        │
└─────────────────────┘

의사결정: 캐싱 효과 입증 → Phase 4로 확대
```

---

## 5. 면접 대비 Q&A

### Q1: "@ExceptionHandler는 왜 필요한가?"

**좋은 답변**:
```
1️⃣ 문제 상황
   - 예외가 발생하면 Spring이 기본 500 에러 응답
   - 모든 예외가 같은 형식 (구조화되지 않음)
   - 클라이언트가 에러 원인을 알 수 없음

2️⃣ 우리 해결책
   - @ExceptionHandler로 예외 타입별 처리
   - 각 예외에 맞는 HTTP 상태 코드 반환
   - 구조화된 JSON ErrorResponse 제공

3️⃣ 예시
   NQLParseException → 400 Bad Request
   ElasticsearchUnavailable → 503 Service Unavailable
   Unexpected Exception → 500 Internal Server Error

4️⃣ 효과
   - 클라이언트가 프로그래밍적으로 처리 가능
   - UI에서 적절한 메시지 표시 가능
   - 에러 로깅 중앙화

5️⃣ 추가: Graceful Degradation
   - 임베딩 실패 → BM25만으로 계속 (완전 실패 아님)
   - 부분 장애 처리 (전체 장애로 확산 방지)
```

### Q2: "왜 3가지 메트릭 타입 (Timer, Counter, Gauge)을 모두 사용하나?"

**답변**:
```
각 메트릭의 용도:

1️⃣ Timer (응답시간)
   - 목적: "쿼리가 얼마나 오래 걸리나?"
   - 측정: min, max, avg, p95, p99
   - 활용: 성능 악화 감지
   
2️⃣ Counter (누적 횟수)
   - 목적: "성공/실패 몇 건인가?"
   - 측정: 누적 값만 증가
   - 활용: 에러율 계산 (error_count / total_count)
   
3️⃣ Gauge (현재 상태)
   - 목적: "지금 얼마나 많은 요청이 처리 중인가?"
   - 측정: 증가/감소 모두 가능
   - 활용: 병목 구간 감지
   
예를 들어:
- Timer로 "응답시간 증가" 감지
- Counter로 "에러율 증가" 확인
- Gauge로 "동시 요청 급증" 발견
  → 이 셋을 함께 보면 전체 그림이 그려짐

추가: SLA 검증
- SLA: "P95 < 500ms"
- Timer histogram으로 P95 계산
- Grafana alert로 위반 감지
```

### Q3: "캐싱이 없다면 모니터링 메트릭이 어떻게 달라질까?"

**답변**:
```
캐싱 전:
- 모든 요청이 ES + 임베딩 전체 경로 실행
- 응답시간: 30-50ms (항상 길음)
- 메트릭이 비교적 안정적 (변화 적음)
- 성능 개선 여지 불명확

캐싱 후:
- 반복 요청은 캐시에서 반환 (< 5ms)
- 새 요청만 ES 경로 실행 (30-50ms)
- 응답시간: 평균 15-25ms (분포 넓음)
- 메트릭 분석이 중요해짐

메트릭 해석:
Before: 평균 40ms (일정함)
After:  평균 20ms (하지만 분포 넓음)
        - P50: 3ms (캐시 히트)
        - P95: 45ms (새 요청)

→ P50과 P95가 다르면 캐싱이 잘 작동한다는 증거
→ 캐시 히트율 메트릭 추가 필요
```

### Q4: "Prometheus 메트릭 이름을 어떻게 정했나?"

**답변**:
```
메트릭 이름 설계 원칙:

1️⃣ 명사 + 단위
   ❌ query_time        (단위 모호)
   ✅ query_duration_ms (ms가 명확)

2️⃣ 카테고리_항목_단위
   ✅ query_duration_ms (쿼리 지속시간)
   ✅ query_error_total (쿼리 에러 누적)
   ✅ query_active      (활성 쿼리)

3️⃣ _total suffix
   - Counter는 항상 _total 붙임
   - 누적값임을 명확히
   - rate() 함수와 호환

4️⃣ 라벨 (tags)
   query_error_total{type="parse"}        # 파싱 에러
   query_error_total{type="elasticsearch"} # ES 에러
   
   → 같은 메트릭이지만 타입별로 분류 가능

5️⃣ Prometheus 명명 규칙
   https://prometheus.io/docs/practices/naming/
   - 뱀_문자 사용
   - 단위는 suffix로
   - 접두사로 조직화
```

---

## 정리

Phase 1의 핵심:

| 항목 | 목표 | 구현 |
|-----|------|------|
| **에러 처리** | 구조화된 응답 | @ExceptionHandler + ErrorResponse |
| **모니터링** | 성능 가시성 | Micrometer + Prometheus |
| **대시보드** | 의사결정 지원 | Grafana |

**다음 단계**: Series 03에서 Phase 2 고급 연산자 구현

---

**작성자**: Claude Code  
**작성일**: 2026-04-23  
**난이도**: ⭐⭐ (초급 ~ 중급)  
**예상 학습시간**: 40분
