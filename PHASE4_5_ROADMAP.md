# Phase 4-5 상세 로드맵

**최종 업데이트**: 2026-04-23  
**상태**: 계획 수립 완료

---

## 📊 현재 성능 상태

### Phase 3 최종 성과 (2026-04-23)

```
응답시간 개선:
Phase 1: 54.23ms (기준선)
Phase 2: 42.22ms (-22.1%)
Phase 3: 30.94ms (-42.9%) ⭐ 최종 성과

P95 지표: 70.97ms (목표 2000ms 대비 96.4% 개선)
성공률: 100%
```

**가장 빠른 쿼리**:
- GROUP BY sentiment: 21.71ms ⭐⭐
- CONTAINS pattern: 30.79ms
- Match all: 28.21ms

**병목 지점 분석**:
1. Simple keyword (cold start): 109.93ms → 캐싱으로 개선 가능
2. LIKE pattern: 51.48ms → 정규식 최적화 필요
3. BETWEEN range: 44.40ms → 인덱스 전략 개선 필요

---

## 🚀 Phase 4: 성능 최적화 (2026-04-24)

### 목표: 30.94ms → **20ms 달성 (35% 개선)**

### 구현 내용

#### 1️⃣ Redis 캐싱 계층 (예상: 30-40% 개선)

**구현 범위**:

| 대상 | 캐시 전략 | TTL | 예상 히트율 |
|------|----------|-----|-----------|
| 자주 검색된 NQL | Look-aside | 1시간 | 40-50% |
| 벡터 임베딩 | Write-through | 24시간 | 70-80% |
| GROUP BY 결과 | Time-based | 5분 | 60-70% |
| Elasticsearch Query DSL | Distributed | 30분 | 50-60% |

**기술 스택**:
- Redis 7.x (in-memory data store)
- Spring Data Redis (캐싱 추상화)
- Caffeine (로컬 L1 캐시)
- Custom CacheKeyGenerator

**구현 파일**:
```
src/main/java/com/newsquery/
├── cache/
│   ├── QueryCacheConfig.java          (Redis/Caffeine 설정)
│   ├── QueryCacheManager.java         (캐시 전략 선택)
│   ├── EmbeddingCacheService.java     (벡터 캐싱)
│   ├── NQLCacheKey.java               (캐시 키 생성)
│   └── CacheStatsCollector.java       (히트율 모니터링)
└── api/
    └── QueryController.java           (@Cacheable 추가)
```

**구현 단계**:
1. Redis Spring Boot Starter 추가
2. CacheManager 설정 (두 단계 캐싱)
3. @Cacheable/@CacheEvict 어노테이션
4. 캐시 키 전략 설계
5. 캐시 워밍업 로직

**성능 기대효과**:
```
캐시 히트 시: 21.71ms (GROUP BY sentiment) → 2-3ms (캐시 조회)
캐시 미스 시: 30.94ms (현재 수준 유지)

평균 (50% 히트율): (2.5 + 30.94) / 2 ≈ 16.7ms
평균 (60% 히트율): (2.5 × 0.6 + 30.94 × 0.4) ≈ 14.9ms
```

---

#### 2️⃣ Elasticsearch 튜닝 (예상: 15-20% 개선)

**1. 인덱스 최적화**:

| 최적화 | 설정 | 효과 |
|--------|------|------|
| 샤드 수 조정 | `number_of_shards: 3` | 쿼리 병렬화 |
| 리프레시 간격 | `refresh_interval: 30s` | 쓰기 성능 향상 |
| 세그먼트 병합 | `merge.policy.max_merged_segment: 5GB` | 검색 속도 향상 |
| Codec 최적화 | `best_compression` | 디스크/메모리 절감 |

**구현 파일**:
```
pipeline/
└── scripts/
    └── optimize_es_indices.py        (인덱스 최적화 스크립트)
```

**2. 쿼리 최적화**:

```java
// ESQueryBuilder.java 개선
- bool query 구조 재정렬 (must → filter)
- range query에 boost 제거
- kNN 검색 k값 동적 조정 (쿼리 복잡도 기반)
- _source 필드 제한 (불필요한 필드 제외)
```

**3. 벡터 검색 최적화**:

| 개선 사항 | 기존 | 최적화 | 효과 |
|----------|------|--------|------|
| kNN k값 | 100 | 50-100 (적응형) | 20-30% 개선 |
| num_candidates | 100 | 200-500 (정확도 우선) | 정확도 향상 |
| rank_constant | 60 | 100-200 (자동 조정) | RRF 가중치 개선 |

**구현 파일**:
```
src/main/java/com/newsquery/
├── scoring/
│   └── AdaptiveRRFScorer.java        (동적 RRF 파라미터)
├── query/
│   └── OptimizedESQueryBuilder.java  (쿼리 최적화)
└── embedding/
    └── SmartVectorSearch.java        (벡터 검색 최적화)
```

---

#### 3️⃣ 쿼리 프로파일링 및 모니터링 (예상: 5-10% 개선)

**프로파일링 대상**:

```
NQL 파싱: 1-2ms
AST 생성: 0.5-1ms
Visitor 변환: 1-2ms
ESQueryBuilder 생성: 2-3ms ← 병목
벡터 임베딩: 5-10ms (별도 서비스)
ES 쿼리 실행: 15-20ms ← 주요 병목
RRF 스코어링: 1-2ms
JSON 직렬화: 1-2ms
```

**상세 프로파일링 추가**:

```java
// 마이크로 벤치마크
@Benchmark
public void benchmarkESQueryBuilder() { }

@Benchmark
public void benchmarkNQLParsing() { }

@Benchmark
public void benchmarkRRFScoring() { }
```

**구현 파일**:
```
src/main/java/com/newsquery/
├── performance/
│   ├── QueryProfiler.java            (상세 프로파일링)
│   ├── SlowQueryLogger.java           (느린 쿼리 기록)
│   └── PerformanceMetrics.java        (메트릭 수집)
└── benchmark/
    └── QueryBenchmarks.java           (JMH 벤치마크)
```

**Grafana 대시보드 확장**:
- 캐시 히트율 추이
- 응답시간 분포 (p50, p75, p95, p99)
- 쿼리 유형별 성능
- ES 샤드별 쿼리 시간
- 벡터 임베딩 캐시 히트율

---

### Phase 4 예상 성과

```
최종 응답시간 분포 (평균 캐시 히트율 55% 가정):

                현재(Phase 3)    →    Phase 4 예상
Simple keyword:  109.93ms      →    45ms   (-59%)
BETWEEN:          44.40ms      →    32ms   (-28%)
CONTAINS:         30.79ms      →    22ms   (-28%)
LIKE:             51.48ms      →    37ms   (-28%)
GROUP BY cat:     38.56ms      →    20ms   (-48%)
GROUP BY sent:    21.71ms      →     8ms   (-63%)
BOOST+GROUP:      32.54ms      →    18ms   (-45%)

===========================================
평균: 30.94ms → 20.1ms (-35%) ✅ 목표 달성
```

---

## 📋 Phase 5: 기능 확장 (2026-04-25)

### 목표: 사용자 경험 개선 및 고급 기능 추가

### 1️⃣ 저장된 검색 (Saved Queries)

**기능 요구사항**:

| 기능 | 설명 | 우선순위 |
|------|------|---------|
| 쿼리 저장 | 자주 사용하는 NQL 저장 | 🔴 높음 |
| 즐겨찾기 | 저장된 검색 마크 | 🟡 중간 |
| 검색 공유 | 다른 사용자와 검색 공유 | 🟡 중간 |
| 스냅샷 저장 | 특정 시점의 검색 결과 저장 | 🟠 낮음 |

**데이터베이스 스키마**:

```sql
CREATE TABLE saved_queries (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    nql TEXT NOT NULL,
    name VARCHAR(255),
    description TEXT,
    tags VARCHAR(50)[],
    created_at TIMESTAMP,
    updated_at TIMESTAMP,
    last_executed_at TIMESTAMP,
    execution_count INT,
    avg_response_time_ms FLOAT,
    is_favorite BOOLEAN,
    FOREIGN KEY (user_id) REFERENCES users(id)
);

CREATE TABLE query_snapshots (
    id UUID PRIMARY KEY,
    saved_query_id UUID NOT NULL,
    results_json JSONB,
    total_hits INT,
    captured_at TIMESTAMP,
    FOREIGN KEY (saved_query_id) REFERENCES saved_queries(id)
);
```

**API 엔드포인트**:

```
POST   /api/queries/saved              (저장)
GET    /api/queries/saved              (목록)
GET    /api/queries/saved/{id}         (조회)
PUT    /api/queries/saved/{id}         (수정)
DELETE /api/queries/saved/{id}         (삭제)
POST   /api/queries/saved/{id}/execute (실행)
POST   /api/queries/{id}/favorite      (즐겨찾기)
POST   /api/queries/{id}/share         (공유)
```

**구현 파일**:

```
src/main/java/com/newsquery/
├── domain/
│   ├── SavedQuery.java
│   ├── QuerySnapshot.java
│   └── SharedQuery.java
├── repository/
│   ├── SavedQueryRepository.java
│   ├── QuerySnapshotRepository.java
│   └── SharedQueryRepository.java
├── service/
│   └── SavedQueryService.java
└── api/
    └── SavedQueryController.java
```

**프론트엔드 UI**:

```
frontend/app/components/
├── SaveQueryModal.tsx              (저장 대화상자)
├── SavedQueryList.tsx              (저장된 검색 목록)
├── QueryHistory.tsx                (검색 히스토리)
└── QueryComparison.tsx             (검색 결과 비교)
```

---

### 2️⃣ 검색 히스토리 (Search History)

**기능**:

| 기능 | 설명 |
|------|------|
| 자동 기록 | 모든 검색 자동 저장 |
| 히스토리 조회 | 날짜/시간별 검색 기록 |
| 재검색 | 이전 검색 다시 실행 |
| 성능 추이 | 동일 쿼리의 시간에 따른 성능 변화 |
| 통계 | 가장 자주 검색, 평균 응답시간 등 |

**데이터베이스 스키마**:

```sql
CREATE TABLE query_history (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    nql TEXT NOT NULL,
    response_time_ms FLOAT,
    total_hits INT,
    error_message TEXT,
    created_at TIMESTAMP,
    INDEX idx_user_created (user_id, created_at)
);

CREATE TABLE query_stats (
    nql_hash VARCHAR(64) PRIMARY KEY,
    total_executions INT,
    avg_response_time_ms FLOAT,
    min_response_time_ms FLOAT,
    max_response_time_ms FLOAT,
    p95_response_time_ms FLOAT,
    last_executed_at TIMESTAMP
);
```

**API 엔드포인트**:

```
GET /api/queries/history              (히스토리 조회)
GET /api/queries/history/{id}         (상세 조회)
GET /api/queries/stats                (통계)
DELETE /api/queries/history/{id}      (삭제)
DELETE /api/queries/history            (전체 삭제)
GET /api/queries/trending             (인기 검색)
```

---

### 3️⃣ 알림 시스템 (Notifications)

**기능**:

| 유형 | 설명 | 트리거 |
|------|------|--------|
| 성능 저하 | 응답시간 임계값 초과 | avg > 100ms |
| 쿼리 오류 | 쿼리 실행 실패 | error_count > 3 |
| 이상 감지 | 검색 결과 수 급변 | hits ↑↓ 200% |
| 저장 알림 | 지정 키워드 새 뉴스 | keyword match |

**구현 아키텍처**:

```
QueryExecutionEvent (발행)
    ↓
  Event Bus (Kafka)
    ↓
  NotificationService (구독)
    ↓
  알림 규칙 엔진 (Rule Engine)
    ↓
  알림 채널 (이메일, 웹소켓, Push)
```

**구현 파일**:

```
src/main/java/com/newsquery/
├── event/
│   ├── QueryExecutionEvent.java
│   ├── QueryExecutionEventPublisher.java
│   └── QueryEventListener.java
├── notification/
│   ├── NotificationRule.java
│   ├── RuleEngine.java
│   ├── NotificationChannel.java
│   ├── EmailNotifier.java
│   ├── WebSocketNotifier.java
│   └── PushNotifier.java
└── api/
    └── NotificationController.java
```

**사용자 설정**:

```sql
CREATE TABLE notification_preferences (
    user_id UUID PRIMARY KEY,
    enable_performance_alerts BOOLEAN,
    performance_threshold_ms INT,
    enable_error_alerts BOOLEAN,
    error_count_threshold INT,
    enable_keyword_alerts BOOLEAN,
    keywords TEXT[],
    notification_channels TEXT[],
    created_at TIMESTAMP
);
```

---

### 4️⃣ 고급 필터링 및 정렬

**필터링 기능 추가**:

```
GET /api/query?nql=...&filter[source]=Reuters,Bloomberg&filter[sentiment]=positive
GET /api/query?nql=...&sort=-publishedAt&sort=score
```

**Group By 확장**:

```nql
keyword("AI") GROUP BY category HAVING count > 10
keyword("technology") GROUP BY source,category ORDER BY count DESC
```

**멀티 인덱스 검색**:

```nql
INDEX news,analysis: keyword("market") AND sentiment != "negative"
```

---

### Phase 5 예상 성과

```
기능 추가:
✅ 저장된 검색 100+ 개 관리 가능
✅ 검색 히스토리 자동 기록 (90일 유지)
✅ 실시간 알림 (3가지 채널)
✅ 성능 추이 대시보드
✅ 고급 필터링 UI

사용자 경험:
- 재검색 시간 90% 감소 (저장된 검색)
- 성능 문제 조기 감지
- 개인화된 검색 경험
```

---

## 📅 실행 일정

### Phase 4: 2026-04-24 (1일)

```
09:00-10:30  Redis 캐싱 구현 (기본 설정 + 테스트)
10:30-12:00  ES 튜닝 (인덱스 최적화)
13:00-14:30  쿼리 프로파일링 (상세 분석)
14:30-15:30  성능 측정 및 비교
15:30-16:00  문서 작성
```

**목표**: Phase 3 (30.94ms) → Phase 4 (20ms)

---

### Phase 5: 2026-04-25 (1일)

```
09:00-11:00  저장된 검색 + 히스토리 (DB + API)
11:00-12:00  알림 시스템 구현
13:00-14:00  고급 필터링 UI
14:00-15:00  통합 테스트
15:00-16:00  문서 + 데모 준비
```

**목표**: 사용자 경험 대폭 개선

---

## 🎯 성공 지표

### Phase 4 KPI

| 지표 | 목표 | 측정 방법 |
|------|------|---------|
| 응답시간 | < 20ms | 성능 테스트 스크립트 |
| 캐시 히트율 | > 50% | Redis 메트릭 |
| P95 응답시간 | < 35ms | Prometheus |
| CPU 사용률 | < 60% | Grafana |

### Phase 5 KPI

| 지표 | 목표 | 측정 방법 |
|------|------|---------|
| 저장된 검색 재사용율 | > 40% | 사용자 분석 |
| 알림 정확도 | > 90% | 오경보율 |
| 사용자 만족도 | > 4.5/5.0 | 피드백 조사 |

---

## 📊 최종 보고서 작성 계획

### 구성

```
FINAL_PHASE_REPORT.md (총 100+ 페이지)
├── Executive Summary
│   └── 전체 프로젝트 성과 (Phase 1-5)
├── Phase 4 상세 리포트
│   ├── 캐싱 전략 분석
│   ├── ES 튜닝 효과 측정
│   ├── 성능 개선 요약
│   └── 최적화 교훈
├── Phase 5 기능 리포트
│   ├── 저장된 검색 통계
│   ├── 알림 효과 분석
│   └── 사용자 만족도
├── 전체 성능 비교
│   ├── 초기 vs 최종
│   ├── 병목 분석
│   └── 향후 개선 방향
└── 프로덕션 배포 체크리스트
```

### 내용

- **기술 심층 분석**: Redis, ES 튜닝 상세 내용
- **성능 데이터**: 그래프 + 통계 포함
- **비용 분석**: 인프라 요구사항
- **확장성 검토**: 향후 성장 시나리오
- **팀 회고**: 개발 경험담 및 교훈

---

## 🔧 기술 의존성

### Phase 4 필요 라이브러리

```gradle
// Redis
implementation 'org.springframework.boot:spring-boot-starter-data-redis:3.2.3'
implementation 'redis.clients:jedis:5.1.0'

// 캐싱
implementation 'com.github.ben-manes.caffeine:caffeine:3.1.8'

// 성능 측정
testImplementation 'org.openjdk.jmh:jmh-core:1.37'
testImplementation 'org.openjdk.jmh:jmh-generator-annprocess:1.37'
```

### Phase 5 필요 라이브러리

```gradle
// 이벤트 기반 아키텍처
implementation 'org.springframework.kafka:spring-kafka:3.0.13'

// 실시간 알림 (WebSocket)
implementation 'org.springframework.boot:spring-boot-starter-websocket:3.2.3'

// 이메일
implementation 'org.springframework.boot:spring-boot-starter-mail:3.2.3'
```

---

## 📝 다음 단계

1. **Phase 4 시작 체크리스트** ✅
   - [ ] Redis 도커 컨테이너 실행
   - [ ] Spring Data Redis 추가
   - [ ] CacheConfig 작성
   - [ ] 성능 측정 준비

2. **Phase 5 준비** 📋
   - [ ] DB 스키마 설계 완료
   - [ ] API 명세서 작성
   - [ ] UI 목업 준비
   - [ ] 알림 규칙 정의

3. **최종 보고서 준비** 📊
   - [ ] 데이터 수집 자동화
   - [ ] 그래프 템플릿 준비
   - [ ] 블로그 글 구성안 작성

---

**작성자**: Claude Code  
**작성일**: 2026-04-23  
**상태**: 준비 완료 → Phase 4 시작 가능
