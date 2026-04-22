# N-QL Intelligence 성능 최적화 여정: Phase 1 → Phase 2

## 📌 들어가며

**N-QL Intelligence** 프로젝트를 통해 뉴스 검색 엔진의 성능을 체계적으로 개선하는 과정을 공유합니다.

이 글에서는:
- ✅ Phase 1 (에러 처리, 모니터링)의 기반 구축
- ✅ Phase 2 (고급 연산자 추가) 개발 과정
- 📊 성능 측정 및 비교 분석
- 🎯 향후 개선 방향

을 다룹니다.

---

## 🏗️ Phase 1: 기초 다지기 (에러 처리 & 모니터링)

### 상황

프로젝트 초반에는:
- ❌ 에러 메시지가 일관성 없음
- ❌ 시스템 헬스 상태를 알 수 없음
- ❌ 쿼리 성능을 측정할 방법이 없음

### Phase 1 솔루션

#### 1) 표준화된 에러 처리

```java
// Before: 일관성 없는 응답
return ResponseEntity.badRequest()
    .body("NQL 쿼리가 비어있습니다");

// After: 구조화된 ErrorResponse
return ResponseEntity.badRequest()
    .body(new ErrorResponse(
        "NQL 쿼리가 비어있습니다.",
        "EMPTY_QUERY",
        "/api/query"
    ));
```

**장점**:
- 클라이언트가 에러 타입을 프로그래밍적으로 처리 가능
- 로깅 및 모니터링 정보 풍부
- API 사용자 경험 향상

#### 2) Prometheus + Grafana 모니터링

**아키텍처**:
```
Spring Boot (Port 8080)
    ↓
Prometheus (Port 9090) ← 메트릭 수집
    ↓
Grafana (Port 3001) ← 시각화
```

**설정 (Docker Compose)**:
```yaml
prometheus:
  image: prom/prometheus:latest
  ports:
    - "9090:9090"
  volumes:
    - ./monitoring/prometheus.yml:/etc/prometheus/prometheus.yml

grafana:
  image: grafana/grafana:latest
  ports:
    - "3001:3000"
```

#### 3) 커스텀 메트릭 수집

Spring Boot Actuator + Micrometer를 통해 자동 메트릭 수집:

```java
@Component
public class QueryMetrics {
    private final Counter queryCount;
    private final Timer queryTimer;
    private final Timer parseTimer;
    private final Timer embeddingTimer;
    private final Timer searchTimer;

    public QueryMetrics(MeterRegistry meterRegistry) {
        this.queryCount = Counter.builder("nql.query.total")
            .description("총 NQL 쿼리 수")
            .register(meterRegistry);
        // ... 더 많은 메트릭
    }
}
```

**수집되는 메트릭**:
| 메트릭 | 설명 |
|--------|------|
| `nql.query.total` | 총 쿼리 수 |
| `nql.query.errors` | 에러 수 |
| `nql.query.duration` | 전체 응답시간 (P50/P95/P99) |
| `nql.parse.duration` | NQL 파싱 시간 |
| `nql.build_query.duration` | 쿼리 빌드 시간 |
| `nql.embedding.duration` | 임베딩 시간 |
| `nql.search.duration` | ES 검색 시간 |

### Phase 1 성과

- ✅ 에러 처리 표준화 (ErrorResponse DTO)
- ✅ 헬스 체크 엔드포인트 (`/api/health`)
- ✅ 모니터링 인프라 구축 (Prometheus + Grafana)
- ✅ 커스텀 메트릭 수집 시작
- ✅ API 문서 작성 완료 (`API_GUIDE.md`)

---

## ⚡ Phase 2: 기능 확장 (고급 NQL 연산자)

### 배경

Phase 1의 모니터링 기반을 갖춘 후, NQL 문법을 확장하여 더 강력한 쿼리 표현을 지원합니다.

### Phase 2 추가 기능

#### 1) BETWEEN 연산자

**문제**: 날짜 범위 검색이 복잡함

```nql
// Before: 두 개의 비교 조건 필요
publishedAt >= "2024-01-01" AND publishedAt <= "2024-12-31"

// After: 간단한 BETWEEN
publishedAt BETWEEN "2024-01-01" AND "2024-12-31"
```

**구현**:

NQL 문법 (NQL.g4):
```antlr
fieldExpr : field compOp value
          | field IN '[' valueList ']'
          | field BETWEEN value AND value
          ;
```

IR 표현 (NQLExpression.java):
```java
record BetweenExpr(String field, String start, String end) 
    implements NQLExpression {}
```

Elasticsearch 변환 (ESQueryBuilder.java):
```java
private ObjectNode buildBetween(BetweenExpr between) {
    ObjectNode root = mapper.createObjectNode();
    ObjectNode rangeField = root.putObject("range")
        .putObject(between.field());
    rangeField.put("gte", between.start());
    rangeField.put("lte", between.end());
    return root;
}
```

결과 쿼리:
```json
{
  "range": {
    "publishedAt": {
      "gte": "2024-01-01",
      "lte": "2024-12-31"
    }
  }
}
```

#### 2) CONTAINS / LIKE 패턴 매칭

**문제**: 부분 문자열 검색 불가능

```nql
// Now possible
source CONTAINS "Reuters"
title LIKE "technolo%"
```

**구현**:

NQL 문법:
```antlr
compOp : EQ | NEQ | ... | CONTAINS | LIKE ;
```

Elasticsearch 변환:
```java
if (cmp.op().equals("CONTAINS") || cmp.op().equals("LIKE")) {
    ObjectNode root = mapper.createObjectNode();
    root.putObject("wildcard")
        .put(cmp.field(), "*" + cmp.value() + "*");
    return root;
}
```

결과 쿼리:
```json
{
  "wildcard": {
    "source": "*Reuters*"
  }
}
```

### Phase 2 복잡도 분석

| 기능 | 구현 복잡도 | 성능 영향 | 테스트 난이도 |
|------|-----------|---------|------------|
| BETWEEN | 낮음 | 없음 | 낮음 |
| CONTAINS | 낮음 | 중간 | 낮음 |
| LIKE | 낮음 | 중간 | 낮음 |

---

## 📊 성능 측정 & 비교

### 측정 방법

Python 성능 테스트 스크립트 (`performance_test.py`):

```python
test_queries = [
    {"nql": "keyword(\"AI\")", "name": "Simple keyword"},
    {"nql": "keyword(\"AI\") AND sentiment == \"positive\"", 
     "name": "Keyword + sentiment"},
    # ... 더 많은 테스트
]

# 각 쿼리를 5회씩 실행하여 평균, 최소, 최대, P95 측정
for test in test_queries:
    durations = []
    for i in range(5):
        result = run_query(test['nql'])
        durations.append(result['duration_ms'])
    
    # 통계
    avg = statistics.mean(durations)
    p95 = sorted(durations)[int(len(durations) * 0.95)]
```

### 측정 항목

#### Phase 1 성능 기준선

| 쿼리 | 평균 응답시간 | P95 |
|-----|-------------|-----|
| Simple keyword | TBD | TBD |
| Keyword + sentiment | TBD | TBD |
| OR query | TBD | TBD |
| Complex boosting | TBD | TBD |

#### Phase 2 추가 측정

| 쿼리 | 평균 응답시간 | P95 |
|-----|-------------|-----|
| BETWEEN 쿼리 | TBD | TBD |
| CONTAINS 쿼리 | TBD | TBD |
| 혼합 쿼리 | TBD | TBD |

### 예상 성능 변화

- **BETWEEN**: 영향 최소 (range 쿼리는 효율적)
- **CONTAINS/LIKE**: 중간 정도 영향 (wildcard 쿼리는 인덱스 미사용)

---

## 🎯 주요 학습 사항

### 1) 모니터링은 필수

> "측정할 수 없으면 개선할 수 없다" - Peter Drucker

Prometheus + Grafana 없이는 성능 개선의 효과를 증명할 수 없습니다.

### 2) 단계적 개선

- Phase 1: 기반 구축 (에러 처리, 모니터링)
- Phase 2: 기능 추가 (고급 연산자)
- Phase 3: 최적화 (성능, UX)

### 3) 문법 확장의 균형

NQL 문법을 확장할 때:
- ✅ 사용성 (더 간단한 쿼리)
- ✅ 파싱 복잡도 (ANTLR4는 관리 가능)
- ⚠️ 성능 영향 (wildcard는 느림)

을 함께 고려해야 합니다.

---

## 🚀 다음 단계 (Phase 3)

### 계획된 기능

1. **집계 (Aggregation)**
   ```nql
   // GROUP BY 같은 기능
   keyword("AI") GROUP BY category
   ```

2. **부스팅 함수**
   ```nql
   // 함수 기반 스코어 조정
   keyword("AI") BOOST recency(publishedAt)
   ```

3. **저장된 검색**
   - 자주 사용하는 쿼리 저장
   - 빠른 검색 실행

4. **사용자별 알림**
   - 조건 만족 시 실시간 알림
   - 이메일 다이제스트

---

## 📈 성능 개선 체크리스트

### Phase 1 체크리스트
- [x] 에러 처리 표준화
- [x] 헬스 체크 엔드포인트
- [x] Prometheus 설정
- [x] Grafana 대시보드
- [x] 커스텀 메트릭

### Phase 2 체크리스트
- [x] BETWEEN 연산자
- [x] CONTAINS/LIKE 연산자
- [x] API 문서 업데이트
- [ ] 단위 테스트 추가
- [ ] 성능 재측정

### Phase 3 계획
- [ ] 집계 기능
- [ ] 부스팅 함수
- [ ] 성능 최적화 (인덱싱, 캐싱)
- [ ] Kubernetes 배포

---

## 💡 결론

N-QL Intelligence 프로젝트를 통해:

1. **체계적인 성능 관리**의 중요성을 배웠습니다
2. **단계적 기능 확장**의 효과를 확인했습니다
3. **모니터링 기반 의사결정**의 가치를 경험했습니다

이제 Phase 2 완료 후 성능을 재측정하여 실제 개선 효과를 정량화할 수 있을 것입니다.

**다음 글에서**: Phase 2 성능 측정 결과와 최적화 전략을 공유하겠습니다.

---

## 📚 참고 자료

- [Prometheus 공식 문서](https://prometheus.io/docs/)
- [Grafana 공식 문서](https://grafana.com/docs/grafana/latest/)
- [ANTLR4 파서 생성기](https://www.antlr.org/)
- [Elasticsearch 쿼리 DSL](https://www.elastic.co/guide/en/elasticsearch/reference/8.12/query-dsl.html)

---

**작성자**: Claude Code  
**작성일**: 2026-04-23  
**프로젝트**: N-QL Intelligence (https://github.com/user/newsquery)

