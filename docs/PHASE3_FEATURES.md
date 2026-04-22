# Phase 3: 고급 기능 (집계 & 부스팅)

**완료일**: 2026-04-23  
**버전**: Phase 3 (Advanced Features)

## 개요

Phase 3는 NQL에 집계(Aggregation)와 부스팅(Boosting) 기능을 추가하여 더욱 강력한 데이터 분석 및 검색 경험을 제공합니다.

---

## 1️⃣ 집계 (Aggregation) 기능

### 목적
검색 결과를 특정 필드별로 그룹화하여 통계 제공

### 문법

```nql
<query> GROUP BY <field> [LIMIT <number>]
```

### 예제

#### 예제 1: 카테고리별 뉴스 개수
```nql
keyword("AI") GROUP BY category LIMIT 10
```

**응답 예**:
```json
{
  "aggregations": {
    "group_by_category": {
      "buckets": [
        { "key": "TECHNOLOGY", "doc_count": 245 },
        { "key": "BUSINESS", "doc_count": 189 },
        { "key": "SCIENCE", "doc_count": 142 }
      ]
    }
  }
}
```

#### 예제 2: 감성별 뉴스 분포
```nql
keyword("cryptocurrency") AND score >= 5.0 GROUP BY sentiment
```

**응답**:
```json
{
  "aggregations": {
    "group_by_sentiment": {
      "buckets": [
        { "key": "positive", "doc_count": 567 },
        { "key": "neutral", "doc_count": 234 },
        { "key": "negative", "doc_count": 123 }
      ]
    }
  }
}
```

#### 예제 3: 국가별 뉴스 (상위 5)
```nql
keyword("technology") GROUP BY country LIMIT 5
```

### 구현 상세

#### NQL 문법 확장
```antlr
query       : expr (groupByClause)? (limitClause)? EOF ;
groupByClause : GROUP BY field ;
limitClause : LIMIT NUMBER ;
```

#### IR 표현
```java
record AggregationExpr(
    NQLExpression expr,           // 기본 쿼리
    String groupByField,          // 그룹핑 필드
    Optional<Integer> limit       // 결과 수 제한
) implements NQLExpression {}
```

#### Elasticsearch 변환
```json
{
  "query": { ... },
  "aggs": {
    "group_by_<field>": {
      "terms": {
        "field": "<field>",
        "size": 10
      }
    }
  }
}
```

#### 구현 클래스
- `AggregationBuilder.java`: 집계 쿼리 생성
  - `buildAggregation()`: 집계 구조 생성
  - `buildQueryWithAggregation()`: 쿼리 + 집계 통합

### 성능 특성

| 항목 | 설명 |
|------|------|
| **정렬** | 자동으로 `_count` 기준 내림차순 |
| **제한** | LIMIT 미지정 시 기본값 10 |
| **캐싱** | 동일한 GROUP BY 쿼리는 캐시됨 |
| **메모리** | 크기 필드에는 약간의 오버헤드 |

---

## 2️⃣ 부스팅 함수 (Boosting Functions)

### 목적
검색 점수를 동적으로 조정하여 특정 뉴스를 우선화

### 지원되는 부스팅 함수

#### 1) RECENCY (최신성 부스팅)

**개념**: 최신 뉴스에 높은 점수 부여

```nql
keyword("AI") BOOST recency(publishedAt)
keyword("blockchain") BOOST recency(publishedAt, 7)
```

**수식**:
```
final_score = original_score × decay_function(days_since_publish)
```

- 최근일수록 1.0에 가까움
- 오래될수록 0.0에 가까움 (지수 감쇠)

**기본값**: 감쇠 기간 7일 (또는 사용자 지정)

**응답 예**:
```json
{
  "hits": [
    {
      "title": "최신 AI 뉴스",
      "publishedAt": "2024-04-23T10:00:00Z",
      "_score": 8.5
    },
    {
      "title": "지난 달 AI 뉴스",
      "publishedAt": "2024-03-20T10:00:00Z",
      "_score": 5.2
    }
  ]
}
```

#### 2) TREND (트렌드 부스팅)

**개념**: 트렌드 점수가 높은 뉴스 우선화

```nql
keyword("technology") BOOST trend(trend_score)
```

**수식**:
```
final_score = original_score × (1 + sqrt(trend_score / 100))
```

**필드 요구사항**: `trend_score` (0~100)

**예**:
- trend_score = 100 → 2배 부스팅
- trend_score = 25 → 1.5배 부스팅
- trend_score = 0 → 1배 (부스팅 없음)

#### 3) POPULARITY (인기도 부스팅)

**개념**: 조회수/공유수가 많은 뉴스 우선화

```nql
keyword("news") BOOST popularity(view_count)
```

**수식**:
```
final_score = original_score × (1 + log(1 + popularity_score))
```

**특징**: 로그 함수로 매우 인기 있는 뉴스가 과도하게 부스팅되지 않음

### 복합 부스팅

여러 부스팅을 조합:

```nql
keyword("AI") BOOST recency(publishedAt) BOOST trend(trend_score)
```

**점수 계산**:
```
final_score = original_score 
            × decay(publishedAt) 
            × sqrt(trend_score / 100)
```

### 구현 상세

#### BoostingFunction.java

**제공 메서드**:
```java
buildRecencyBoost(String dateField, int decayDays)
buildTrendBoost(String trendField)
buildPopularityBoost(String popularityField)
buildCompositeBoost(ObjectNode baseQuery, ObjectNode... boostFunctions)
buildFunctionScoreQuery(ObjectNode boolQuery, ObjectNode... boostFunctions)
```

#### Elasticsearch 변환

**기본 구조**:
```json
{
  "query": {
    "function_score": {
      "query": { ... },
      "functions": [
        {
          "gauss": {
            "publishedAt": {
              "origin": "now",
              "scale": "7d",
              "decay": 0.5
            }
          }
        },
        {
          "field_value_factor": {
            "field": "trend_score",
            "factor": 0.01,
            "modifier": "sqrt"
          }
        }
      ],
      "score_mode": "multiply",
      "boost_mode": "multiply"
    }
  }
}
```

### 성능 특성

| 함수 | 성능 영향 | 비고 |
|------|---------|------|
| RECENCY | 낮음 | 날짜 필드는 기본 인덱싱됨 |
| TREND | 중간 | 필드값 접근 필요 |
| POPULARITY | 중간 | log 함수 계산 |
| 복합 | 중간 | 함수 개수만큼 선형 증가 |

---

## 3️⃣ 실제 사용 예제

### 예제 1: 최신 기술 뉴스 검색

```nql
keyword("artificial intelligence") 
AND category == "TECHNOLOGY"
AND sentiment != "negative"
BOOST recency(publishedAt)
GROUP BY country
LIMIT 5
```

**동작**:
1. "artificial intelligence" 포함
2. 기술 카테고리
3. 부정 감성 제외
4. 최신 뉴스 우선
5. 국가별로 그룹화
6. 상위 5개만 반환

### 예제 2: 트렌드 기반 금융 뉴스

```nql
(keyword("cryptocurrency") OR keyword("blockchain"))
AND score >= 7.0
BOOST trend(trend_score)
GROUP BY sentiment
```

**응답 예**:
```json
{
  "hits": [
    {
      "title": "Bitcoin reaches new ATH",
      "_score": 12.5,
      "trend_score": 95,
      "sentiment": "positive"
    }
  ],
  "aggregations": {
    "group_by_sentiment": {
      "buckets": [
        { "key": "positive", "doc_count": 234 },
        { "key": "neutral", "doc_count": 89 },
        { "key": "negative", "doc_count": 12 }
      ]
    }
  }
}
```

### 예제 3: 인기 기사 추천

```nql
keyword("news")
AND publishedAt BETWEEN "2024-04-01" AND "2024-04-23"
BOOST popularity(view_count)
BOOST recency(publishedAt)
GROUP BY source
LIMIT 10
```

---

## 4️⃣ 기술 개선사항

### NQL 문법 확장
```antlr
// Phase 2
fieldExpr : field compOp value
          | field IN '[' valueList ']'
          | field BETWEEN value AND value
          ;

// Phase 3 추가
query : expr (groupByClause)? (limitClause)? EOF ;
groupByClause : GROUP BY field ;
limitClause : LIMIT NUMBER ;

keywordExpr : KEYWORD '(' STRING ')' ('*' NUMBER)? (boostFunc)? ;
boostFunc : BOOST boostType '(' boostArg ')' ;
boostType : RECENCY | TREND | POPULARITY ;
```

### 새로운 클래스

| 클래스 | 역할 |
|--------|------|
| `AggregationBuilder` | 집계 쿼리 생성 |
| `BoostingFunction` | 부스팅 함수 유틸 |
| `NQLExpression.AggregationExpr` | 집계 IR 표현 |

### 파이프라인 업데이트

```
NQL String
  → Parser (GROUP BY, BOOST 절 인식)
  → NQLExpression (AggregationExpr)
  → ESQueryBuilder + AggregationBuilder + BoostingFunction
  → Elasticsearch Query + Aggs + Function Score
  → Elasticsearch 실행
  → 결과 반환
```

---

## 5️⃣ 성능 비교 (예상)

### Phase 2 vs Phase 3

| 쿼리 | Phase 2 | Phase 3 | 변화 |
|-----|--------|--------|------|
| Simple keyword | 50ms | 50ms | ➡️ |
| Keyword + sentiment | 70ms | 70ms | ➡️ |
| GROUP BY (10개) | N/A | 80ms | ✨ 신규 |
| WITH BOOST | N/A | 100ms | ✨ 신규 |
| 복합 (GROUP + BOOST) | N/A | 150ms | ✨ 신규 |

> 예상값이며 실제 데이터 규모에 따라 달라집니다.

---

## 6️⃣ 다음 단계 (Phase 4 계획)

### 단기
- [ ] 초기 성능 측정 (실제 Elasticsearch)
- [ ] 성능 재측정 및 비교 분석
- [ ] 블로그 글 업데이트

### 중기
- [ ] 더 많은 부스팅 함수 (composite_boost 등)
- [ ] 저장된 검색 기능
- [ ] 검색 히스토리

### 장기
- [ ] 사용자 기반 알림 시스템
- [ ] 대시보드 기능
- [ ] 프로덕션 배포

---

## 📚 참고

### Elasticsearch 공식 문서
- [Terms Aggregation](https://www.elastic.co/guide/en/elasticsearch/reference/8.12/search-aggregations-bucket-terms-aggregation.html)
- [Function Score Query](https://www.elastic.co/guide/en/elasticsearch/reference/8.12/query-dsl-function-score-query.html)
- [Decay Functions](https://www.elastic.co/guide/en/elasticsearch/reference/8.12/query-dsl-decay-query.html)

### NQL 전체 지원 기능

**Phase 1**: 에러 처리, 모니터링  
**Phase 2**: BETWEEN, CONTAINS/LIKE  
**Phase 3**: GROUP BY, LIMIT, BOOST 함수  

---

**저작자**: Claude Code  
**작성일**: 2026-04-23  
**상태**: 개발 완료, 성능 측정 대기

