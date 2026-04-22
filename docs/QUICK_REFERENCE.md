# 빠른 참고서 (Quick Reference)

자주 사용되는 명령어와 설정을 한눈에 볼 수 있는 치트시트입니다.

---

## 자주 사용하는 명령어

### Docker & 인프라

```bash
# 모든 서비스 시작
docker-compose up -d

# 서비스 상태 확인
docker-compose ps

# 특정 서비스 로그 확인
docker logs newsquery-elasticsearch
docker logs newsquery-kafka

# 모든 서비스 중지
docker-compose down

# 볼륨까지 삭제 (초기화)
docker-compose down -v
```

### Spring Boot 빌드 & 실행

```bash
# ANTLR4 파서 생성 (NQL.g4 수정 후)
./gradlew generateGrammarSource

# 빌드
./gradlew build

# 테스트 실행
./gradlew test

# 특정 테스트만 실행
./gradlew test --tests "com.newsquery.nql.NQLParserTest"

# 개발 서버 실행
./gradlew bootRun

# 캐시 삭제 후 빌드
./gradlew clean build
```

### Python 파이프라인

```bash
# 임베딩 서비스 시작 (port 8000)
python pipeline/embedding_service.py

# Kafka Consumer 시작
python pipeline/news_worker.py

# GDELT 프로듀서 시작
python pipeline/gdelt_producer.py

# RSS 프로듀서 시작
python pipeline/rss_producer.py

# ES 매핑 설정
python scripts/update_es_mapping.py

# 샘플 데이터 로드
python scripts/ingest_sample.py

# Iceberg 초기화
python scripts/setup_iceberg.py

# Iceberg 데이터 조회
python scripts/query_iceberg.py --recent 10
```

### 프론트엔드 (Next.js)

```bash
# 의존성 설치
npm install

# 개발 서버 실행 (port 3000)
npm run dev

# 프로덕션 빌드
npm run build

# 프로덕션 서버 실행
npm start

# 린트 확인
npm run lint

# 포맷팅
npm run format
```

---

## 자주 사용하는 API

### 검색 API

```bash
# 기본 쿼리
curl -X POST http://localhost:8080/api/query \
  -H "Content-Type: application/json" \
  -d '{"query": "keyword(\"AI\")", "page": 1, "size": 20}'

# 복잡한 쿼리
curl -X POST http://localhost:8080/api/query \
  -H "Content-Type: application/json" \
  -d '{
    "query": "keyword(\"AI\") AND sentiment = \"positive\" AND source IN [\"Reuters\", \"Bloomberg\"]",
    "page": 1,
    "size": 10
  }'
```

### Elasticsearch 조회

```bash
# 인덱스 정보
curl http://localhost:9200/news

# 인덱스 매핑
curl http://localhost:9200/news/_mapping | jq

# 인덱스 통계
curl http://localhost:9200/news/_stats | jq

# 문서 검색 (테스트)
curl -X POST http://localhost:9200/news/_search \
  -H "Content-Type: application/json" \
  -d '{"query": {"match_all": {}}, "size": 5}' | jq
```

### Kafka 모니터링

```bash
# Kafka UI 접속 (웹)
http://localhost:8888

# 토픽 목록
docker exec newsquery-kafka kafka-topics.sh --bootstrap-server localhost:9092 --list

# 토픽 상세 정보
docker exec newsquery-kafka kafka-topics.sh --bootstrap-server localhost:9092 --describe --topic news-raw

# 메시지 소비
docker exec newsquery-kafka kafka-console-consumer.sh \
  --bootstrap-server localhost:9092 \
  --topic news-raw \
  --from-beginning \
  --max-messages 10
```

---

## NQL 쿼리 문법 치트시트

### 기본 구조

```sql
field(operator) condition [AND|OR|NOT ...] [AND|OR|NOT ...]
```

### 필드별 쿼리

```sql
-- 텍스트 필드 (keyword)
keyword("artificial intelligence")
keyword("AI", "machine learning")

-- 동등 비교 (keyword)
sentiment = "positive"
sentiment != "negative"
source = "Reuters"

-- IN 연산자
source IN ["Reuters", "Bloomberg", "CNN"]
country IN ["US", "KR"]

-- 범위 비교 (numeric/date)
score > 5.0
score >= 7.5
score < 10
publishedAt >= "2024-01-01"
publishedAt < "2024-12-31"

-- 모든 문서
*
```

### 논리 연산

```sql
-- AND (모두 만족)
keyword("AI") AND sentiment = "positive"

-- OR (하나 이상 만족)
keyword("AI") OR keyword("blockchain")

-- NOT (부정)
NOT sentiment = "negative"
keyword("AI") AND NOT sentiment = "negative"

-- 괄호로 우선순위 지정
(keyword("CEO") OR keyword("leadership")) AND NOT sentiment = "negative"
```

### 자주 사용되는 쿼리

```sql
-- 1. 특정 키워드 검색
keyword("artificial intelligence")

-- 2. 긍정적인 뉴스만
keyword("earnings") AND sentiment = "positive"

-- 3. 특정 출처만
source IN ["Reuters", "Bloomberg"]

-- 4. 특정 국가 기사
country = "US"

-- 5. 최근 기사 (날짜 범위)
publishedAt >= "2024-01-01"

-- 6. 복합 조건
(keyword("AI") OR keyword("machine learning")) 
AND sentiment != "negative" 
AND source IN ["Reuters", "Bloomberg"]
AND publishedAt >= "2024-01-01"
```

---

## 포트 맵핑 요약

| 서비스 | 포트 | URL |
|---|---|---|
| **Spring Boot** | 8080 | http://localhost:8080 |
| **Next.js 프론트엔드** | 3000 | http://localhost:3000 |
| **FastAPI 임베딩** | 8000 | http://localhost:8000 |
| **Elasticsearch** | 9200 | http://localhost:9200 |
| **Kafka Broker** | 9092 | localhost:9092 |
| **Zookeeper** | 2181 | localhost:2181 |
| **Kafka UI** | 8888 | http://localhost:8888 |

---

## 환경 변수 기본값

### Elasticsearch

```properties
spring.elasticsearch.uris=http://localhost:9200
elasticsearch.index.name=news
elasticsearch.vector.field=content_vector
elasticsearch.vector.dimension=384
elasticsearch.vector.similarity=cosine
```

### Kafka

```python
KAFKA_BROKERS=localhost:9092
KAFKA_TOPIC=news-raw
KAFKA_GROUP_ID=news-worker
```

### 임베딩

```python
EMBEDDING_SERVICE_URL=http://localhost:8000
EMBEDDING_MODEL=sentence-transformers/all-MiniLM-L6-v2
EMBEDDING_DIMENSION=384
```

### 데이터 수집

```python
GDELT_INTERVAL=15  # 분
RSS_INTERVAL=5     # 분
MAX_BATCH_SIZE=100  # Kafka 메시지 배치
ES_BULK_SIZE=1000   # ES 인덱싱 배치
```

---

## 자주 발생하는 문제 해결

### 문제: Elasticsearch 503 Service Unavailable

```bash
# 재시작
docker restart newsquery-elasticsearch

# 또는 로그 확인
docker logs newsquery-elasticsearch
```

**원인:** 메모리 부족, JVM 에러

### 문제: NQL 파싱 실패

```bash
# ANTLR4 코드 재생성
./gradlew clean generateGrammarSource

# 빌드
./gradlew build
```

**원인:** NQL.g4 파일 수정 후 파서 미재생성

### 문제: 임베딩 타임아웃

```bash
# FastAPI 서비스 재시작
python pipeline/embedding_service.py

# 또는 테스트
curl http://localhost:8000/health
```

**원인:** 서비스 응답 느림, 메모리 부족

### 문제: Kafka 메시지 미처리

```bash
# Consumer 로그 확인
# news_worker.py 재시작
python pipeline/news_worker.py

# Kafka UI에서 Consumer Group lag 확인
http://localhost:8888
```

**원인:** Consumer 오류, Elasticsearch 저장 실패

---

## 성능 최적화 팁

### 쿼리 최적화

| 방법 | 효과 |
|---|---|
| 페이지네이션 사용 (size: 20) | 응답 시간 50% 감소 |
| 날짜 범위 필터 추가 | 검색 범위 축소 |
| OR 연산자 최소화 | 파싱 및 검색 시간 단축 |
| IN 연산자 대신 AND/OR 최소화 | 쿼리 복잡도 감소 |

### 시스템 최적화

| 항목 | 권장 값 |
|---|---|
| Elasticsearch 힙 메모리 | 4GB 이상 |
| Kafka 파티션 수 | 3~4 |
| Worker 병렬도 | CPU 코어 수 |
| RRF rank_constant | 60 (기본값) |
| RRF window_size | 100 (기본값) |

---

## 로그 레벨 설정

### Spring Boot (application.properties)

```properties
# 전체 로그 레벨
logging.level.root=INFO

# 프로젝트 로그 레벨
logging.level.com.newsquery=DEBUG

# 특정 클래스 로그
logging.level.com.newsquery.api.QueryController=TRACE
logging.level.com.newsquery.nql=DEBUG
```

### Python

```python
import logging

logging.basicConfig(
    level=logging.DEBUG,
    format='%(asctime)s - %(name)s - %(levelname)s - %(message)s'
)
```

---

## 유용한 도구 및 리소스

### 로컬 테스트 도구

```bash
# curl (HTTP 요청)
curl -X POST http://localhost:8080/api/query ...

# jq (JSON 포맷팅)
curl http://localhost:9200/news/_stats | jq

# 또는 Postman / Insomnia (GUI)
```

### 웹 UI

- **Kafka UI**: http://localhost:8888
- **Elasticsearch Kibana**: (별도 설치 필요)
- **프론트엔드**: http://localhost:3000

### 개발 도구

- **IntelliJ IDEA**: ANTLR4 플러그인으로 NQL.g4 파싱 테스트
- **VS Code**: Next.js, Python 개발
- **Excalidraw**: 다이어그램 편집

---

## 체크리스트: 새 프로젝트 시작

### 초기 설정 (첫 실행)

- [ ] Docker 설치 및 실행 중 확인
- [ ] `docker-compose up -d` 실행
- [ ] Elasticsearch 상태 확인: `curl http://localhost:9200`
- [ ] `./gradlew build` 실행
- [ ] `python scripts/update_es_mapping.py` 실행

### 개발 환경 구성

- [ ] Java 17 설치 확인
- [ ] Python 3.9+ 설치 확인
- [ ] Node.js 18+ 설치 확인
- [ ] IDE 설정 (IntelliJ/VS Code)
- [ ] Git 클론 및 초기화

### 서비스 시작

- [ ] `./gradlew bootRun` (Spring Boot)
- [ ] `python pipeline/embedding_service.py` (FastAPI)
- [ ] `cd frontend && npm run dev` (Next.js)
- [ ] 브라우저에서 `http://localhost:3000` 접속

### 테스트

- [ ] `./gradlew test` (단위 테스트)
- [ ] API 테스트: `POST /api/query`
- [ ] 프론트엔드 UI 테스트
- [ ] Kafka 메시지 확인 (Kafka UI)

---

**마지막 업데이트:** 2026-04-22
