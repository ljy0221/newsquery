# N-QL Intelligence API 가이드

## 개요

N-QL Intelligence는 JQL 스타일의 NQL(News Query Language)을 사용하여 뉴스를 검색하고 순위를 매기는 전문가용 엔진입니다.

**기본 URL**: `http://localhost:8080/api`

---

## 1. 뉴스 검색 API

### 요청

```http
POST /api/query
Content-Type: application/json

{
  "nql": "keyword(\"HBM\") AND sentiment != \"negative\"",
  "page": 0
}
```

### 파라미터

| 파라미터 | 타입 | 필수 | 설명 |
|---------|------|------|------|
| `nql` | string | ✅ | NQL 검색 쿼리 |
| `page` | integer | ✅ | 페이지 번호 (0부터 시작) |

### 응답

```json
{
  "total": 150,
  "hits": [
    {
      "id": "doc_12345",
      "title": "HBM 신제품 출시",
      "source": "Reuters",
      "sentiment": "positive",
      "country": "KR",
      "publishedAt": "2024-01-15T10:30:00Z",
      "score": 0.95,
      "url": "https://reuters.com/article/..."
    }
  ]
}
```

### 예제

#### 1) 기본 키워드 검색
```
GET /api/query?nql=keyword("AI")&page=0
```

#### 2) 감성 필터링
```
keyword("GPU") AND sentiment == "positive"
```

#### 3) 출처 제한
```
keyword("blockchain") AND source IN ["Reuters", "Bloomberg"]
```

#### 4) 날짜 범위 (범위 비교)
```
keyword("tech") AND publishedAt >= "2024-01-01" AND publishedAt <= "2024-12-31"
```

#### 5) 날짜 범위 (BETWEEN 연산자)
```
keyword("tech") AND publishedAt BETWEEN "2024-01-01" AND "2024-12-31"
```

#### 6) 패턴 매칭
```
source CONTAINS "Reuters"
source LIKE "Bloom%"
```

#### 7) 복합 조건
```
(keyword("AI") * 2.0 OR keyword("machine learning"))
AND sentiment != "negative"
AND country IN ["KR", "US"]
AND score > 5.0
```

---

## 2. NQL 문법

### 지원 연산자

| 연산자 | 설명 | 예시 |
|--------|------|------|
| `keyword()` | 텍스트 검색 | `keyword("HBM")` |
| `*` (boost) | 가중치 | `keyword("urgent") * 3.0` |
| `AND` | 논리곱 | `keyword("A") AND sentiment == "positive"` |
| `OR` | 논리합 | `sentiment == "positive" OR sentiment == "neutral"` |
| `!` | 부정 | `!sentiment == "negative"` |
| `==` | 같음 | `sentiment == "positive"` |
| `!=` | 다름 | `source != "spam"` |
| `>=`, `<=`, `>`, `<` | 비교 | `score >= 7.0` |
| `BETWEEN` | 범위 | `publishedAt BETWEEN "2024-01-01" AND "2024-12-31"` |
| `CONTAINS`, `LIKE` | 패턴 | `source CONTAINS "Reuters"` |
| `IN` | 포함 | `source IN ["Reuters", "Bloomberg"]` |
| `*` | 모든 뉴스 | `*` |

### 지원 필드

| 필드 | 타입 | 설명 | 예시 |
|------|------|------|------|
| `keyword` | text | 뉴스 본문 검색 | `keyword("AI")` |
| `sentiment` | enum | 감성 ("positive", "neutral", "negative") | `sentiment == "positive"` |
| `source` | string | 뉴스 출처 | `source IN ["Reuters"]` |
| `category` | string | GDELT 이벤트 분류 | `category == "TECH"` |
| `country` | string | 국가 코드 (ISO 3166-1) | `country IN ["KR", "US"]` |
| `publishedAt` | date | 발행 날짜 (ISO 8601) | `publishedAt >= "2024-01-01"` |
| `score` | float | GDELT Goldstein Scale | `score > 5.0` |

---

## 3. 성능 최적화

### 쿼리 팁

1. **키워드 가중치 활용**
   ```
   keyword("urgent") * 3.0  // 중요한 키워드에 가중치 부여
   ```

2. **필터 조합**
   ```
   keyword("news") AND sentiment == "positive"  // 필터로 범위 좁히기
   ```

3. **날짜 제한**
   ```
   publishedAt >= "2024-01-01"  // 최근 뉴스만 검색
   ```

### SLA

- **평균 응답 시간**: < 500ms
- **P99 응답 시간**: < 5s
- **최대 결과**: 1000건 (페이지네이션으로 접근)

### 캐싱

- 동일한 쿼리는 자동으로 캐시됨
- 캐시 TTL: 5분

---

## 4. 에러 처리

### 에러 응답 형식

```json
{
  "message": "상세 오류 메시지",
  "errorCode": "ERROR_CODE",
  "timestamp": 1642312800000,
  "path": "/api/query"
}
```

### 에러 코드

| 코드 | HTTP 상태 | 설명 |
|------|----------|------|
| `EMPTY_QUERY` | 400 | NQL 쿼리가 비어있음 |
| `NQL_PARSE_ERROR` | 400 | NQL 문법 오류 |
| `INVALID_ARGUMENT` | 400 | 잘못된 파라미터 |
| `ES_CONNECTION_ERROR` | 500 | Elasticsearch 연결 실패 |
| `INTERNAL_SERVER_ERROR` | 500 | 서버 내부 오류 |

### 예제

```bash
# 잘못된 쿼리
curl -X POST http://localhost:8080/api/query \
  -H "Content-Type: application/json" \
  -d '{"nql":"", "page":0}'

# 응답
{
  "message": "NQL 쿼리가 비어있습니다. 검색 조건을 입력해주세요.",
  "errorCode": "EMPTY_QUERY",
  "timestamp": 1642312800000,
  "path": "/api/query"
}
```

---

## 5. 헬스 체크

### 요청

```http
GET /api/health
```

### 응답

```json
{
  "status": "UP",
  "timestamp": 1642312800000,
  "components": {
    "elasticsearch": {
      "status": "UP",
      "version": "8.12.0"
    },
    "embedding": {
      "status": "UP"
    }
  }
}
```

### 상태값

- `UP`: 모든 서비스 정상
- `DEGRADED`: 일부 서비스 이상 (부분 기능만 동작)
- `DOWN`: 서비스 불가능

---

## 6. 로깅

### 성능 로그

```
INFO  - 전체 검색 완료: 234ms, 결과: 42건
DEBUG - NQL 파싱: 10ms
DEBUG - 쿼리 빌드: 5ms
DEBUG - 임베딩: 45ms
DEBUG - ES 검색: 174ms
```

### 느린 쿼리 로그

응답 시간이 5초를 초과하는 쿼리는 자동으로 WARN 레벨로 로깅됨.

---

## 7. 예제 코드

### cURL

```bash
# 기본 검색
curl -X POST http://localhost:8080/api/query \
  -H "Content-Type: application/json" \
  -d '{
    "nql": "keyword(\"HBM\")",
    "page": 0
  }'
```

### Python

```python
import requests

response = requests.post(
    'http://localhost:8080/api/query',
    json={
        'nql': 'keyword("AI") AND sentiment == "positive"',
        'page': 0
    }
)
data = response.json()
print(f"Total: {data['total']}")
for hit in data['hits']:
    print(f"  {hit['title']} ({hit['score']})")
```

### JavaScript

```javascript
fetch('http://localhost:8080/api/query', {
  method: 'POST',
  headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify({
    nql: 'keyword("blockchain") AND country IN ["KR", "US"]',
    page: 0
  })
})
.then(r => r.json())
.then(data => {
  console.log(`Found ${data.total} results`);
  data.hits.forEach(hit => console.log(hit.title));
});
```

---

## 지원

문제가 발생하면 다음을 확인하세요:

1. **헬스 체크 상태** - `/api/health` 확인
2. **로그** - 서버 로그에서 에러 메시지 확인
3. **NQL 문법** - 문법 섹션 검토

