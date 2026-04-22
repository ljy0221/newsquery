# 프로젝트 실행 스크린샷 가이드

이 문서는 N-QL Intelligence의 각 단계별 실행 화면을 보여주는 가이드입니다.

---

## 1. Docker Compose 시작

### 명령어

```bash
docker-compose up -d
```

### 예상 출력

```
Creating newsquery-zookeeper ... done
Creating newsquery-elasticsearch ... done
Creating newsquery-kafka ... done
Creating newsquery-kafka-ui ... done
```

### 상태 확인

```bash
docker-compose ps
```

**예상 출력:**

```
NAME                          COMMAND                  SERVICE             STATUS              PORTS
newsquery-elasticsearch       /bin/tini -- /usr/local/bin/docker-entrypoint.sh       elasticsearch       Up 2 minutes        9200/tcp, 9300/tcp
newsquery-kafka               /etc/confluent/docker/run   kafka               Up 2 minutes        0.0.0.0:9092->9092/tcp
newsquery-kafka-ui           node /app/dist/index.js   kafka-ui            Up 2 minutes        0.0.0.0:8888->8080/tcp
newsquery-zookeeper          /etc/confluent/docker/run   zookeeper           Up 2 minutes        2181/tcp, 2888/tcp, 3888/tcp
```

---

## 2. Spring Boot 빌드

### 문법 생성

```bash
./gradlew generateGrammarSource
```

**예상 출력:**

```
> Task :generateGrammarSource
ANTLR 4.13.1 ( Jun  8 2023 14:50:55 )
/path/to/src/main/antlr4/com/newsquery/NQL.g4
Generating abstract syntax tree visitor

BUILD SUCCESSFUL in 2s
```

### 빌드

```bash
./gradlew build
```

**예상 출력:**

```
> Task :compileJava
> Task :processResources
> Task :classes
> Task :jar
> Task :bootJar
> Task :test
BUILD SUCCESSFUL in 35s
```

### 개발 서버 실행

```bash
./gradlew bootRun
```

**예상 로그:**

```
  .   ____          _            __ _ _
 /\\ / ___'_ __ _ _(_)_ __  __ _ \ \ \ \
( ( )\___ | '_ | '_| | '_ \/ _` | \ \ \ \
 \\/  ___)| |_)| | | | | || (_| |  ) ) ) )
  '  |____| .__|_| |_|_| |_|\__, | / / / /
 =========|_|==============|___/=/_/_/_/
 :: Spring Boot ::                (v3.2.3)

2026-04-22 14:30:00.000 INFO com.newsquery.NewsQueryApplication : Starting NewsQueryApplication v0.0.1-SNAPSHOT
2026-04-22 14:30:00.100 INFO o.elasticsearch.client.RestClient : [RestClient] initialized with [http://localhost:9200]
2026-04-22 14:30:00.500 INFO o.s.b.a.w.s.WelcomePageHandlerMapping : Spring is here to stay
2026-04-22 14:30:00.600 INFO o.s.b.w.e.t.TomcatWebServer : Tomcat started on port(s): 8080

Successfully started application
```

---

## 3. Elasticsearch 확인

### Elasticsearch 상태 확인

```bash
curl http://localhost:9200
```

**예상 출력:**

```json
{
  "name": "newsquery-elasticsearch",
  "cluster_name": "docker-cluster",
  "cluster_uuid": "abc123...",
  "version": {
    "number": "8.12.0",
    "build_flavor": "default",
    "build_type": "docker",
    "build_hash": "...",
    "build_date": "2024-01-17T19:07:37.635161047Z",
    "build_snapshot": false,
    "lucene_version": "9.9.1",
    "minimum_wire_compatibility_version": "7.17.0",
    "minimum_index_compatibility_version": "7.0.0"
  },
  "tagline": "You Know, for Search"
}
```

### 인덱스 생성 및 매핑 확인

```bash
curl http://localhost:9200/news/_mapping
```

**예상 출력:**

```json
{
  "news": {
    "mappings": {
      "properties": {
        "title": { "type": "text" },
        "content": { "type": "text" },
        "content_vector": {
          "type": "dense_vector",
          "dims": 384,
          "index": true,
          "similarity": "cosine"
        },
        "source": { "type": "keyword" },
        "sentiment": { "type": "keyword" },
        "publishedAt": { "type": "date" }
      }
    }
  }
}
```

---

## 4. 데이터 임베딩 서비스

### FastAPI 임베딩 서비스 실행

```bash
python pipeline/embedding_service.py
```

**예상 로그:**

```
INFO:     Uvicorn running on http://0.0.0.0:8000 (Press CTRL+C to quit)
INFO:     Application startup complete
```

### 임베딩 서비스 테스트

```bash
curl -X POST http://localhost:8000/embed/single \
  -H "Content-Type: application/json" \
  -d '{"text": "artificial intelligence in healthcare"}'
```

**예상 출력:**

```json
{
  "embedding": [0.1234, -0.5678, ..., 0.0912],
  "model": "sentence-transformers/all-MiniLM-L6-v2",
  "dim": 384
}
```

---

## 5. Kafka 메시지 흐름

### 샘플 데이터 인덱싱

```bash
python scripts/ingest_sample.py
```

**예상 출력:**

```
Connecting to Elasticsearch at localhost:9200...
Index: news
✓ Successfully indexed 200 news documents
Index stats:
  - Total documents: 200
  - Index size: 1.2 MB
  - Average document size: 6.2 KB

Sample documents indexed:
  - "HBM Memory Demand Surge in AI Era" (Reuters)
  - "Quantum Computing Breakthrough" (Bloomberg)
  - "Tech Giant Announces New Products" (CNBC)
```

### Kafka 메시지 확인 (Kafka UI)

**URL:** http://localhost:8888

**화면 요소:**
- Topics: `news-raw` 토픽 선택
- Messages: 각 메시지의 key, value, offset 확인
- Consumer Groups: `news-worker` 그룹의 lag 모니터링

**메시지 샘플:**

```json
{
  "id": "gdelt_20240422_001",
  "title": "HBM Memory Demand Surge",
  "content": "Leading manufacturers report increased demand...",
  "source": "Reuters",
  "sentiment": "positive",
  "category": "TECHNOLOGY",
  "country": "US",
  "publishedAt": "2024-04-22T10:30:00Z"
}
```

---

## 6. NQL 쿼리 API 테스트

### API 요청

```bash
curl -X POST http://localhost:8080/api/query \
  -H "Content-Type: application/json" \
  -d '{
    "query": "keyword(\"AI\") AND sentiment = \"positive\"",
    "page": 1,
    "size": 10
  }'
```

### 예상 응답 (200 OK)

```json
{
  "total": 45,
  "page": 1,
  "size": 10,
  "items": [
    {
      "id": "news_20240422_001",
      "title": "AI Breakthrough in Healthcare",
      "content": "Researchers announced a major breakthrough...",
      "source": "Reuters",
      "sentiment": "positive",
      "publishedAt": "2024-04-22T10:30:00Z",
      "rffScore": 1.85,
      "category": "TECHNOLOGY",
      "country": "US"
    },
    {
      "id": "news_20240422_002",
      "title": "Machine Learning Improves Diagnosis",
      "content": "A new ML model shows promising results...",
      "source": "Bloomberg",
      "sentiment": "positive",
      "publishedAt": "2024-04-22T09:15:00Z",
      "rffScore": 1.62,
      "category": "HEALTH",
      "country": "US"
    }
  ]
}
```

### API 에러 응답 (400 Bad Request)

```json
{
  "error": "Query parsing failed",
  "message": "Unexpected token 'INVALID' at position 15",
  "query": "keyword(\"test\") INVALID sentiment = \"positive\""
}
```

---

## 7. 프론트엔드 Next.js UI

### 프론트엔드 실행

```bash
cd frontend
npm install
npm run dev
```

**예상 로그:**

```
> frontend@ dev
> next dev

  ▲ Next.js 14.0.3
  - Local:        http://localhost:3000
  - Environments: .env.local

✓ Ready in 1.2s
```

### 브라우저 접속

**URL:** http://localhost:3000

**페이지 요소:**

1. **검색 패널 (SearchPanel.tsx)**
   - NQL 쿼리 입력 텍스트 박스
   - "검색" 버튼
   - 페이지네이션 컨트롤

2. **결과 영역**
   - 뉴스 카드 목록 (NewsCard.tsx)
   - 각 카드에 표시되는 정보:
     - 제목
     - 요약 (content)
     - 출처 (source)
     - 감정 (sentiment 배지: positive/neutral/negative)
     - 발행 시간
     - RRF 점수
     - 카테고리, 국가

3. **로딩 상태**
   - 검색 중: 스핀너 표시
   - 에러: 에러 메시지 표시

### 쿼리 입력 예시

**입력:**

```
keyword("blockchain") AND source IN ["Reuters", "Bloomberg"]
```

**로드 시간:** ~500ms (RRF 스코어링 포함)

**결과 수:** 23개 (10개/페이지)

---

## 8. Iceberg 장기 저장소 확인

### Iceberg 데이터 조회

```bash
python scripts/query_iceberg.py --recent 10
```

**예상 출력:**

```
Connecting to Spark...
Reading Iceberg table: news_archive

Recent 10 documents:
┌─────────────────────────────────────────────────────────┐
│ ID              │ Title                    │ Source      │
├─────────────────────────────────────────────────────────┤
│ iceberg_001     │ AI Breakthrough...       │ Reuters     │
│ iceberg_002     │ Quantum Computing...     │ Bloomberg   │
│ iceberg_003     │ Tech Giant Announces...  │ CNBC        │
└─────────────────────────────────────────────────────────┘

Total documents in table: 45,230
```

### Iceberg 스냅샷 히스토리

```bash
python scripts/query_iceberg.py --snapshot
```

**예상 출력:**

```
Iceberg Table: news_archive

Snapshots:
┌────────────────────────────────────────────────────────┐
│ Snapshot ID │ Timestamp         │ Total Records         │
├────────────────────────────────────────────────────────┤
│ 123456789   │ 2026-04-22 14:30  │ 45,230                │
│ 123456788   │ 2026-04-22 14:00  │ 45,100                │
│ 123456787   │ 2026-04-22 13:30  │ 44,980                │
└────────────────────────────────────────────────────────┘
```

---

## 9. 모니터링 및 로그

### Spring Boot 로그 (bootRun 콘솔)

```
2026-04-22 14:35:10.123 INFO  c.n.api.QueryController : Processing query: keyword("AI") AND sentiment = "positive"
2026-04-22 14:35:10.245 DEBUG c.n.nql.NQLQueryParser : Parsed AST: BinaryExpr(AND, KeywordExpr([AI]), ComparisonExpr(sentiment, EQUALS, positive))
2026-04-22 14:35:10.356 DEBUG c.n.query.ESQueryBuilder : Built Query DSL: {"bool": {"must": [...]}}
2026-04-22 14:35:10.457 DEBUG c.n.embedding.EmbeddingClient : Embedding keywords: [AI]
2026-04-22 14:35:10.789 DEBUG c.n.scoring.RRFScorer : BM25 results: 45 hits, Vector results: 43 hits
2026-04-22 14:35:10.890 INFO  c.n.search.NewsSearchService : RRF scoring completed: 45 results
2026-04-22 14:35:10.950 INFO  c.n.api.QueryController : Response sent: 45 total, 10 items
```

### Elasticsearch 인덱스 통계

```bash
curl http://localhost:9200/news/_stats | jq '.indices.news'
```

**예상 출력:**

```json
{
  "primaries": {
    "docs": {
      "count": 200,
      "deleted": 5
    },
    "store": {
      "size_in_bytes": 1245000
    },
    "indexing": {
      "index_total": 205,
      "index_time_in_millis": 1234
    },
    "search": {
      "query_total": 128,
      "query_time_in_millis": 5678
    }
  }
}
```

---

## 10. 성능 벤치마크

### 쿼리 응답 시간 측정

```bash
time curl -X POST http://localhost:8080/api/query \
  -H "Content-Type: application/json" \
  -d '{
    "query": "keyword(\"AI\", \"machine learning\") AND sentiment != \"negative\"",
    "page": 1,
    "size": 20
  }'
```

**예상 출력:**

```
real    0m0.892s
user    0m0.123s
sys     0m0.045s
```

### 처리 단계별 시간 분석

| 단계 | 시간 | 설명 |
|---|---|---|
| NQL 파싱 | 5ms | ANTLR4 파서 |
| Query DSL 생성 | 2ms | ESQueryBuilder |
| 키워드 추출 | 3ms | KeywordExtractor |
| 벡터 임베딩 | 150ms | FastAPI 호출 |
| BM25 검색 | 180ms | Elasticsearch bool query |
| kNN 벡터 검색 | 160ms | Elasticsearch kNN |
| RRF 스코어링 | 40ms | RRFScorer 통합 |
| 결과 포장 | 5ms | JSON 직렬화 |
| **총계** | **~545ms** | **전체 응답 시간** |

---

## 문제 해결 (Troubleshooting)

### Elasticsearch 503 Service Unavailable

```bash
# 컨테이너 재시작
docker restart newsquery-elasticsearch

# 로그 확인
docker logs newsquery-elasticsearch
```

**예상 에러:**
```
ERROR: bootstrap checks failed. You must address the following before starting Elasticsearch:
```

**해결:** `docker-compose.yml`의 메모리 설정 확인

### NQL 파싱 오류

**입력:**
```
keyword("test") UNKNOW sentiment = "positive"
```

**에러 응답:**

```json
{
  "error": "Query parsing failed",
  "message": "Token UNKNOW is not recognized",
  "suggestion": "Did you mean 'AND' or 'OR'?"
}
```

### 임베딩 서비스 타임아웃

**에러:**
```
EmbeddingClient: Connection timeout to http://localhost:8000
```

**해결:**
```bash
# 서비스 재시작
python pipeline/embedding_service.py --port 8000
```

---

**마지막 업데이트:** 2026-04-22
