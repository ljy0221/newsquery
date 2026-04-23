# N-QL Intelligence 기술 심화 학습 가이드 — Series 01. 아키텍처 설계

**최종 업데이트**: 2026-04-23  
**난이도**: ⭐⭐⭐ (중급 ~ 고급)  
**예상 학습시간**: 45분  
**면접 예상 빈도**: ⭐⭐⭐⭐⭐ (매우 높음)

---

## 📚 목차

1. **프로젝트 개요 & 기술 선택 이유**
2. **NQL 처리 파이프라인 아키텍처**
3. **데이터 흐름 설계**
4. **기술 선택 검증**
5. **면접 대비 Q&A**

---

## 1. 프로젝트 개요 & 기술 선택 이유

### 1.1 What: N-QL Intelligence란?

**정의**: JQL(Jira Query Language) 스타일의 NQL(News Query Language)을 통해 뉴스를 검색하는 전문가용 엔진

**문제 정의**: 기존 검색 엔진의 한계
```
❌ 문제 1: Elasticsearch의 Query DSL은 복잡하고 배우기 어려움
❌ 문제 2: 자연어 검색은 정확도가 떨어짐
❌ 문제 3: 키워드 + 벡터 하이브리드 검색을 동시에 지원하기 어려움
```

**솔루션**: NQL 언어 + RRF 하이브리드 검색
```
✅ 해결책 1: SQL 같은 직관적 문법으로 표현 가능
   예: keyword("AI") AND sentiment != "negative" AND source IN ["Reuters"]
   
✅ 해결책 2: Elasticsearch Query DSL로 자동 변환
   (사용자가 DSL을 몰라도 됨)
   
✅ 해결책 3: BM25 + 벡터 유사도를 RRF로 통합
   (최고 품질의 검색 결과)
```

---

### 1.2 Why: 각 기술 선택의 이유

#### 🎯 Backend: Java 17 + Spring Boot 3.x

| 선택 | 이유 | 대안 | 우리가 선택한 이유 |
|------|------|------|------------------|
| **Java 17** | 패턴 매칭, Record | Python, Go | 1. Pattern matching으로 sealed interface 지원 2. 엔터프라이즈 성숙도 |
| **Spring Boot 3.x** | 의존성 주입, 자동 설정 | Spring 직접, 수동 DI | 1. 빠른 개발 속도 2. 프로덕션 레디 기능 제공 |

**깊이 있는 설명**:

```java
// Java 17의 sealed interface 패턴 매칭 사용
public sealed interface NQLExpression {
    // 하위 구현을 제한 (컴파일 타임 검증)
}

final class KeywordExpr implements NQLExpression { ... }
final class RangeExpr implements NQLExpression { ... }

// instanceof 패턴 매칭으로 type-safe 코드
if (expr instanceof KeywordExpr k) {
    // k.keyword()를 바로 사용 (캐스팅 불필요)
}
```

**이 설계의 장점**:
- ✅ Compiler가 모든 경우를 검증 (Exhaustiveness check)
- ✅ Runtime 타입 캐스팅 제거 → 성능 향상
- ✅ 버그 가능성 감소

---

#### 🎯 문법 파싱: ANTLR4 (vs Regex, 수동 파서)

| 선택 | 단순성 | 확장성 | 유지보수성 | 성능 |
|------|--------|---------|-----------|------|
| **정규표현식** | ⭐⭐⭐⭐⭐ | ⭐ | ⭐⭐ | ⭐⭐⭐⭐ |
| **수동 파서** | ⭐⭐ | ⭐⭐⭐ | ⭐⭐ | ⭐⭐⭐ |
| **ANTLR4** ✅ | ⭐⭐⭐ | ⭐⭐⭐⭐⭐ | ⭐⭐⭐⭐ | ⭐⭐⭐⭐ |

**ANTLR4를 선택한 이유**:

```
초기: keyword("AI") 만 지원
    └─ Regex로도 가능
    
Phase 2: BETWEEN, CONTAINS, LIKE 추가
    └─ Regex가 복잡해지기 시작
    
Phase 3: GROUP BY, BOOST, 집계 추가
    └─ Regex로는 거의 불가능
    
Phase 5: 다양한 필터 추가
    └─ ANTLR4의 가치 극대화
```

**ANTLR4 architecture**:
```
NQL.g4 (문법 정의)
  ├─ Lexer (토큰화)
  │   keyword("AI") → [KEYWORD, LPAREN, STRING, RPAREN]
  │
  └─ Parser (AST 생성)
      └─ NQLVisitorImpl (AST → IR 변환)
          └─ sealed interface로 타입 안전성 보장
```

---

#### 🎯 검색 엔진: Elasticsearch 8.x

| 선택 | 텍스트 검색 | 벡터 검색 | 집계 | 확장성 |
|------|-----------|---------|------|--------|
| **Elasticsearch** ✅ | ⭐⭐⭐⭐⭐ | ⭐⭐⭐⭐ | ⭐⭐⭐⭐⭐ | ⭐⭐⭐⭐⭐ |
| **PostgreSQL** | ⭐⭐⭐ | ⭐⭐ | ⭐⭐⭐ | ⭐⭐⭐ |
| **Milvus** | ⭐ | ⭐⭐⭐⭐⭐ | ⭐ | ⭐⭐⭐ |

**Elasticsearch 8.x의 3가지 장점**:

```java
// 1️⃣ BM25 전문 검색 (매우 효율적)
{
  "query": {
    "match": {
      "content": {
        "query": "AI",
        "boost": 2.0
      }
    }
  }
}

// 2️⃣ kNN 벡터 검색 (8.0+)
{
  "query": {
    "knn": {
      "content_vector": {
        "vector": [0.1, 0.2, ...],
        "k": 100
      }
    }
  }
}

// 3️⃣ bool query로 복잡한 조건 조합
{
  "query": {
    "bool": {
      "must": [...],      // AND
      "should": [...],    // OR
      "must_not": [...]   // NOT
    }
  }
}
```

**선택 결과**:
- ✅ 텍스트 + 벡터 하이브리드 검색 동시 지원
- ✅ 복잡한 쿼리 표현 가능
- ✅ 대규모 데이터 처리 (수억 건 규모)

---

#### 🎯 벡터 임베딩: FastAPI + all-MiniLM-L6-v2

| 선택 | 속도 | 품질 | 리소스 | 배포 난이도 |
|------|------|------|--------|-----------|
| **FastAPI + sentence-transformers** ✅ | ⭐⭐⭐⭐ | ⭐⭐⭐⭐ | ⭐⭐⭐⭐ | ⭐⭐⭐ |
| **OpenAI API** | ⭐⭐⭐⭐⭐ | ⭐⭐⭐⭐⭐ | N/A | ⭐⭐⭐⭐⭐ |
| **Local LLM (LLaMA)** | ⭐⭐⭐ | ⭐⭐⭐⭐ | ⭐ | ⭐⭐ |

**왜 all-MiniLM-L6-v2인가**?

```
모델 선택 기준:
1. 속도: 한 문장 임베딩 < 50ms (가능)
2. 품질: semantic 유사도 87% 이상 (충분)
3. 크기: 380MB (배포 용이)
4. 차원: 384 (메모리 절감)

대안 분석:
- OpenAI (text-embedding-3-small)
  ✅ 최고 품질
  ❌ API 호출 비용 + 지연
  ❌ 외부 의존성
  
- LLaMA 70B
  ✅ 최고 품질
  ❌ GPU 필요 (고비용)
  ❌ 배포 복잡
  
- all-MiniLM-L6-v2 ✅
  ✅ 빠름 (50ms 이내)
  ✅ 충분히 좋음 (87%)
  ✅ CPU로 충분
  ✅ 배포 간단
```

**선택 결과**: 비용-성능 최적점

---

#### 🎯 캐싱: Redis (L2) + Caffeine (L1)

**2-계층 캐싱 설계**:

```
┌─────────────────────────────────┐
│   Fast Lookup (< 10 μs)         │
│  Caffeine (Local In-Memory)     │
│  - TTL: 5분                      │
│  - 최대: 10,000 항목             │
└──────────────┬──────────────────┘
               │ (Cache Miss)
┌──────────────▼──────────────────┐
│  Medium Lookup (< 10ms)         │
│    Redis (Distributed)          │
│  - TTL: 24시간 (임베딩)           │
│  - TTL: 5분 (GROUP BY)          │
└──────────────┬──────────────────┘
               │ (Cache Miss)
┌──────────────▼──────────────────┐
│  Slow Lookup (> 20ms)           │
│  Elasticsearch + Embedding      │
└─────────────────────────────────┘
```

**각 계층의 이유**:

| 계층 | 왜? | 언제 사용? |
|------|-----|----------|
| **Caffeine** | 네트워크 없음, 초고속 | 같은 서버에서 반복되는 쿼리 |
| **Redis** | 여러 서버 간 공유 | 모든 서버에서 공통적으로 사용할 캐시 |
| **ES + Embedding** | 정확한 데이터 | 캐시 미스 시 디폴트 |

---

## 2. NQL 처리 파이프라인 아키텍처

### 2.1 End-to-End 흐름

```
사용자 입력 (NQL 쿼리)
    │
    ├─────────────────────────────────────────────────┐
    │                                                 │
    ▼                                                 ▼
[1. 파싱 계층]                                    [Redis 확인]
- ANTLR4 Lexer/Parser                           (캐시 L2)
- AST 생성                                        │
- NQLVisitorImpl (AST → IR)                      Hit? → 응답
    │                                             │
    ▼                                             Miss
[2. 변환 계층]
- ESQueryBuilder (IR → bool query)
- KeywordExtractor (벡터용 키워드 추출)
    │
    ├─────────────────────────────────────────────────┐
    │                                                 │
    ▼                                                 ▼
[3. 임베딩 계층]                                 [Redis 확인]
- EmbeddingClient (FastAPI 호출)                (임베딩 캐시)
- 벡터 = float[384]                             │
                                                Hit? → 사용
                                                │
                                                Miss
                                                │
                                                ▼
                                            FastAPI
                                            (느림: 10ms)
                │
                ▼
    [4. RRF 스코어링]
    - RRFScorer (bool + kNN 결합)
    - rank_constant=60
    - BM25 + 벡터 점수 합산
    │
    ▼
[5. ES 검색 실행]
- Elasticsearch kNN + bool query
    │
    ▼
[6. 결과 반환]
- NewsSearchResponse
- 캐시에 저장
    │
    ▼
사용자 (JSON 응답)
```

---

### 2.2 각 계층의 역할과 설계 결정

#### 계층 1️⃣: 파싱 (ANTLR4)

**파일**: `src/main/antlr4/NQL.g4`

```antlr
// 문법 정의 (간단 예시)
expression
    : term (AND term)*
    | term (OR term)*
    | NOT term
    ;

term
    : functionCall
    | comparison
    ;

functionCall
    : IDENTIFIER LPAREN argument (COMMA argument)* RPAREN
    ;

comparison
    : field (EQ | NEQ | GT | LT | GTE | LTE) value
    ;
```

**설계 결정**: 왜 visitor 패턴인가?

```
Listener 패턴          vs      Visitor 패턴 ✅
- 트리 순회 자동           - 순회 제어 가능
- 반환값 없음              - 반환값으로 값 계산
- 파이프라인 불가능       - 파이프라인 가능 ✅

→ 우리는 AST를 IR로 변환하므로
  Visitor 패턴이 필수
```

**구현**:
```java
public class NQLVisitorImpl extends NQLBaseVisitor<NQLExpression> {
    @Override
    public NQLExpression visitFunctionCall(NQLParser.FunctionCallContext ctx) {
        String funcName = ctx.IDENTIFIER().getText();
        List<String> args = ctx.argument().stream()
            .map(NQLParser.ArgumentContext::getText)
            .collect(toList());
        
        return switch (funcName) {
            case "keyword" -> new KeywordExpr(args.get(0));
            case "sentiment" -> new SentimentExpr(args.get(0));
            // ...
            default -> throw new ParseException("Unknown function: " + funcName);
        };
    }
}
```

---

#### 계층 2️⃣: 변환 (IR → Query DSL)

**IR (Intermediate Representation)** 설계:

```java
public sealed interface NQLExpression {
    // 모든 표현식의 기반
}

// 실제 구현들
final class KeywordExpr implements NQLExpression {
    private final String keyword;
}

final class RangeExpr implements NQLExpression {
    private final String field;
    private final long min, max;
}

final class CompoundExpr implements NQLExpression {
    private final NQLExpression left, right;
    private final LogicalOp op; // AND, OR, NOT
}
```

**왜 IR 계층이 필요한가?**

```
NQL (사용자 입력)
  ↓
  └─ ANTLR4로 파싱하면 AST (Abstract Syntax Tree)
  
     ❌ AST는 매우 복잡함
        - 모든 구조 규칙 포함
        - 직접 쓰기 어려움
  
  ↓
  
NQL Expression (우리 IR)
  
  ✅ 도메인 개념으로 단순화
  ✅ 여러 곳에서 재사용 가능
     - ES Query DSL로 변환
     - KeywordExtractor로 벡터 추출
     - 쿼리 캐싱
```

**ESQueryBuilder로 변환**:

```java
public class ESQueryBuilder {
    public ObjectNode buildQuery(NQLExpression expr) {
        return switch (expr) {
            case KeywordExpr k -> buildKeywordQuery(k);
            case RangeExpr r -> buildRangeQuery(r);
            case CompoundExpr c -> buildCompoundQuery(c);
            default -> throw new IllegalArgumentException();
        };
    }
    
    private ObjectNode buildKeywordQuery(KeywordExpr k) {
        ObjectNode node = mapper.createObjectNode();
        node.put("match", mapper.createObjectNode()
            .put("content", mapper.createObjectNode()
                .put("query", k.keyword())
                .put("boost", 2.0)));
        return node;
    }
}
```

---

#### 계층 3️⃣: 임베딩 (벡터 생성)

**병목점 분석**:

```
첫 요청:  keyword("AI") 
  → EmbeddingClient.embed("AI")
  → HTTP POST localhost:8000/embed/single
  → 응답 대기: 8-10ms
  → Elasticsearch kNN 실행: 12-15ms
  → 전체: 20-25ms

2회 요청: 동일 keyword("AI")
  → Redis 캐시 확인: 1-2ms
  → 바로 Elasticsearch kNN 실행
  → 전체: 1ms (캐시 히트)
```

**캐싱 전략**:

```java
@Service
public class EmbeddingClient {
    private final RedisTemplate<String, List<Float>> redis;
    
    public List<Float> embed(String text) {
        // 1. Redis에서 확인 (24시간 TTL)
        String cacheKey = "embedding:" + hashcode(text);
        List<Float> cached = redis.opsForValue().get(cacheKey);
        
        if (cached != null) {
            return cached; // 2ms
        }
        
        // 2. FastAPI 호출 (8-10ms)
        List<Float> embedding = callFastAPI(text);
        
        // 3. Redis에 저장
        redis.opsForValue().set(cacheKey, embedding, 
            Duration.ofHours(24));
        
        return embedding;
    }
}
```

---

#### 계층 4️⃣: RRF 스코어링

**개념**: 두 검색 결과를 점수 기반 순위 융합(Reciprocal Rank Fusion)

```
쿼리: keyword("AI") 
      (BM25 + 벡터 하이브리드)

결과:

BM25 순위              벡터 순위           RRF 점수
1. Reuters (BM25=9.5) 3. Reuters (cos=0.87)
2. BBC (BM25=8.2)     1. Guardian (cos=0.92)
3. Guardian (BM25=7.1) 2. BBC (cos=0.89)

↓

RRF 점수 = 1/(60+rank_bm25) + 1/(60+rank_vector)

Reuters:   1/(60+1) + 1/(60+3) = 0.0161 + 0.0159 = 0.032  ⭐⭐⭐
BBC:       1/(60+2) + 1/(60+3) = 0.0159 + 0.0161 = 0.032  ⭐⭐⭐
Guardian:  1/(60+3) + 1/(60+1) = 0.0159 + 0.0161 = 0.032  ⭐⭐⭐

→ 최종 순위: Reuters > BBC > Guardian
   (두 방식 모두 상위 3개 고려)
```

**구현**:

```java
public class RRFScorer {
    private static final int RANK_CONSTANT = 60;
    
    public RetrieverJson buildRetriever(
        ObjectNode boolQuery,
        float[] vector) {
        
        // 1. BM25 쿼리
        ObjectNode must = boolQuery
            .get("bool")
            .get("must");
        
        // 2. kNN 쿼리
        ObjectNode knnQuery = mapper.createObjectNode()
            .putObject("knn")
            .putObject("content_vector")
            .putPOJO("vector", vector)
            .put("k", 100);
        
        // 3. RRF 결합
        ObjectNode retriever = mapper.createObjectNode()
            .putObject("rrf")
            .putObject("retrievers")
            .put(0, boolQuery)
            .put(1, knnQuery)
            .put("rank_constant", RANK_CONSTANT)
            .put("rank_window_size", 100);
        
        return new RetrieverJson(retriever);
    }
}
```

---

## 3. 데이터 흐름 설계

### 3.1 읽기 경로 (Search Path)

```
사용자 요청
   │
   ▼
QueryController.query(QueryRequest)
   │
   ├─ [1] @Cacheable 확인 (L1 Caffeine)
   │   Hit? → 응답 (< 10μs)
   │
   └─ Miss?
      │
      ├─ [2] Redis 확인 (L2)
      │   Hit? → Caffeine 업데이트 → 응답 (< 10ms)
      │
      └─ Miss?
         │
         ├─ [3] NQL 파싱 (1-2ms)
         │  - ANTLR4 Lexer/Parser
         │  - AST → NQLExpression
         │
         ├─ [4] ES 쿼리 생성 (1-2ms)
         │  - KeywordExtractor
         │  - ESQueryBuilder
         │  - bool query 생성
         │
         ├─ [5] 벡터 임베딩 (2-10ms)
         │  - EmbeddingClient
         │  - Redis 캐시 확인
         │  - FastAPI 호출 (필요시)
         │
         ├─ [6] RRF 쿼리 생성 (1-2ms)
         │  - RRFScorer
         │  - bool + kNN 결합
         │
         ├─ [7] ES 검색 (15-20ms)
         │  - Elasticsearch 실행
         │  - 결과 반환
         │
         └─ [8] 응답 + 캐싱
            - NewsSearchResponse 생성
            - Redis에 저장 (TTL: 5분)
            - Caffeine에 저장 (TTL: 5분)
            - 응답 반환
```

### 3.2 쓰기/업데이트 경로

```
데이터 수집 (Kafka)
   │
   ├─ Kafka Topic: news-raw
   │  ├─ GDELT Producer (15분마다)
   │  └─ RSS Producer (5분마다)
   │
   ▼
Python Worker
   │
   ├─ [1] Kafka Consumer
   │  - 뉴스 기사 수신
   │
   ├─ [2] 임베딩 생성
   │  - sentence-transformers 사용
   │  - 384차원 벡터
   │
   ├─ [3] Elasticsearch 색인
   │  - ES Bulk API
   │  - content_vector (dense_vector)
   │
   └─ [4] 캐시 무효화 (선택적)
      - 새 데이터 추가 시 집계 캐시 제거
```

---

## 4. 기술 선택 검증

### 4.1 성능 검증

**벤치마크 결과**:

| 쿼리 유형 | 응답시간 | 목표 | 달성도 |
|---------|---------|------|--------|
| Simple keyword | 54.23ms | < 100ms | ✅ |
| BETWEEN range | 44.40ms | < 100ms | ✅ |
| GROUP BY | 21.71ms | < 100ms | ✅ |
| RRF hybrid | 30.94ms | < 100ms | ✅ |

**캐싱 효과**:
- 임베딩 캐시: 10ms → 2ms (-80%)
- 쿼리 캐시: 30ms → 3ms (-90%)
- 집계 캐시: 40ms → 0ms (-100%)

---

### 4.2 확장성 검증

```
확장 시나리오 1: 뉴스 데이터 증가 (1M → 100M)
┌─────────────────────────────────────────────┐
│ Elasticsearch 샤딩                          │
│ - number_of_shards: 1 → 5                  │
│ - 쿼리 병렬화 가능                          │
│ - 응답시간: 30ms → 30-50ms (합리적)        │
└─────────────────────────────────────────────┘

확장 시나리오 2: 서버 증가 (1 → 100)
┌─────────────────────────────────────────────┐
│ 분산 캐싱 (Redis)                           │
│ - 모든 서버가 같은 Redis 공유              │
│ - Caffeine L1 + Redis L2 구조 유효        │
│ - 캐시 일관성 자동 보장                     │
└─────────────────────────────────────────────┘

확장 시나리오 3: 기능 추가 (NQL 연산자 확장)
┌─────────────────────────────────────────────┐
│ ANTLR4 문법 수정                           │
│ - NQL.g4 수정 (1-2시간)                     │
│ - generateGrammarSource 실행                │
│ - 파서 재생성 자동 처리                     │
│ - 기존 코드 대부분 재사용 가능             │
└─────────────────────────────────────────────┘
```

---

### 4.3 아키텍처 강점과 약점

**강점** ✅:

1. **Type Safety**: sealed interface + pattern matching
   - Compiler가 모든 경우 검증
   - 런타임 오류 가능성 최소화

2. **Extensibility**: ANTLR4 + IR 계층
   - 새로운 연산자 추가 용이
   - 기존 코드 영향 최소화

3. **Performance**: 2-계층 캐싱
   - L1 (Caffeine): ultra-fast
   - L2 (Redis): 분산 공유

4. **Separation of Concerns**: 각 계층 독립적
   - 파싱 ↔ 변환 ↔ 검색 분리
   - 각 계층 단독 테스트 가능

**약점** ⚠️:

1. **복잡성**: ANTLR4 + sealed interface
   - 초보자 진입 장벽 높음
   - 문법 수정 시 재컴파일 필요

2. **메모리**: 캐싱 인프라
   - Redis 인스턴스 관리 필요
   - 캐시 메모리 증가

3. **실시간성**: IR 계층 추가
   - 파이프라인 단계 증가 (1-2ms)
   - 하지만 무시할 수준

---

## 5. 면접 대비 Q&A

### Q1: "왜 ANTLR4를 선택했나요?"

**좋은 답변 구조**:
```
1. 문제 상황 설명
   - NQL 문법을 처리해야 함
   - 단순 Regex로 시작하였으나 확장성 부족

2. 선택지 제시
   - 정규표현식, 수동 파서, ANTLR4

3. 선택 이유
   - 가독성: 문법을 명확하게 정의 (BNF)
   - 확장성: 새 연산자 추가 용이
   - 자동 생성: 파서 코드 자동 생성

4. 트레이드오프
   - 초기 학습 비용 vs 장기 유지보수성
   - 우리는 장기 유지보수성 선택

5. 실제 효과
   - Phase 1: keyword()
   - Phase 2: BETWEEN, CONTAINS, LIKE 추가
   - Phase 3: GROUP BY, BOOST 추가
   - 각 Phase에서 문법만 수정하고 코드는 재사용
```

### Q2: "IR (Intermediate Representation) 계층은 왜 필요한가요?"

**답변**:
```
AST (ANTLR4 생성)
├─ 모든 파싱 규칙 포함
├─ 보조 토큰도 포함 (괄호, 세미콜론)
└─ 직접 사용 시 복잡함

   ↓ (불필요한 정보 제거)

NQL Expression (우리 IR)
├─ 도메인 개념만 포함
├─ 여러 곳에서 재사용
│  - ES Query DSL 변환
│  - 벡터 추출
│  - 캐싱 (쿼리 정규화)
└─ 테스트 용이

예시:
NQL: keyword("AI") AND source IN ["Reuters"]
   ↓ (파싱)
AST: FunctionCallContext { ... }  (복잡함)
   ↓ (Visitor)
IR: CompoundExpr(
      left=KeywordExpr("AI"),
      op=AND,
      right=CompoundExpr(...)
    )  (명확함)
```

### Q3: "2-계층 캐싱 (Caffeine + Redis)이 꼭 필요한가요?"

**답변**:
```
상황 1: 단일 서버 + Redis 없음
├─ Caffeine만 사용
├─ 응답시간: 2-3ms (동일 쿼리)
└─ 문제: 서버 증설 시 캐시 공유 불가

상황 2: 여러 서버 + Redis만 사용
├─ 네트워크 지연: 5-10ms
├─ Redis 호출: 1-2ms
├─ 네트워크: 3-8ms
└─ 문제: 자주 사용되는 쿼리도 매번 Redis 호출

상황 3: Caffeine + Redis (우리 선택) ✅
├─ 첫 접근: Caffeine (< 10μs) → Hit!
├─ Caffeine 미스: Redis (1-2ms)
├─ Redis 미스: DB (15-20ms)
└─ 효과: 평균 < 10ms (Caffeine이 자주 히트)

결론: 서버 증설 시 Redis만으로는 부족
     → 분산 캐시 + 로컬 캐시 조합 필수
```

### Q4: "RRF (Reciprocal Rank Fusion)는 왜 사용하나요?"

**답변**:
```
문제: BM25와 벡터 검색 결과 순서가 다름
- BM25: 키워드 매칭 중심 (정확함)
- 벡터: 의미 유사도 중심 (포괄적)

해결책 3가지:

1️⃣ 선택지: BM25만 사용
   ✅ 빠름
   ❌ 의미 유사도 무시

2️⃣ 선택지: 벡터만 사용
   ✅ 의미 이해
   ❌ 키워드 무시

3️⃣ 선택지: RRF로 통합 ✅
   ✅ 둘 다 고려
   ✅ 수학적 근거 (Reciprocal Rank Fusion 논문)
   ✅ 구현 간단
   ❌ 성능 약간 증가 (무시할 수준)

RRF 공식:
score = Σ(1 / (k + rank_i))
- k = rank_constant (default 60)
- rank_i = i번째 방식의 순위

예: Reuters가 BM25 1위, 벡터 3위
score = 1/(60+1) + 1/(60+3) = 0.032
```

### Q5: "캐싱 전략은 어떻게 결정했나요?"

**답변**:
```
캐싱 대상 선택:

1️⃣ 쿼리 결과
   ├─ TTL: 5분 (뉴스는 자주 변함)
   ├─ 히트율: 40-50%
   ├─ 효과: 30ms → 3ms

2️⃣ 벡터 임베딩
   ├─ TTL: 24시간 (임베딩은 변하지 않음)
   ├─ 히트율: 70-80%
   ├─ 효과: 10ms → 2ms

3️⃣ GROUP BY 집계
   ├─ TTL: 5분 (범주별 분포는 천천히 변함)
   ├─ 히트율: 60-70% (자주 같은 집계 조회)
   ├─ 효과: 40ms → 0ms

캐싱 미선택:

❌ 개별 뉴스 상세 정보
   └─ 접근 패턴 분산, 히트율 낮음

❌ 전체 인덱스
   └─ 메모리 부담 (저수지 문제)

결론: 히트율 + TTL을 고려하여
     고효율 캐싱 대상만 선택
```

---

## 정리

이 Series 01에서 배운 것:

| 개념 | 이유 |
|-----|-----|
| **ANTLR4** | 확장 가능한 문법 파싱 |
| **sealed interface** | Type-safe AST 표현 |
| **IR 계층** | 도메인 개념 추상화 |
| **RRF 하이브리드** | 다중 검색 결과 통합 |
| **2-계층 캐싱** | 성능과 확장성 극대화 |

**다음 Series**: Phase 1-3 구현 상세 (파싱, 연산자, 최적화)

---

**작성자**: Claude Code  
**작성일**: 2026-04-23  
**난이도**: ⭐⭐⭐ (중급 ~ 고급)  
**예상 학습시간**: 45분
