# Phase 5.1 학습 리포트 — Part 2: JPA 엔티티 설계 및 Repository

**난이도**: 중급 | **시간**: 15분

## 1. JPA 엔티티 설계 원칙

### 원칙 1: 엔티티 라이프사이클 이해

```java
// 1. Transient (영속화 안 됨)
SavedQuery q = new SavedQuery("user1", "query", "name", null);
// JPA 관리 X, DB 없음

// 2. Persistent (영속화됨)
SavedQuery saved = repository.save(q);
// DB에 INSERT, JPA 관리, 변경 자동 감지
saved.setName("new name");  // 자동으로 UPDATE 쿼리!

// 3. Detached (분리됨)
// 트랜잭션 완료 후
// saved 객체는 메모리에만 존재, JPA가 관리 안 함
saved.setName("another name");  // UPDATE 쿼리 안 발생 ⚠️
repository.save(saved);  // 명시적 저장 필요
```

### 원칙 2: 불변 식별자

```java
// ❌ 나쁜 예
@Entity
public class SavedQuery {
    @Id
    private String id;
    
    public void setId(String id) {  // ID 변경 가능 ❌
        this.id = id;
    }
}

// ✅ 좋은 예
@Entity
public class SavedQuery {
    @Id
    private final String id;
    
    public SavedQuery(String userId, String nql, ...) {
        this.id = UUID.randomUUID().toString();  // 한 번만
    }
    
    public String getId() { return id; }  // 읽기만
    // setId() 없음
}
```

**이유**: ID는 엔티티의 유일한 정체성. 변경되면 데이터 불일치 발생

### 원칙 3: 명시적 제약 조건

```java
@Entity
@Table(name = "saved_queries", indexes = {
    @Index(name = "idx_sq_user_id", columnList = "user_id")
})
public class SavedQuery {
    @Id
    @Column(name = "id")
    private String id;
    
    @Column(name = "user_id", nullable = false)
    private String userId;  // NOT NULL 제약
    
    @Column(columnDefinition = "TEXT", nullable = false)
    private String nql;  // TEXT, NOT NULL
    
    @Column(nullable = true)
    private String description;  // nullable
    
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;  // 생성 후 변경 불가
    
    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;  // 자동 수정 시간
}
```

## 2. Repository 패턴

### 자동 구현: 이름 기반 쿼리

```java
@Repository
public interface SavedQueryRepository 
    extends JpaRepository<SavedQuery, String> {
    
    // 1. 단순 조회
    List<SavedQuery> findByUserId(String userId);
    // → SELECT * FROM saved_queries WHERE user_id = ?
    
    // 2. 정렬
    List<SavedQuery> findByUserIdOrderByCreatedAtDesc(String userId);
    // → ORDER BY created_at DESC
    
    // 3. Optional 반환
    Optional<SavedQuery> findById(String id);
    // → 존재 여부 확인 가능
    
    // 4. 개수 세기
    long countByUserId(String userId);
    // → SELECT COUNT(*) ...
    
    // 5. 존재 확인
    boolean existsByIdAndUserId(String id, String userId);
    // → SELECT EXISTS(...)
}
```

### 복잡한 쿼리: @Query 사용

```java
@Repository
public interface QueryHistoryRepository 
    extends JpaRepository<QueryHistory, String> {
    
    // 1. WHERE + AND
    @Query("SELECT qh FROM QueryHistory qh " +
           "WHERE qh.userId = :userId AND qh.success = false")
    List<QueryHistory> findFailedQueriesByUserId(
        @Param("userId") String userId);
    
    // 2. 집계 함수
    @Query("SELECT AVG(qh.responseTimeMs) FROM QueryHistory qh " +
           "WHERE qh.userId = :userId")
    Double getAverageResponseTimeByUserId(
        @Param("userId") String userId);
    
    // 3. 수정 쿼리
    @Modifying
    @Transactional
    @Query("UPDATE QueryHistory qh SET qh.success = false " +
           "WHERE qh.id = :id")
    void markAsFailed(@Param("id") String id);
}
```

## 3. Phase 5.1의 엔티티 설계

### SavedQuery 엔티티

```java
@Entity
@Table(name = "saved_queries", indexes = {
    @Index(name = "idx_saved_queries_user_id", columnList = "user_id")
})
public class SavedQuery {
    @Id
    @Column(name = "id")
    private String id;  // UUID (분산 시스템 지원)

    @Column(name = "user_id", nullable = false)
    private String userId;  // 외래 키

    @Column(name = "nql", columnDefinition = "TEXT", nullable = false)
    private String nql;  // NQL은 길이 제한 없음

    @Column(name = "name")
    private String name;  // 선택적

    @Column(name = "is_favorite")
    private boolean isFavorite = false;  // 기본값

    @Column(name = "execution_count")
    private int executionCount = 0;

    @Column(name = "avg_response_time_ms")
    private double avgResponseTimeMs = 0.0;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    // 기본 생성자 (JPA 필수)
    public SavedQuery() {}

    // 값 생성자 (비즈니스 로직에서 사용)
    public SavedQuery(String userId, String nql, 
                      String name, String description) {
        this.id = UUID.randomUUID().toString();
        this.userId = userId;
        this.nql = nql;
        this.name = name;
        this.isFavorite = false;
        this.executionCount = 0;
        this.avgResponseTimeMs = 0.0;
    }

    // Getter/Setter
    public String getId() { return id; }
    public String getUserId() { return userId; }
    public void setFavorite(boolean favorite) { 
        this.isFavorite = favorite; 
    }
    // ... 기타 필드
}
```

### QueryHistory 엔티티 (복합 인덱스)

```java
@Entity
@Table(name = "query_history", indexes = {
    @Index(name = "idx_query_history_user_id", columnList = "user_id"),
    @Index(name = "idx_query_history_executed_at", columnList = "executed_at")
})
public class QueryHistory {
    @Id
    @Column(name = "id")
    private String id;

    @Column(name = "user_id", nullable = false)
    private String userId;

    @Column(name = "nql", columnDefinition = "TEXT", nullable = false)
    private String nql;

    @Column(name = "response_time_ms", nullable = false)
    private double responseTimeMs;

    @Column(name = "total_hits", nullable = false)
    private long totalHits;

    @Column(name = "success", nullable = false)
    private boolean success;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;  // nullable

    @CreationTimestamp
    @Column(name = "executed_at", nullable = false, updatable = false)
    private LocalDateTime executedAt;

    // 생성자들...
}

// 복합 인덱스 (Flyway에서 정의)
// CREATE INDEX idx_query_history_user_executed
// ON query_history(user_id, executed_at DESC);
```

## 4. 성능 최적화: TEXT vs VARCHAR

```java
// VARCHAR(255)
@Column(length = 255)
private String name;
// 스토리지: 사용한 만큼만 저장 (효율적)
// 용도: 제한된 길이의 문자열

// TEXT
@Column(columnDefinition = "TEXT")
private String nql;
// 스토리지: 페이지 단위 저장
// 용도: 임의로 긴 내용 (쿼리, 설명 등)

// 선택 기준:
// name (최대 100글자) → VARCHAR(255)
// nql (임의 길이) → TEXT
// description (최대 1000글자) → TEXT (안전성)
```

## 결론: 엔티티 설계 체크리스트

```
✓ 기본 생성자 + 값 생성자
✓ ID는 불변 (final, setter 없음)
✓ NOT NULL 제약으로 필수 필드 표시
✓ TEXT vs VARCHAR 구분
✓ 인덱스 정의 (성능)
✓ @CreationTimestamp/@UpdateTimestamp
✓ 외래 키 명시 (Repository 사용 권장)
✓ 정규화 준수 (중복 제거)
```

