# Phase 5.1: PostgreSQL JPA 마이그레이션 설정 가이드

## 개요

Phase 5.1은 메타데이터, 사용자 설정, 검색 히스토리 등을 PostgreSQL에 저장하여 ACID 보장과 구조화된 데이터 관리를 제공합니다.

## 아키텍처

### 데이터베이스 역할 분담

```
┌─────────────────────────────────────────────────────────────────┐
│                    N-QL Intelligence                             │
├─────────────────────────────────────────────────────────────────┤
│  Elasticsearch (뉴스 검색)      │  PostgreSQL (메타데이터)       │
│  - 전문 검색 (키워드, 벡터)   │  - 사용자 정보                │
│  - 50-200ms (수백만 문서)     │  - 저장된 쿼리                │
│  - RRF 스코어링               │  - 검색 히스토리              │
│  - 통계 집계                  │  - 알림 설정 (1-5ms)         │
└─────────────────────────────────────────────────────────────────┘
```

## 필수 구성 요소

### 1. PostgreSQL 설치

**Docker 사용 (권장)**:
```bash
docker run -d \
  --name postgres-newsquery \
  -e POSTGRES_DB=newsquery \
  -e POSTGRES_USER=postgres \
  -e POSTGRES_PASSWORD=postgres \
  -p 5432:5432 \
  postgres:15-alpine
```

**로컬 설치** (Windows):
- [PostgreSQL 15 다운로드](https://www.postgresql.org/download/windows/)
- 설치 중 기본값 유지 (포트 5432)
- 데이터베이스 생성:
```bash
psql -U postgres -c "CREATE DATABASE newsquery;"
```

### 2. 데이터베이스 연결 설정

**application.yml 검토**:
```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/newsquery
    username: postgres
    password: postgres
    driver-class-name: org.postgresql.Driver
    hikari:
      maximum-pool-size: 10
      minimum-idle: 2

  jpa:
    hibernate:
      ddl-auto: validate
    properties:
      hibernate:
        dialect: org.hibernate.dialect.PostgreSQLDialect
        format_sql: true

  flyway:
    enabled: true
    baseline-on-migrate: true
    locations: classpath:db/migration
```

### 3. Flyway 마이그레이션

자동 실행 (첫 시작 시):
- `V1__initial_schema.sql` — 초기 테이블 생성
- `V2__add_notification_features.sql` — 기본 규칙 설정

수동 실행:
```bash
./gradlew flywayMigrate
```

## 엔티티 구조

### User (사용자)
```
user_id: VARCHAR(255) PK
email: VARCHAR(255) UNIQUE
created_at: TIMESTAMP (자동)
```

### SavedQuery (저장된 검색)
```
id: VARCHAR(255) PK
user_id: VARCHAR(255) FK
nql: TEXT (NQL 쿼리)
name, description: VARCHAR/TEXT
is_favorite: BOOLEAN
execution_count, avg_response_time_ms: INT/DOUBLE
created_at, updated_at, last_executed_at: TIMESTAMP
```

### QueryHistory (검색 히스토리)
```
id: VARCHAR(255) PK
user_id: VARCHAR(255) FK
nql: TEXT
response_time_ms: DOUBLE
total_hits: BIGINT
success: BOOLEAN
error_message: TEXT (nullable)
executed_at: TIMESTAMP
```

### KeywordSubscription (키워드 구독)
```
id: VARCHAR(255) PK
user_id, keyword: VARCHAR(255)
is_active: BOOLEAN
created_at: TIMESTAMP
UNIQUE (user_id, keyword)
```

### UserNotificationPreference (알림 설정)
```
id: VARCHAR(255) PK
user_id: VARCHAR(255) UNIQUE FK
email_enabled, push_enabled, console_enabled: BOOLEAN
quiet_hours_start, quiet_hours_end: VARCHAR(5) HH:MM
created_at, updated_at: TIMESTAMP
```

### NotificationRuleConfig (규칙 설정)
```
id: VARCHAR(255) PK
rule_type: VARCHAR(50) {PERFORMANCE, ERROR, KEYWORD}
enabled: BOOLEAN
condition_json: TEXT (JSON)
created_at, updated_at: TIMESTAMP
```

## 인덱스 전략

성능 최적화를 위한 주요 인덱스:

| 테이블 | 인덱스 | 용도 |
|--------|--------|------|
| saved_queries | user_id | 사용자별 쿼리 조회 |
| query_history | user_id, executed_at | 최근 히스토리 조회 (복합 인덱스) |
| keyword_subscriptions | user_id | 사용자별 구독 목록 |

## 데이터 흐름

### 1. 쿼리 실행 시
```
QueryController.search()
  ↓
NewsSearchService.searchWithRrf()
  ↓
QueryExecutionEvent 생성
  ↓
EventPublisher.publish()
  ↓
NotificationService.onQueryExecution()
  ├─ QueryHistory 저장 (PostgreSQL)
  ├─ SavedQuery 통계 업데이트
  └─ RuleEngine 평가
```

### 2. 사용자 관심 키워드 알림
```
KeywordRule.evaluate(QueryExecutionEvent)
  ↓
KeywordSubscriptionService.getSubscribedKeywords(userId)
  ↓
PostgreSQL 쿼리 (1-5ms, 캐시 가능)
  ↓
Notification 생성 및 배분
```

## 성능 특성

### PostgreSQL 성능 (로컬)

| 작업 | 응답 시간 | 특성 |
|------|----------|------|
| 사용자 조회 | 1-2ms | PK 조회 |
| 최근 히스토리 (10건) | 2-3ms | 인덱스 활용 |
| 키워드 구독 조회 | 1-2ms | IN 쿼리 |
| 저장된 쿼리 목록 | 3-5ms | 페이징 (50건) |

### 예상 오버헤드

- SavedQueryService.recordHistory() → 2-3ms (INSERT)
- KeywordRule.evaluate() → 1-2ms (SELECT) + 통지 배분

**총 알림 시스템 오버헤드**: 10-31ms (메인 쿼리 시간의 3-10%)

## 트러블슈팅

### 연결 오류
```
org.postgresql.util.PSQLException: Connection to localhost:5432 refused
```
**해결**: PostgreSQL 서비스 실행 확인
```bash
# Docker
docker ps | grep postgres-newsquery

# Windows 서비스
services.msc → PostgreSQL 확인

# psql 연결 테스트
psql -h localhost -U postgres -d newsquery
```

### Flyway 마이그레이션 오류
```
Found non-empty schema "public" without metadata table! Use baseline() or set baselineOnMigrate to true
```
**해결**: application.yml에 설정됨 (자동 처리)

### JPA 컴파일 오류
```
The import org.springframework.data cannot be resolved
```
**해결**: 빌드 캐시 클리어
```bash
./gradlew clean build -x test
```

## 실행 순서

1. **PostgreSQL 시작**
   ```bash
   docker-compose up -d postgres
   ```

2. **Spring Boot 시작** (자동 마이그레이션 포함)
   ```bash
   ./gradlew bootRun
   ```

3. **Flyway 마이그레이션 확인**
   ```bash
   psql -h localhost -U postgres -d newsquery
   SELECT * FROM flyway_schema_history;
   ```

4. **테스트**
   ```bash
   curl -X POST http://localhost:8080/api/queries/saved \
     -H "Content-Type: application/json" \
     -d '{"nql":"keyword(\"Bitcoin\") > 5","name":"My Query"}'
   ```

## 다음 단계

- **Phase 6**: Email/WebSocket/Push 알림 채널 구현
- **Phase 7**: Kafka 이벤트 스트리밍 + 동적 규칙
- **Phase 8**: 대시보드 및 통계 기능

## 참고 자료

- [Flyway 공식 문서](https://flywaydb.org/)
- [Spring Data JPA](https://spring.io/projects/spring-data-jpa)
- [PostgreSQL 13+ 문서](https://www.postgresql.org/docs/)
- [Hibernate 매핑 가이드](https://hibernate.org/orm/documentation/)
