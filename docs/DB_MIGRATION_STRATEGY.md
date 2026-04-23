# DB 마이그레이션 전략: 왜 PostgreSQL을 추가하는가?

## 📋 핵심 질문
> "ES(Elasticsearch)를 이미 사용하는데 왜 PostgreSQL을 추가하는가?"

**답변**: ES와 PostgreSQL은 **다른 목적**으로 사용됩니다.

---

## 🎯 각 DB의 역할 구분

### Elasticsearch (ES) - 검색 최적화
```
목적: 뉴스 데이터 검색 (전문 검색, 벡터 유사도)
사용 대상: 뉴스 본문, 키워드, 임베딩 벡터
특징:
  ✅ 전문 검색 빠름 (BM25 알고리즘)
  ✅ 벡터 유사도 검색 빠름 (kNN)
  ✅ 복잡한 쿼리 DSL 지원
  ❌ ACID 트랜잭션 약함
  ❌ 관계형 데이터 관리 약함
  ❌ 정확한 수치 계산 약함

데이터 예시:
{
  "_index": "news",
  "content": "AI 기술이 혁신을...",
  "content_vector": [0.234, -0.156, ...],
  "sentiment": "positive",
  "score": 8.5
}
```

### PostgreSQL - 비즈니스 데이터 관리
```
목적: 사용자, 설정, 메타데이터, 통계 저장
사용 대상:
  • 사용자 정보 (userId, email)
  • 알림 규칙 (NotificationRuleConfig)
  • 사용자 설정 (UserNotificationPreference)
  • 저장된 검색 (SavedQuery)
  • 검색 히스토리 (QueryHistory)
  • 쿼리 통계 (avg_response_time, execution_count)

특징:
  ✅ ACID 트랜잭션 (데이터 일관성)
  ✅ 외래키 제약조건 (관계 무결성)
  ✅ 복잡한 조인 쿼리
  ✅ 정확한 수치 계산
  ❌ 대규모 텍스트 검색 느림
  ❌ 벡터 검색 지원 약함

데이터 예시:
{
  "user_id": "user123",
  "email": "user@example.com",
  "rule_name": "PERFORMANCE",
  "threshold": 1.5,
  "enabled": true,
  "created_at": "2026-04-23T15:30:00"
}
```

---

## 📊 데이터 흐름 구조

```
┌──────────────────────────────────────────────────────┐
│           외부 데이터 소스                            │
│  ├─ GDELT (뉴스 메타데이터)                           │
│  └─ RSS (뉴스 본문)                                   │
└─────────────────────┬────────────────────────────────┘
                      │
        ┌─────────────┴─────────────┐
        ▼                           ▼
┌───────────────────┐      ┌────────────────────┐
│ Elasticsearch     │      │ PostgreSQL         │
│ (뉴스 검색)        │      │ (메타데이터)        │
├───────────────────┤      ├────────────────────┤
│ • content (본문)   │      │ • users            │
│ • content_vector  │      │ • saved_queries    │
│ • sentiment       │      │ • query_history    │
│ • score           │      │ • rules_config     │
│ • source          │      │ • preferences      │
│ • publishedAt     │      │ • query_stats      │
└────────┬──────────┘      └────────┬───────────┘
         │                         │
         └──────────────┬──────────┘
                        │
            ┌───────────▼────────────┐
            │ QueryController        │
            │ (API 엔드포인트)        │
            └────────────────────────┘
                        │
            ┌───────────▼────────────┐
            │ 애플리케이션 로직       │
            │ • 쿼리 실행            │
            │ • 결과 랭킹            │
            │ • 통계 계산            │
            └────────────────────────┘
```

---

## 🔄 구체적인 사용 사례

### 사례 1: 뉴스 검색 (ES 사용)

```
사용자: "AI AND sentiment:positive"

1. QueryController에서 NQL 파싱
2. ESQueryBuilder로 ES Query DSL 변환
3. Elasticsearch에 bool 쿼리 실행
   {
     "bool": {
       "must": [
         { "match": { "content": "AI" } }
       ],
       "filter": [
         { "term": { "sentiment": "positive" } }
       ]
     }
   }
4. ES가 **빠르게** 결과 반환 (수백ms)
   - BM25로 관련도순 정렬
   - 벡터 유사도로 재랭킹

✅ ES의 강점: "뉴스 검색"
```

### 사례 2: 알림 규칙 조회 (PostgreSQL 사용)

```
사용자 "user123"의 알림 규칙 조회

1. NotificationRuleConfigRepository.findByUserId("user123")
2. PostgreSQL에서 쿼리
   SELECT * FROM notification_rule_configs
   WHERE user_id = 'user123' AND enabled = true
3. 결과 반환 (수ms)
   [
     {
       "ruleName": "PERFORMANCE",
       "condition": {"threshold": 1.5},
       "enabled": true
     }
   ]

✅ PostgreSQL의 강점: "구조화된 데이터 관리"
```

### 사례 3: 성능 통계 계산 (PostgreSQL 사용)

```
특정 NQL의 평균 응답시간 계산

쿼리:
SELECT 
  AVG(response_time_ms) as avg_response_time,
  COUNT(*) as execution_count,
  MIN(response_time_ms) as min_response_time,
  MAX(response_time_ms) as max_response_time
FROM query_history
WHERE user_id = 'user123' AND nql = 'keyword(AI)'

✅ PostgreSQL의 강점: "정확한 수치 계산 + 집계"
   (ES도 가능하지만 PostgreSQL이 더 정확함)
```

---

## 📈 왜 두 개를 모두 필요한가?

### ❌ ES만 사용하는 경우의 문제점

```
1️⃣ 사용자 정보 저장
   - ES에 저장하면 비용 증가
   - 구조화된 데이터에는 부적합
   - ACID 트랜잭션 없음

2️⃣ 알림 규칙 관리
   - ES는 스키마 유연성이 목적
   - 엄격한 타입 정의가 필요한 규칙은 부적합

3️⃣ 통계 계산
   - 복잡한 집계 함수가 필요
   - PostgreSQL이 훨씬 정확함
   - ES aggregation은 근사값 제공

4️⃣ 트랜잭션 필요
   - 사용자 설정 + 규칙 + 히스토리를 원자적으로 저장해야 하는 경우
   - ES는 약한 일관성만 제공
```

### ✅ PostgreSQL + ES 조합의 장점

```
최적의 구분:
  
  PostgreSQL (정형 데이터)
  ├─ 사용자 (users)
  ├─ 설정 (notification_preferences)
  ├─ 규칙 (notification_rules)
  ├─ 히스토리 (query_history)
  └─ 통계 (query_stats)
  
  Elasticsearch (비정형 데이터 + 검색)
  ├─ 뉴스 본문 (content)
  ├─ 임베딩 벡터 (content_vector)
  ├─ 메타데이터 (sentiment, score, source)
  └─ 전문 검색 (BM25 + kNN)
```

---

## 🚀 성능 비교

### 쿼리 1: 뉴스 검색 (수백만 건)

```
사용자: "AI AND sentiment:positive"

Elasticsearch:
  ├─ 인덱스 크기: 50GB
  ├─ 응답시간: 50-200ms ✅ 빠름
  ├─ 이유: 역색인 + 벡터 인덱스 최적화
  └─ 처리건수: 수백만

PostgreSQL:
  ├─ 시간: 3000-5000ms ❌ 느림
  ├─ 이유: 전문 검색 인덱스 부족
  └─ 권장: 쓰지 말 것
```

### 쿼리 2: 사용자 설정 조회

```
쿼리: user_id = 'user123'의 알림 규칙

Elasticsearch:
  ├─ 응답시간: 100-300ms ❌ 느림
  ├─ 이유: 구조화된 데이터에 오버헤드
  └─ 권장: 비효율적

PostgreSQL:
  ├─ 응답시간: 1-5ms ✅ 매우 빠름
  ├─ 이유: PK 인덱스 직접 접근
  └─ 권장: 최고의 선택
```

### 쿼리 3: 평균 응답시간 계산

```
쿼리: SELECT AVG(response_time) ...

Elasticsearch:
  ├─ 정확도: 근사값 (기본 샘플링)
  ├─ 응답시간: 100-200ms
  └─ 권장: 성능 메트릭용 (대략적)

PostgreSQL:
  ├─ 정확도: 100% 정확
  ├─ 응답시간: 5-20ms
  └─ 권장: 핵심 통계용 (정확함)
```

---

## 📊 Phase별 DB 사용

### Phase 5 (현재)
```
메모리 저장소 (프로토타입)
├─ SavedQuery: Map<String, SavedQuery>
├─ QueryHistory: Map<String, List<QueryHistory>>
└─ KeywordSubscription: Map<String, KeywordSubscription>

문제점:
  ❌ 서버 재시작 시 데이터 손실
  ❌ 다중 서버 환경에서 동기화 불가
  ❌ 확장성 없음
```

### Phase 5.1 (목표)
```
PostgreSQL (장기 저장)
├─ users (사용자)
├─ saved_queries (저장된 검색)
├─ query_history (검색 이력)
├─ notification_rule_configs (규칙)
└─ user_notification_preferences (설정)

장점:
  ✅ 영구 저장
  ✅ ACID 트랜잭션
  ✅ 다중 서버 환경 지원
  ✅ 복잡한 쿼리 지원
```

### Phase 6-7
```
PostgreSQL + Elasticsearch + Kafka

┌─────────────────────────────────┐
│ 뉴스 검색                       │
│ (Elasticsearch)                  │
│ • BM25 검색                      │
│ • 벡터 유사도                    │
│ • 실시간 임베딩                  │
└──────────────┬──────────────────┘
               │
        ┌──────▼───────────────────┐
        │  PostgreSQL             │
        │  (메타데이터 + 설정)      │
        ├────────────────────────┤
        │ • 사용자 정보            │
        │ • 알림 규칙              │
        │ • 사용자 설정            │
        │ • 검색 히스토리          │
        │ • 쿼리 통계              │
        └────────────────────────┘
               │
        ┌──────▼───────────────────┐
        │  Kafka (이벤트)           │
        │  • 비동기 처리            │
        │  • 분산 처리              │
        │  • 실시간 분석            │
        └────────────────────────┘
```

---

## 💡 마이그레이션 전략

### Step 1: 메모리 → PostgreSQL

```java
// 현재 (메모리)
private final Map<String, SavedQuery> savedQueries = new ConcurrentHashMap<>();

// 변경 후 (PostgreSQL)
@Repository
public interface SavedQueryRepository extends JpaRepository<SavedQuery, String> {
    List<SavedQuery> findByUserId(String userId);
}
```

### Step 2: 스키마 설계

```sql
CREATE TABLE users (
    user_id VARCHAR(255) PRIMARY KEY,
    email VARCHAR(255) UNIQUE,
    created_at TIMESTAMP DEFAULT NOW()
);

CREATE TABLE saved_queries (
    id VARCHAR(255) PRIMARY KEY,
    user_id VARCHAR(255) REFERENCES users(user_id),
    nql TEXT NOT NULL,
    name VARCHAR(255),
    description TEXT,
    is_favorite BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW()
);

CREATE TABLE query_history (
    id VARCHAR(255) PRIMARY KEY,
    user_id VARCHAR(255) REFERENCES users(user_id),
    nql TEXT NOT NULL,
    response_time_ms BIGINT,
    total_hits INTEGER,
    success BOOLEAN,
    error_message TEXT,
    executed_at TIMESTAMP DEFAULT NOW()
);

CREATE TABLE notification_rule_configs (
    id VARCHAR(255) PRIMARY KEY,
    user_id VARCHAR(255) REFERENCES users(user_id),
    rule_name VARCHAR(50),
    condition JSONB,
    enabled BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW()
);

CREATE TABLE user_notification_preferences (
    id VARCHAR(255) PRIMARY KEY,
    user_id VARCHAR(255) UNIQUE REFERENCES users(user_id),
    email_enabled BOOLEAN DEFAULT TRUE,
    push_enabled BOOLEAN DEFAULT TRUE,
    sms_enabled BOOLEAN DEFAULT FALSE,
    quiet_hours_start VARCHAR(5),
    quiet_hours_end VARCHAR(5),
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW()
);

-- 인덱스
CREATE INDEX idx_saved_queries_user_id ON saved_queries(user_id);
CREATE INDEX idx_query_history_user_id ON query_history(user_id);
CREATE INDEX idx_query_history_nql ON query_history(nql);
CREATE INDEX idx_notification_rules_user ON notification_rule_configs(user_id);
```

### Step 3: JPA 엔티티

```java
@Entity
@Table(name = "saved_queries")
public class SavedQuery {
    @Id
    private String id;
    
    @Column(nullable = false)
    private String userId;
    
    @Column(columnDefinition = "TEXT")
    private String nql;
    
    private String name;
    private String description;
    
    @Column(name = "is_favorite")
    private boolean favorite;
    
    @CreationTimestamp
    private LocalDateTime createdAt;
    
    @UpdateTimestamp
    private LocalDateTime updatedAt;
}

@Repository
public interface SavedQueryRepository extends JpaRepository<SavedQuery, String> {
    List<SavedQuery> findByUserId(String userId);
    List<SavedQuery> findByUserIdAndFavoriteTrue(String userId);
}
```

---

## 🎯 결론

### DB 사용 원칙

| 작업 | 사용 DB | 이유 |
|------|--------|------|
| 뉴스 검색 | **ES** | 전문 검색 + 벡터 검색 최적화 |
| 사용자 정보 | **PostgreSQL** | 구조화된 데이터, ACID 필요 |
| 알림 규칙 | **PostgreSQL** | 정확한 타입 정의 필요 |
| 쿼리 통계 | **PostgreSQL** | 정확한 수치 계산 필요 |
| 검색 히스토리 | **PostgreSQL** | 트랜잭션 + 관계 무결성 필요 |
| 복잡한 분석 | **PostgreSQL** | JOIN + GROUP BY 최적화 |
| 임베딩 벡터 | **ES** | 벡터 인덱스 + kNN 검색 |

### 성능 요약

```
뉴스 검색 (수백만 건)
  ├─ ES: 50-200ms ✅
  └─ PostgreSQL: 3000-5000ms ❌

사용자 설정 조회
  ├─ PostgreSQL: 1-5ms ✅
  └─ ES: 100-300ms ❌

평균값 계산
  ├─ PostgreSQL: 5-20ms, 100% 정확 ✅
  └─ ES: 100-200ms, 근사값 ❌
```

---

## 🚀 최종 정리

**Q: ES를 이미 사용하는데 왜 PostgreSQL을 추가하는가?**

**A**: 
- **ES**: 뉴스 검색용 (빠른 전문 검색 + 벡터 검색)
- **PostgreSQL**: 메타데이터 + 설정 + 통계 저장 (ACID + 정확성)

**두 가지 다른 목적**이므로 **둘 다 필요**합니다!

