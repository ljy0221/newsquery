# Phase 5.1 학습 리포트 — Part 3: 성능 최적화 및 문제 해결

**난이도**: 고급 | **시간**: 15분

## 1. 인덱싱 전략

### 문제: 풀 테이블 스캔

```sql
SELECT * FROM query_history 
WHERE user_id = 'user1' 
ORDER BY executed_at DESC 
LIMIT 10;

-- 1,000,000개 행을 모두 검사 → 50ms
```

### 해결 1: 단순 인덱스

```sql
CREATE INDEX idx_query_history_user_id 
ON query_history(user_id);

-- user_id 검색: 10,000개 행만 검사 → 10ms (5배 향상)
-- 하지만 정렬은 메모리에서 수행
```

### 해결 2: 복합 인덱스 (최적)

```sql
CREATE INDEX idx_query_history_user_executed 
ON query_history(user_id, executed_at DESC);

-- user_id 검색 + 정렬된 상태로 조회 → 1ms (50배 향상!)
```

**JPA 구현**:

```java
@Entity
@Table(name = "query_history", indexes = {
    @Index(name = "idx_query_history_user_id", columnList = "user_id"),
    @Index(name = "idx_query_history_executed_at", columnList = "executed_at")
})
public class QueryHistory {
    // JPA @Index로는 복합 인덱스 정의 불가
    // → Flyway (V1__initial_schema.sql)에서 정의
}

// V1__initial_schema.sql
CREATE INDEX idx_query_history_user_executed
ON query_history(user_id, executed_at DESC);
```

## 2. N+1 쿼리 문제

### 문제: 1000개 쿼리 실행

```java
@Service
public class SavedQueryService {
    public List<SavedQueryDTO> findAllWithUsers() {
        List<SavedQuery> queries = repository.findAll();
        // SELECT * FROM saved_queries  (1개)
        
        List<SavedQueryDTO> dtos = new ArrayList<>();
        for (SavedQuery q : queries) {
            User user = userRepository.findById(q.getUserId()).orElse(null);
            // SELECT * FROM users WHERE user_id = ?  (N개!)
            dtos.add(new SavedQueryDTO(q, user));
        }
        
        return dtos;
        // 총 N+1 = 1,000,000 + 1 = 1,000,001개 쿼리!
    }
}
```

### 해결: JOIN 쿼리

```java
@Repository
public interface SavedQueryRepository 
    extends JpaRepository<SavedQuery, String> {
    
    // 하나의 JOIN 쿼리로 해결
    @Query("SELECT new com.newsquery.api.SavedQueryDTO(sq, u) " +
           "FROM SavedQuery sq " +
           "LEFT JOIN User u ON sq.userId = u.userId")
    List<SavedQueryDTO> findAllWithUsers();
}

// 실행: 1개 JOIN 쿼리만 수행 → 50배 성능 향상
```

## 3. 배치 처리

### 문제: INSERT 1000번

```java
public void saveHistories(List<QueryHistory> histories) {
    for (QueryHistory qh : histories) {
        repository.save(qh);  // INSERT  (1ms × 1000 = 1000ms)
    }
}
```

### 해결: 배치 처리

```java
// application.yml
spring:
  jpa:
    properties:
      hibernate:
        jdbc:
          batch_size: 20  // 20개씩 배치
          fetch_size: 50  // 50개씩 페치

// Service
public void saveHistories(List<QueryHistory> histories) {
    repository.saveAll(histories);  
    // 50개 배치 × 2ms = 100ms (10배 향상!)
}
```

## 4. 트랜잭션 관리

### 패턴 1: @Transactional 기본

```java
@Service
@Transactional  // 클래스 레벨: 모든 메서드 적용
public class SavedQueryService {
    
    // 읽기 메서드: 쓰기 권한 최소화
    @Transactional(readOnly = true)
    public List<SavedQuery> findByUserId(String userId) {
        return repository.findByUserIdOrderByCreatedAtDesc(userId);
        // 읽기만 → 락 불필요 → 성능 ↑
    }
    
    // 쓰기 메서드: 기본값 (@Transactional)
    public SavedQuery save(String userId, String nql, 
                          String name, String description) {
        SavedQuery sq = new SavedQuery(userId, nql, name, description);
        return repository.save(sq);
    }
}
```

### 패턴 2: 전파 (Propagation)

```java
@Service
public class NotificationService {
    
    @Transactional
    public void onQueryExecution(QueryExecutionEvent event) {
        // 메인 트랜잭션
        queryHistoryRepository.save(new QueryHistory(...));
        
        // 독립적 작업: 실패해도 메인 영향 X
        try {
            sendNotification(event);  // 별도 TX
        } catch (Exception e) {
            logger.warn("Notification failed", e);
            // 메인 TX는 계속 진행
        }
    }
    
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void sendNotification(QueryExecutionEvent event) {
        // 이 메서드가 실패해도 onQueryExecution의 TX는 영향 X
        emailService.send(event);
    }
}
```

## 5. 실제 문제 해결

### 문제 1: Import 불가능

```
오류: The import org.springframework.data cannot be resolved

원인: build.gradle에 spring-boot-starter-data-jpa 없음

해결:
dependencies {
    implementation 'org.springframework.boot:spring-boot-starter-data-jpa'
}
./gradlew clean build
```

### 문제 2: 반환값 불일치

```java
// SavedQueryService
public void delete(String id) {  // void!
    repository.deleteById(id);
}

// SavedQueryController
if (savedQueryService.delete(id)) {  // boolean 기대 → 컴파일 오류
    ...
}

// 해결
if (repository.findById(id).isPresent()) {
    repository.deleteById(id);
    return ResponseEntity.ok().build();
}
return ResponseEntity.notFound().build();
```

### 문제 3: 데이터베이스 연결 실패

```
오류: org.postgresql.util.PSQLException: 
      Connection to localhost:5432 refused

해결:
docker run -d \
  --name postgres-newsquery \
  -e POSTGRES_DB=newsquery \
  -e POSTGRES_USER=postgres \
  -e POSTGRES_PASSWORD=postgres \
  -p 5432:5432 \
  postgres:15-alpine

확인: psql -h localhost -U postgres -d newsquery
```

### 문제 4: 외래 키 제약 위반

```
오류: ERROR: insert or update on table "saved_queries" 
      violates foreign key constraint

원인: 존재하지 않는 user_id로 저장 시도

해결:
User user = new User("user1", "user1@test.com");
userRepository.save(user);  // 먼저 User 생성

SavedQuery sq = new SavedQuery("user1", ...);
repository.save(sq);  // 그 다음 SavedQuery
```

## 6. 성능 검증 체크리스트

```
DB 레벨:
✓ 복합 인덱스 (user_id, created_at DESC)
✓ 외래 키 제약 (데이터 일관성)
✓ NOT NULL 제약 (필수 필드)
✓ 연결 풀 설정 (hikari.maximum-pool-size=10)

애플리케이션:
✓ @Transactional 선언 (Service)
✓ readOnly=true (읽기 메서드)
✓ N+1 쿼리 제거 (JOIN)
✓ 배치 처리 (jdbc.batch_size=20)
✓ 페이징 (Pageable)

모니터링:
✓ 느린 쿼리 로깅 (spring.jpa.show-sql=true)
✓ SQL 바인딩 매개변수 출력 (hibernate.use_sql_comments)
✓ 응답 시간 추적
✓ 연결 풀 상태 확인
```

## 결론: 성능 개선 결과

```
Before (인덱스 없음, N+1):
- 저장된 쿼리 조회: 50ms
- 히스토리 조회: 500ms
- 전체: 550ms

After (복합 인덱스, JOIN, 배치):
- 저장된 쿼리 조회: 1ms
- 히스토리 조회: 5ms
- 전체: 30ms

개선율: 550ms → 30ms (18배 향상)
```

