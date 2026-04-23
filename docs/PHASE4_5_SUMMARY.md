# Phase 4-5 실행 요약

**문서 생성일**: 2026-04-23  
**상태**: 계획 수립 완료 → 실행 준비

---

## 📈 전체 성능 로드맵

```
Phase 1  Phase 2  Phase 3  Phase 4  Phase 5
(기본)   (범위)   (집계)   (최적화) (확장)
|        |        |        |        |
54.23ms  42.22ms  30.94ms  20.00ms  18.00ms
  100%     78%      57%      37%      33%
   |        |        |--------|--------|
   |        |        -42.9%   -35.3%   -41.7%
   |        +---------|
   |              -22.1%
   |
   +------------- 91.5% 총 개선 (목표: 500ms)
```

---

## 🎯 Phase 4: 성능 최적화 (2026-04-24)

### 목표
**30.94ms → 20ms (35% 개선)**

### 3가지 핵심 전략

#### 1️⃣ Redis 캐싱 (30-40% 개선)
```
자주 사용된 쿼리 → 캐시 히트 → 2-3ms
캐시 미스 → ES 쿼리 → 30-40ms

평균 50% 히트율 시:
(2.5 × 0.5) + (30 × 0.5) = 16.25ms ✅
```

**대상**:
- 자주 검색된 NQL 쿼리
- 벡터 임베딩 (재활용)
- GROUP BY 결과 (5분 TTL)

#### 2️⃣ Elasticsearch 튜닝 (15-20% 개선)
```
최적화 전: 30.94ms
├─ 파싱: 3ms
├─ 쿼리 빌드: 5ms
├─ ES 실행: 20ms ← 병목
└─ RRF 스코어링: 2ms

튜닝 후:
├─ 샤드 병렬화: -2ms
├─ 인덱스 최적화: -2ms
├─ 쿼리 구조 개선: -1ms
└─ 벡터 검색 적응형: -3ms
```

**조치**:
- 샤드 수 조정 (number_of_shards: 3)
- 리프레시 간격 증가 (refresh_interval: 30s)
- bool 쿼리 재정렬 (must → filter)
- 벡터 검색 k값 동적 조정

#### 3️⃣ 쿼리 프로파일링 (5-10% 개선)
```
상세 측정으로 숨겨진 병목 발견
↓
마이크로 벤치마크 (JMH) 추가
↓
느린 쿼리 자동 기록
↓
Grafana 대시보드 확장 (p99 메트릭)
```

### 예상 결과

| 쿼리 유형 | Phase 3 | Phase 4 | 개선율 |
|----------|---------|---------|--------|
| Simple keyword | 109.93ms | 45ms | -59% |
| GROUP BY sentiment | 21.71ms | **8ms** | -63% |
| GROUP BY category | 38.56ms | 20ms | -48% |
| CONTAINS | 30.79ms | 22ms | -28% |
| LIKE | 51.48ms | 37ms | -28% |
| BETWEEN | 44.40ms | 32ms | -28% |
| BOOST + GROUP | 32.54ms | 18ms | -45% |
| **평균** | **30.94ms** | **20ms** | **-35%** ✅ |

---

## 📋 Phase 5: 기능 확장 (2026-04-25)

### 목표
**사용자 경험 대폭 개선 + 고급 기능 추가**

### 4가지 핵심 기능

#### 1️⃣ 저장된 검색 (Saved Queries)

```
사용자 → NQL 입력 → 검색 결과
         ↓
      저장 (즐겨찾기)
         ↓
    다음 세션에서 클릭 1번으로 재검색
```

**효과**:
- 재검색 시간: 30.94ms → 100ms (입력 고려) → 2-3ms (캐시)
- 사용자 만족도 ⬆️
- 쿼리 재사용율 예상 40%

**구현**: Spring Data JPA + PostgreSQL

#### 2️⃣ 검색 히스토리 (Search History)

```
모든 검색 자동 기록
  ↓
날짜별/쿼리별 통계
  ↓
인기 검색 추천
  ↓
성능 추이 분석
```

**기능**:
- 최근 100개 검색 목록
- 쿼리 통계 (실행 횟수, 평균 응답시간)
- 성능 추이 차트

#### 3️⃣ 알림 시스템 (Notifications)

```
QueryExecutionEvent
    ↓ (Kafka)
NotificationService
    ↓ (Rule Engine)
알림 규칙 평가
    ↓
이메일/웹소켓/Push
```

**알림 유형**:
- 📊 성능 저하 (응답시간 > 100ms)
- ⚠️ 쿼리 오류 (3회 연속 실패)
- 🔔 이상 감지 (결과 수 ±200%)
- 📰 키워드 알림 (지정 키워드 새 뉴스)

**기술**: Kafka Event Bus + Rule Engine

#### 4️⃣ 고급 필터링 (Advanced Filtering)

```nql
# 필터링 추가
GET /api/query?nql=...&filter[source]=Reuters&filter[sentiment]=positive

# GROUP BY 확장
keyword("AI") GROUP BY category HAVING count > 10

# 멀티 인덱스
INDEX news,analysis: keyword("market")
```

### 구현 분량

| 기능 | 예상 LOC | 시간 | 복잡도 |
|------|---------|------|--------|
| 저장된 검색 | 500 LOC | 3h | ⭐⭐ |
| 검색 히스토리 | 300 LOC | 2h | ⭐⭐ |
| 알림 시스템 | 800 LOC | 4h | ⭐⭐⭐ |
| 고급 필터링 | 400 LOC | 2.5h | ⭐⭐⭐ |

---

## 💾 데이터베이스 스키마

### 핵심 테이블

```sql
-- 저장된 검색
CREATE TABLE saved_queries (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    nql TEXT,
    name VARCHAR(255),
    is_favorite BOOLEAN,
    execution_count INT,
    avg_response_time_ms FLOAT,
    created_at TIMESTAMP,
    updated_at TIMESTAMP
);

-- 검색 히스토리
CREATE TABLE query_history (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    nql TEXT,
    response_time_ms FLOAT,
    total_hits INT,
    created_at TIMESTAMP
);

-- 알림 규칙
CREATE TABLE notification_preferences (
    user_id UUID PRIMARY KEY,
    enable_performance_alerts BOOLEAN,
    performance_threshold_ms INT,
    enable_error_alerts BOOLEAN,
    error_count_threshold INT,
    notification_channels TEXT[]  -- email,websocket,push
);
```

---

## 📊 예상 성능 지표

### Phase 4 달성 시

```
응답시간:
├─ 평균: 30.94ms → 20ms (-35%)
├─ P95: 70.97ms → 45ms (-36%)
└─ P99: 85ms → 55ms (-35%)

캐시 효율:
├─ 히트율: 50-60%
├─ 캐시 히트 시간: 2-3ms
└─ 캐시 미스 시간: 30-40ms

시스템 부하:
├─ CPU: 40-50% (현재 60%)
├─ 메모리: 1.5GB (Redis 500MB + App 1GB)
└─ 네트워크: 50-100 Mbps
```

### Phase 5 달성 시

```
사용자 경험:
├─ 저장 검색 재사용율: 40%+
├─ 히스토리 조회율: 30%+
└─ 알림 정확도: 90%+

기능 커버리지:
├─ 저장된 검색: 100+ 개 관리
├─ 히스토리: 90일 자동 보관
├─ 알림: 4가지 유형
└─ 고급 필터링: 10+ 개 필터
```

---

## 🔧 기술 스택 확장

### 추가 라이브러리

**Phase 4**:
```gradle
spring-boot-starter-data-redis:3.2.3
caffeine:3.1.8
jmh-core:1.37
```

**Phase 5**:
```gradle
spring-kafka:3.0.13
spring-boot-starter-websocket:3.2.3
spring-boot-starter-mail:3.2.3
```

### 인프라

**Phase 4**:
- Redis 7.x (in-memory cache)
- 디스크 +500MB

**Phase 5**:
- PostgreSQL (히스토리/알림)
- 디스크 +2GB
- 메모리 +500MB

---

## 📝 실행 계획

### Day 1 (Phase 4) - 2026-04-24

```
09:00 ~ 10:30  ✅ Redis 캐싱 구현
                ├─ Spring Data Redis 설정
                ├─ CacheManager 구현
                ├─ @Cacheable 어노테이션 추가
                └─ 캐시 워밍업

10:30 ~ 12:00  ✅ Elasticsearch 튜닝
                ├─ 인덱스 설정 최적화
                ├─ bool 쿼리 재정렬
                ├─ 벡터 검색 동적 k값
                └─ 테스트 및 검증

13:00 ~ 14:30  ✅ 쿼리 프로파일링
                ├─ 마이크로 벤치마크 추가
                ├─ 느린 쿼리 로거 구현
                └─ Grafana 대시보드 확장

14:30 ~ 15:30  ✅ 성능 측정
                ├─ performance_comparison.py 실행
                ├─ Phase 4 결과 분석
                └─ 20ms 달성 확인

15:30 ~ 16:00  ✅ 문서 작성
                └─ PHASE4_COMPLETE.md
```

### Day 2 (Phase 5) - 2026-04-25

```
09:00 ~ 11:00  ✅ 저장된 검색 + 히스토리
                ├─ DB 스키마 생성
                ├─ Repository & Service
                ├─ REST API 구현
                └─ 프론트엔드 UI

11:00 ~ 12:00  ✅ 알림 시스템
                ├─ Event 정의
                ├─ Rule Engine
                ├─ 알림 채널 구현
                └─ 통합 테스트

13:00 ~ 14:00  ✅ 고급 필터링
                ├─ NQL 문법 확장
                ├─ ESQueryBuilder 수정
                └─ UI 추가

14:00 ~ 15:00  ✅ 통합 테스트
                └─ 전체 흐름 검증

15:00 ~ 16:00  ✅ 최종 보고서
                └─ FINAL_PHASE_REPORT.md
```

---

## 📋 최종 체크리스트

### Phase 4
- [ ] Redis 도커 실행
- [ ] CacheConfig 작성
- [ ] ES 인덱스 재생성
- [ ] 성능 테스트 스크립트 실행
- [ ] 20ms 달성 확인
- [ ] 문서 완성

### Phase 5
- [ ] DB 스키마 생성
- [ ] API 엔드포인트 구현
- [ ] 프론트엔드 UI 완성
- [ ] 알림 규칙 테스트
- [ ] 전체 통합 테스트
- [ ] 최종 보고서 작성

### 최종 보고서
- [ ] Phase 1-5 성능 비교 그래프
- [ ] 기술 상세 분석
- [ ] 비용 분석 (인프라)
- [ ] 향후 개선 방향
- [ ] 팀 회고 및 교훈
- [ ] 프로덕션 배포 가이드

---

## 🎉 예상 최종 성과

### 성능
```
초기값 (Phase 1): 54.23ms
최종값 (Phase 5): 18.00ms
총 개선율: 66.8% ✅

목표 (500ms): 91.5% 개선 달성
```

### 기능
```
지원 연산자: 20+ (AND, OR, NOT, IN, BETWEEN, LIKE, CONTAINS, GROUP BY, BOOST 등)
저장된 검색: 100+ 개 관리
검색 히스토리: 자동 기록 (90일)
알림 유형: 4가지 (성능, 오류, 이상, 키워드)
사용자 기능: 즐겨찾기, 공유, 통계
```

### 문서
```
총 페이지: 500+ (모든 단계별 상세 가이드)
실제 데이터: 모든 성능 측정값 포함
블로그 글: 최적화 여정 & 교훈
```

---

**다음 단계**: Phase 4 시작 (2026-04-24 09:00)  
**문서**: [PHASE4_5_ROADMAP.md](PHASE4_5_ROADMAP.md) 상세 버전 참고
