# Phase 5.1 학습 리포트 — Part 1: ACID와 정규화 이론

**난이도**: 중급 | **시간**: 15분

## 1. ACID 특성

관계형 DB의 4가지 핵심 특징:

### Atomicity (원자성)
트랜잭션은 "모두 성공" 또는 "모두 실패"

```java
@Transactional
public SavedQuery saveAndRecordHistory(String userId, String nql) {
    SavedQuery sq = repository.save(new SavedQuery(...));
    QueryHistory qh = historyRepository.save(new QueryHistory(...));
    return sq;  // 둘 다 성공 또는 둘 다 실패
}
```

### Consistency (일관성)
모든 데이터는 정의된 제약을 만족

```java
// 외래 키 제약
SavedQuery sq = new SavedQuery("non-existent-user", ...);
repository.save(sq);  // ConstraintViolationException!
```

### Isolation (고립성)
동시 트랜잭션이 서로 간섭하지 않음

```java
// TX1: 읽기 (공유 락)
@Transactional(readOnly = true)
public List<SavedQuery> findByUserId(String userId) { ... }

// TX2: 쓰기 (배타 락)
@Transactional
public void update(String id, String name) { ... }
// TX1이 완료될 때까지 대기
```

### Durability (지속성)
커밋된 데이터는 영구 보존

```
1. WAL (Write-Ahead Log)에 기록
2. 메모리 버퍼에 적용
3. COMMIT 응답
4. 디스크 Flush

→ 서버 다운 시 WAL에서 복구 가능
```

## 2. 정규화 (1NF, 2NF, 3NF)

### 1NF: 원자성
각 속성은 분해 불가능해야 함

```java
// ❌ 위반
@Column
private String keywords;  // "BTC,ETH,AI"

// ✅ 준수
@Entity
public class KeywordSubscription {
    @Column
    private String keyword;  // 단일 값
}
```

### 2NF: 부분 함수 종속 제거
비키 속성은 키 전체에 종속, 키 일부에만 종속 불가

```java
// ❌ 위반
@Entity
public class SavedQueryWithEmail {
    @Id
    private String queryId;
    
    @Column
    private String userId;        // PK 일부
    
    @Column
    private String userEmail;     // userId만으로 결정 (중복)
}

// ✅ 준수
@Entity public class SavedQuery { @Column private String userId; }
@Entity public class User { @Column private String email; }
```

### 3NF: 이행 함수 종속 제거
비키 속성 간에 함수 종속 없음

```java
// ❌ 위반: queryId → userId → planType → diskQuota (이행)
@Column
private String diskQuota;  // planType에만 의존

// ✅ 준수
@Entity public class SavedQuery { @Column private String userId; }
@Entity public class User { @Column private String planType; }
@Entity public class Plan { @Column private String diskQuota; }
```

## 3. ORM의 필요성

### Before (Pure JDBC) — 300줄 매핑 코드
```java
String sql = "SELECT id, user_id, nql, ... FROM saved_queries WHERE user_id = ?";
try (Connection conn = ds.getConnection();
     PreparedStatement stmt = conn.prepareStatement(sql)) {
    stmt.setString(1, userId);
    ResultSet rs = stmt.executeQuery();
    while (rs.next()) {
        SavedQuery sq = new SavedQuery();
        sq.setId(rs.getString("id"));
        sq.setUserId(rs.getString("user_id"));
        sq.setNql(rs.getString("nql"));
        // ... 8개 필드 더 수동 매핑
        result.add(sq);
    }
}
```

**문제**: 반복적 코드, NULL 처리, 오류 위험, 타입 안전 부족

### After (JPA) — 2줄
```java
@Repository
public interface SavedQueryRepository 
    extends JpaRepository<SavedQuery, String> {
    List<SavedQuery> findByUserId(String userId);
}

// 사용
repository.findByUserId(userId);
```

**장점**: 선언적, 타입 안전, 자동 캐싱, 변경 감지

## 결론

```
메모리 저장 (Phase 5)
  ❌ 서버 재시작 → 데이터 손실
  ❌ 복잡한 쿼리 불가능
  ❌ ACID 보장 부족
  
PostgreSQL + JPA (Phase 5.1)
  ✅ 영구 저장 (ACID 보장)
  ✅ 복잡한 JOIN 가능
  ✅ 자동 트랜잭션 관리
  ✅ 마이크로서비스 확장 가능
```

