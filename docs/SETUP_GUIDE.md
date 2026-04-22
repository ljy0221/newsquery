# 환경 설정 및 실행 가이드

## 1단계: 인프라 구성 (Docker Compose)

```bash
docker-compose up -d
```

**확인 체크리스트:**
- Elasticsearch: http://localhost:9200 (200 OK)
- Kafka UI: http://localhost:8888
- Zookeeper: localhost:2181

## 2단계: Elasticsearch 매핑 설정

```bash
python scripts/update_es_mapping.py
```

## 3단계: 임베딩 서비스 시작

```bash
python pipeline/embedding_service.py
```

**포트:** http://localhost:8000

## 4단계: Kafka Consumer 시작

```bash
python pipeline/news_worker.py
```

## 5단계: 데이터 프로듀서 시작

```bash
# GDELT 또는 RSS 선택
python pipeline/gdelt_producer.py
# 또는
python pipeline/rss_producer.py
```

## 6단계: Spring Boot 실행

```bash
./gradlew bootRun
```

**포트:** http://localhost:8080

## 7단계: 프론트엔드 실행

```bash
cd frontend
npm install
npm run dev
```

**포트:** http://localhost:3000
