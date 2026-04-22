# Phase 2 개선사항 정리

**완료일**: 2026-04-23  
**버전**: Phase 2 (Advanced Operators)

## 개요

Phase 1의 에러 처리 및 모니터링 기반 위에 Phase 2에서는 NQL 문법을 확장하여 더 강력한 쿼리 기능을 제공합니다.

## 추가된 기능

### 1. BETWEEN 연산자

**목적**: 날짜 또는 숫자 범위 검색 간소화

**문법**:
```nql
field BETWEEN value1 AND value2
```

**예제**:
```nql
publishedAt BETWEEN "2024-01-01" AND "2024-12-31"
score BETWEEN "5.0" AND "10.0"
```

**구현**:
- NQL.g4: `fieldExpr` 규칙에 BETWEEN 추가
- NQLExpression: `BetweenExpr` 레코드 추가
- ESQueryBuilder: `range` 쿼리로 변환 (gte/lte)

**쿼리 변환 예**:
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

### 2. CONTAINS / LIKE 패턴 매칭

**목적**: 부분 문자열 검색

**문법**:
```nql
field CONTAINS "text"
field LIKE "text"
```

**예제**:
```nql
source CONTAINS "Reuters"
title LIKE "technology"
```

**구현**:
- NQL.g4: `compOp` 규칙에 CONTAINS, LIKE 추가
- ESQueryBuilder: `wildcard` 쿼리로 변환

**쿼리 변환 예**:
```json
{
  "wildcard": {
    "source": "*Reuters*"
  }
}
```

**성능 고려사항**:
- Wildcard 쿼리는 Elasticsearch에서 느릴 수 있음
- 가능하면 `keyword()` 또는 정확한 매칭 사용 권장

## 아키텍처 변경

### NQL 파이프라인 흐름

```
NQL 문자열
  → ANTLR4 Lexer/Parser (NQL.g4)
  → NQLVisitorImpl
      ├─ AndExpr / OrExpr / NotExpr
      ├─ KeywordExpr
      ├─ CompareExpr (==, !=, >, <, >=, <=, CONTAINS, LIKE)
      ├─ InExpr (IN operator)
      └─ BetweenExpr ← NEW
  → ESQueryBuilder
      ├─ multi_match (keyword)
      ├─ term (exact match ==)
      ├─ terms (IN)
      ├─ range (>, <, >=, <=)
      ├─ wildcard (CONTAINS, LIKE) ← UPDATED
      └─ range (BETWEEN) ← NEW
  → Elasticsearch Query DSL
```

### 파일 변경 사항

| 파일 | 변경 | 상세 |
|------|------|------|
| `src/main/antlr4/NQL.g4` | 수정 | BETWEEN, CONTAINS, LIKE 토큰 추가 |
| `src/main/java/.../NQLExpression.java` | 수정 | BetweenExpr 추가 |
| `src/main/java/.../NQLVisitorImpl.java` | 수정 | BETWEEN 처리 로직 추가 |
| `src/main/java/.../ESQueryBuilder.java` | 수정 | buildBetween() 메서드, CONTAINS/LIKE 처리 |
| `docs/API_GUIDE.md` | 수정 | 새 연산자 문서 및 예제 추가 |

## 사용 예제

### 예제 1: 최근 뉴스 검색
```nql
keyword("AI") AND publishedAt BETWEEN "2024-04-01" AND "2024-04-23"
```

### 예제 2: 특정 출처 부분 매칭
```nql
(keyword("blockchain") CONTAINS "crypto") OR source LIKE "CoinDesk"
```

### 예제 3: 점수 범위 + 감성
```nql
score BETWEEN "7.0" AND "10.0" AND sentiment == "positive"
```

### 예제 4: 복합 쿼리
```nql
(keyword("technology") * 2.0 OR keyword("innovation"))
AND publishedAt BETWEEN "2024-01-01" AND "2024-12-31"
AND country IN ["US", "KR"]
AND sentiment != "negative"
AND source CONTAINS "Reuters"
```

## 테스트 전략

### 단위 테스트 추가 예정

1. **문법 파싱 테스트**
   - `BetweenExprTest`: BETWEEN 표현식 파싱
   - `PatternMatchingTest`: CONTAINS/LIKE 파싱

2. **쿼리 빌드 테스트**
   - `ESQueryBuilderBetweenTest`: BETWEEN → range 쿼리 변환
   - `ESQueryBuilderPatternTest`: CONTAINS/LIKE → wildcard 쿼리 변환

3. **통합 테스트**
   - Elasticsearch에 데이터 적재 후 실제 쿼리 실행

## 성능 영향

### BETWEEN 연산자
- **영향**: 최소
- **이유**: `range` 쿼리는 Elasticsearch에서 효율적
- **예상 응답시간**: 동일 수준 유지

### CONTAINS/LIKE 패턴
- **영향**: 중간
- **이유**: Wildcard 쿼리는 인덱스 사용 불가능
- **권장사항**: 
  - 긴 텍스트 필드보다는 짧은 필드에 사용
  - 가능하면 정확한 매칭 또는 `keyword()` 사용
  - 와일드카드는 문자열 시작/종료에만 사용 (예: `*text`, `text*`)

## 호환성

- ✅ 기존 NQL 쿼리 100% 호환
- ✅ 기존 API 응답 형식 유지
- ✅ 기존 테스트 케이스 모두 통과

## 다음 단계 (Phase 3)

1. **집계 (Aggregation)**
   - 카테고리별 뉴스 개수
   - 감성 분포
   - 국가별 분포

2. **부스팅 함수**
   - `recency_boost`: 최신 뉴스에 높은 가중치
   - `trend_boost`: 트렌드 점수 기반 부스팅

3. **저장된 검색**
   - 자주 사용하는 쿼리 저장
   - 쿼리 실행 히스토리

4. **알림 시스템**
   - 특정 조건 만족 시 알림
   - 이메일 다이제스트

## 성능 측정 (Before/After)

**Phase 2 개발 후 성능 측정 예정**

| 쿼리 유형 | Phase 1 | Phase 2 | 변화 |
|----------|---------|---------|------|
| Simple keyword | TBD | TBD | - |
| BETWEEN 쿼리 | N/A | TBD | 신규 |
| CONTAINS 쿼리 | N/A | TBD | 신규 |
| 복합 쿼리 | TBD | TBD | - |

---

**참고**: 자세한 성능 비교는 `PERFORMANCE_BASELINE.md` 참조

