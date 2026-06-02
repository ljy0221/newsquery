# Phase 1-2: 오류 처리와 모니터링 실제 구현하기

> **시리즈:** N-QL Intelligence (2/10) | **난이도:** ⭐⭐⭐ | **읽는 시간:** 12분

---

## 소개

**Phase 1-1**에서 이론을 배웠습니다. 이제 실제로 구현합니다.

**학습 목표:**
1. ✅ GlobalExceptionHandler로 모든 예외를 일관되게 처리
2. ✅ ANTLR4 파싱 오류 감지 및 명확한 메시지 전달
3. ✅ Elasticsearch 연결 실패 시 자동 재시도
4. ✅ 임베딩 서비스 실패 시 BM25로 자동 전환 (Graceful Degradation)
5. ✅ Prometheus 메트릭으로 문제를 미리 감지

---

## Part 1: 전역 예외 처리기 (GlobalExceptionHandler)

### 1-1. 예외 클래스 정의

```java
// src/main/java/com/newsquery/exception/NQLParsingException.java
public class NQLParsingException extends RuntimeException {
    private final int line;
    private final int column;
    
    public NQLParsingException(String message) {
        super(message);
        this.line = -1;
        this.column = -1;
    }
    
    public NQLParsingException(String message, int line, int column) {
        super(message);
        this.line = line;
        this.column = column;
    }
    
    public int getLine() { return line; }
    public int getColumn() { return column; }
}

// src/main/java/com/newsquery/exception/SearchException.java
public class SearchException extends RuntimeException {
    public SearchException(String message) {
        super(message);
    }
    
    public SearchException(String message, Throwable cause) {
        super(message, cause);
    }
}

// src/main/java/com/newsquery/exception/EmbeddingException.java
public class EmbeddingException extends RuntimeException {
    public EmbeddingException(String message) {
        super(message);
    }
}
```

### 1-2. 응답 DTO

```java
// src/main/java/com/newsquery/api/ErrorResponse.java
public record ErrorResponse(
    String code,              // "INVALID_QUERY", "SEARCH_FAILED", etc.
    String message,           // 사용자 메시지
    String details,           // 개발자용 상세 정보
    int status,              // HTTP 상태 코드
    long timestamp           // 발생 시간
) {
    public static ErrorResponse of(String code, String message, int status) {
        return new ErrorResponse(code, message, null, status, System.currentTimeMillis());
    }
    
    public static ErrorResponse of(String code, String message, String details, int status) {
        return new ErrorResponse(code, message, details, status, System.currentTimeMillis());
    }
}
```

### 1-3. GlobalExceptionHandler 구현

```java
// src/main/java/com/newsquery/api/GlobalExceptionHandler.java
@RestControllerAdvice
public class GlobalExceptionHandler {
    
    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    
    // 1. NQL 파싱 오류 (400 Bad Request)
    @ExceptionHandler(NQLParsingException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handleNQLParsingException(NQLParsingException ex) {
        
        String details = ex.getLine() > 0 
            ? String.format("Line %d, Column %d", ex.getLine(), ex.getColumn())
            : null;
        
        logger.warn("NQL parsing error: {}", ex.getMessage());
        
        return ErrorResponse.of(
            "INVALID_QUERY",
            "쿼리 문법이 올바르지 않습니다. " + ex.getMessage(),
            details,
            HttpStatus.BAD_REQUEST.value()
        );
    }
    
    // 2. 검색 오류 (503 Service Unavailable)
    @ExceptionHandler(SearchException.class)
    @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
    public ErrorResponse handleSearchException(SearchException ex) {
        
        logger.error("Search service error: {}", ex.getMessage(), ex);
        
        return ErrorResponse.of(
            "SEARCH_FAILED",
            "검색 서비스가 일시적으로 사용 불가능합니다. 잠시 후 다시 시도하세요.",
            ex.getMessage(),
            HttpStatus.SERVICE_UNAVAILABLE.value()
        );
    }
    
    // 3. 예기치 않은 오류 (500 Internal Server Error)
    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ErrorResponse handleUnexpectedException(Exception ex) {
        
        logger.error("Unexpected error: {}", ex.getMessage(), ex);
        
        return ErrorResponse.of(
            "INTERNAL_ERROR",
            "내부 서버 오류가 발생했습니다.",
            ex.getClass().getSimpleName() + ": " + ex.getMessage(),
            HttpStatus.INTERNAL_SERVER_ERROR.value()
        );
    }
}
```

---

## Part 2: ANTLR4 파싱 오류 처리

### 2-1. 커스텀 오류 리스너

```java
// src/main/java/com/newsquery/nql/NQLErrorListener.java
public class NQLErrorListener extends BaseErrorListener {
    
    private final List<ParseError> errors = new ArrayList<>();
    
    @Override
    public void syntaxError(
        Recognizer<?, ?> recognizer,
        Object offendingSymbol,
        int line,
        int charPositionInLine,
        String msg,
        RecognitionException e
    ) {
        errors.add(new ParseError(line, charPositionInLine, msg));
    }
    
    public boolean hasErrors() {
        return !errors.isEmpty();
    }
    
    public String getErrorMessage() {
        return errors.stream()
            .map(err -> String.format(
                "Line %d, Column %d: %s",
                err.line, err.column, err.message
            ))
            .collect(Collectors.joining("\n"));
    }
    
    private record ParseError(int line, int column, String message) {}
}
```

### 2-2. 파서에 오류 리스너 적용

```java
// src/main/java/com/newsquery/nql/NQLQueryParser.java
@Service
public class NQLQueryParser {
    
    public NQLExpression parseToExpression(String queryString) 
            throws NQLParsingException {
        
        try {
            ANTLRInputStream input = new ANTLRInputStream(queryString);
            NQLLexer lexer = new NQLLexer(input);
            CommonTokenStream tokens = new CommonTokenStream(lexer);
            NQLParser parser = new NQLParser(tokens);
            
            // 1. 기본 오류 리스너 제거
            parser.removeErrorListeners();
            
            // 2. 커스텀 오류 리스너 추가
            NQLErrorListener errorListener = new NQLErrorListener();
            parser.addErrorListener(errorListener);
            
            // 3. 파싱 시작
            NQLParser.QueryContext ctx = parser.query();
            
            // 4. 오류 확인
            if (errorListener.hasErrors()) {
                throw new NQLParsingException(
                    "Failed to parse NQL query:\n" + errorListener.getErrorMessage(),
                    errorListener.errors.get(0).line,
                    errorListener.errors.get(0).column
                );
            }
            
            // 5. AST → IR 변환
            return new NQLVisitorImpl().visit(ctx);
            
        } catch (NQLParsingException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new NQLParsingException(
                "Unexpected error during parsing: " + ex.getMessage()
            );
        }
    }
}
```

---

## Part 3: Elasticsearch 재시도 로직

### 3-1. 기본 재시도

```java
// src/main/java/com/newsquery/search/NewsSearchService.java
@Service
public class NewsSearchService {
    
    private static final Logger logger = LoggerFactory.getLogger(NewsSearchService.class);
    private static final int MAX_RETRIES = 3;
    private static final long RETRY_DELAY_MS = 500;
    
    private final ElasticsearchClient esClient;
    
    public NewsSearchResponse searchWithRRF(
        NQLExpression expr, 
        int page, 
        int size
    ) throws SearchException {
        
        int attempt = 0;
        long lastDelay = 100;  // 시작: 100ms
        
        while (attempt < MAX_RETRIES) {
            try {
                attempt++;
                logger.debug("Search attempt {}/{}", attempt, MAX_RETRIES);
                
                return performSearch(expr, page, size);
                
            } catch (IOException | ElasticsearchException ex) {
                
                if (attempt < MAX_RETRIES) {
                    // Exponential Backoff: 100ms → 200ms → 400ms
                    long waitTime = lastDelay;
                    lastDelay *= 2;
                    
                    logger.warn(
                        "Search failed (attempt {}/{}), retrying in {}ms",
                        attempt, MAX_RETRIES, waitTime
                    );
                    
                    try {
                        Thread.sleep(waitTime);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new SearchException("Search interrupted", ie);
                    }
                } else {
                    // 모든 재시도 실패
                    logger.error(
                        "Search failed after {} attempts: {}",
                        MAX_RETRIES,
                        ex.getMessage()
                    );
                    throw new SearchException(
                        "Elasticsearch request failed after retries",
                        ex
                    );
                }
            }
        }
        
        throw new SearchException("Search failed unexpectedly");
    }
    
    private NewsSearchResponse performSearch(NQLExpression expr, int page, int size) 
            throws IOException {
        // 실제 검색 로직
        // ...
    }
}
```

### 3-2. 테스트 코드

```java
// src/test/java/com/newsquery/search/RetryTest.java
@Test
void testRetryLogic() throws Exception {
    // Mock Elasticsearch를 준비: 첫 시도는 실패, 두 번째는 성공
    ElasticsearchClient mockClient = mock(ElasticsearchClient.class);
    
    when(mockClient.search(any()))
        .thenThrow(new IOException("Connection refused"))  // 첫 시도
        .thenReturn(createMockResponse());                  // 두 번째 성공
    
    NewsSearchService service = new NewsSearchService(mockClient);
    
    // 실행 (재시도가 작동해야 함)
    long startTime = System.currentTimeMillis();
    NewsSearchResponse result = service.searchWithRRF(expr, 1, 20);
    long duration = System.currentTimeMillis() - startTime;
    
    // 검증
    assertNotNull(result);
    assertTrue(duration >= 100);  // 최소 대기 시간
}
```

---

## Part 4: Graceful Degradation

### 4-1. 임베딩 서비스 실패 처리

```java
// src/main/java/com/newsquery/embedding/EmbeddingClient.java
@Service
public class EmbeddingClient {
    
    private static final Logger logger = LoggerFactory.getLogger(EmbeddingClient.class);
    private final RestTemplate restTemplate;
    
    @Value("${embedding.service.url:http://localhost:8000}")
    private String embeddingServiceUrl;
    
    // null을 반환 → graceful degradation
    public float[] embed(String text) {
        try {
            EmbedResponse response = restTemplate.postForObject(
                embeddingServiceUrl + "/embed/single",
                new EmbedRequest(text),
                EmbedResponse.class,
                Duration.ofSeconds(5)
            );
            
            return response.getEmbedding();
            
        } catch (RestClientException ex) {
            logger.warn(
                "Embedding service unavailable: {} ({})",
                ex.getClass().getSimpleName(),
                ex.getMessage()
            );
            return null;  // ← graceful degradation
        }
    }
}

// Request/Response DTO
record EmbedRequest(String text) {}
record EmbedResponse(float[] embedding) {}
```

### 4-2. 검색에서 활용

```java
// src/main/java/com/newsquery/search/NewsSearchService.java
public NewsSearchResponse searchWithRRF(NQLExpression expr, int page, int size) {
    
    // 1. 키워드 추출
    List<String> keywords = KeywordExtractor.extract(expr);
    
    // 2. 임베딩 시도
    float[] vector = null;
    if (!keywords.isEmpty()) {
        vector = embeddingClient.embed(String.join(" ", keywords));
    }
    
    // 3. 벡터가 없으면 BM25만 사용
    if (vector == null) {
        logger.info("Using BM25-only search (embedding unavailable)");
        return searchWithoutVector(expr, page, size);
    }
    
    // 4. 정상: 하이브리드 검색
    return searchWithBoth(expr, vector, page, size);
}
```

---

## Part 5: Prometheus 메트릭 수집

### 5-1. 메트릭 정의

```java
// src/main/java/com/newsquery/metrics/QueryMetrics.java
@Component
public class QueryMetrics {
    
    private final MeterRegistry meterRegistry;
    
    // 응답 시간
    private final Timer queryTimer;
    
    // 오류 횟수
    private final Counter parsingErrorCounter;
    private final Counter searchErrorCounter;
    private final Counter embeddingErrorCounter;
    
    // 성공률
    private final AtomicInteger successCount = new AtomicInteger(0);
    private final AtomicInteger totalCount = new AtomicInteger(0);
    
    public QueryMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
        
        this.queryTimer = Timer.builder("nql.query.duration")
            .description("NQL query execution time")
            .publishPercentiles(0.5, 0.95, 0.99)
            .register(meterRegistry);
        
        this.parsingErrorCounter = Counter.builder("nql.parsing.errors")
            .description("Count of NQL parsing errors")
            .register(meterRegistry);
        
        this.searchErrorCounter = Counter.builder("elasticsearch.search.errors")
            .description("Count of Elasticsearch search errors")
            .register(meterRegistry);
        
        this.embeddingErrorCounter = Counter.builder("embedding.errors")
            .description("Count of embedding service errors")
            .register(meterRegistry);
        
        // 성공률 게이지
        meterRegistry.gauge(
            "query.success.rate",
            () -> totalCount.get() > 0 
                ? (double) successCount.get() / totalCount.get() 
                : 0.0
        );
    }
    
    public void recordQueryTime(long durationMs) {
        queryTimer.record(durationMs, TimeUnit.MILLISECONDS);
    }
    
    public void recordSuccess() {
        successCount.incrementAndGet();
        totalCount.incrementAndGet();
    }
    
    public void recordFailure() {
        totalCount.incrementAndGet();
    }
    
    public void recordParsingError() {
        parsingErrorCounter.increment();
    }
    
    public void recordSearchError() {
        searchErrorCounter.increment();
    }
    
    public void recordEmbeddingError() {
        embeddingErrorCounter.increment();
    }
}
```

### 5-2. Controller에서 사용

```java
// src/main/java/com/newsquery/api/QueryController.java
@RestController
@RequestMapping("/api/query")
public class QueryController {
    
    private final QueryMetrics metrics;
    private final NQLQueryParser parser;
    private final NewsSearchService searchService;
    
    @PostMapping
    public ResponseEntity<NewsSearchResponse> query(
        @RequestBody QueryRequest request
    ) {
        long startTime = System.currentTimeMillis();
        
        try {
            // 파싱
            NQLExpression expr = parser.parseToExpression(request.getQuery());
            
            // 검색
            NewsSearchResponse response = searchService.searchWithRRF(
                expr,
                request.getPage(),
                request.getSize()
            );
            
            // 메트릭 기록
            long duration = System.currentTimeMillis() - startTime;
            metrics.recordQueryTime(duration);
            metrics.recordSuccess();
            
            return ResponseEntity.ok(response);
            
        } catch (NQLParsingException ex) {
            metrics.recordParsingError();
            metrics.recordFailure();
            throw ex;
        } catch (SearchException ex) {
            metrics.recordSearchError();
            metrics.recordFailure();
            throw ex;
        }
    }
}
```

### 5-3. Prometheus 설정

```yaml
# application.yml
management:
  endpoints:
    web:
      exposure:
        include: metrics, prometheus
  metrics:
    tags:
      application: newsquery
    export:
      prometheus:
        step: 1m
```

### 5-4. Prometheus 쿼리

```promql
# 평균 응답 시간 (지난 5분)
rate(nql_query_duration_sum[5m]) / rate(nql_query_duration_count[5m])

# 오류율 (%)
(rate(nql_parsing_errors_total[5m]) + 
 rate(elasticsearch_search_errors_total[5m]) +
 rate(embedding_errors_total[5m])) 
 / rate(query_duration_count[5m]) * 100

# 성공률
query_success_rate
```

---

## Part 6: 통합 테스트

```java
// src/test/java/com/newsquery/api/ErrorHandlingIntegrationTest.java
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ErrorHandlingIntegrationTest {
    
    @Autowired
    private TestRestTemplate restTemplate;
    
    @Test
    void testNQLParsingError() {
        // 잘못된 쿼리
        QueryRequest request = new QueryRequest(
            "keyword(\"AI\") AND sentiment = INVALID",
            1,
            20
        );
        
        // 요청
        ResponseEntity<ErrorResponse> response = restTemplate.postForEntity(
            "/api/query",
            request,
            ErrorResponse.class
        );
        
        // 검증
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("INVALID_QUERY", response.getBody().code());
        assertTrue(response.getBody().message().contains("문법"));
        assertNotNull(response.getBody().details());
    }
    
    @Test
    void testSearchServiceError() {
        // Mock: Elasticsearch 다운
        when(elasticsearchClient.search(any()))
            .thenThrow(new IOException("Connection refused"));
        
        // 요청
        ResponseEntity<ErrorResponse> response = restTemplate.postForEntity(
            "/api/query",
            new QueryRequest("keyword(\"AI\")", 1, 20),
            ErrorResponse.class
        );
        
        // 검증 (3회 재시도 후 실패)
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, response.getStatusCode());
        assertEquals("SEARCH_FAILED", response.getBody().code());
    }
}
```

---

## Part 7: 모니터링 대시보드

### 7-1. Grafana 대시보드

```json
{
  "panels": [
    {
      "title": "Query Response Time",
      "targets": [
        {
          "expr": "nql_query_duration"
        }
      ]
    },
    {
      "title": "Error Rate (%)",
      "targets": [
        {
          "expr": "(rate(errors_total[5m]) / rate(requests_total[5m])) * 100"
        }
      ]
    },
    {
      "title": "Success Rate (%)",
      "targets": [
        {
          "expr": "query_success_rate * 100"
        }
      ]
    }
  ]
}
```

---

## Phase 1-2 실전 요약

| 항목 | 구현 | 효과 |
|------|------|------|
| GlobalExceptionHandler | 전역 예외 처리 | 일관된 오류 응답 |
| ANTLR 오류 리스너 | 명확한 파싱 오류 메시지 | 사용자가 오류 수정 가능 |
| 재시도 로직 | Exponential Backoff | 일시적 오류 자동 복구 |
| Graceful Degradation | 임베딩 실패 → BM25 전환 | 부분 서비스 계속 |
| Prometheus 메트릭 | 응답 시간, 오류율 추적 | 문제 사전 감지 |

---

## 다음 편 예고

**Phase 2-1: 고급 연산자의 이론**

ANTLR4 문법 설계와 sealed interface의 아름다움:
- 📌 언어 설계의 원칙
- 📌 Context-Free Grammar (CFG)
- 📌 Sealed Interface 패턴
- 📌 Visitor 패턴의 장점

---

**실습을 통해 배웠나요?** 💬 댓글로 피드백을 남겨주세요!

