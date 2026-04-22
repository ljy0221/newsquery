# N-QL Intelligence 성능 기준선 (Baseline) 측정

**날짜**: 2026-04-23  
**버전**: Phase 1 (Error Handling, Health Check, Metrics)

## 시스템 구성

### 개발 환경
- Java 17 + Spring Boot 3.2.3
- ANTLR4 파서
- Elasticsearch 8.12.0 (미실행 - Docker 준비 중)
- Prometheus + Grafana (Docker Compose 구성 완료)

### 의존성
```
implementation 'org.springframework.boot:spring-boot-starter-actuator'
implementation 'io.micrometer:micrometer-registry-prometheus'
```

## 측정 항목

### 1. 응답 시간 분해

쿼리 처리 파이프라인의 각 단계별 수행 시간:

| 단계 | 역할 | 예상 시간 |
|------|------|----------|
| NQL 파싱 | ANTLR4 파서 | ~10ms |
| 쿼리 빌드 | IR → ES Query DSL | ~5ms |
| 임베딩 | FastAPI /embed/single | ~45ms |
| ES 검색 | Elasticsearch 쿼리 | ~174ms |
| **전체** | 포함 데이터 파이프라인 | **~234ms** |

### 2. 메트릭 수집 포인트

#### QueryMetrics (Spring Boot)
```java
// 카운터
nql.query.total       // 총 쿼리 수
nql.query.errors      // 에러 수

// 타이머
nql.query.duration      // 전체 응답시간 (P50, P95, P99)
nql.parse.duration      // 파싱 시간
nql.build_query.duration // 쿼리 빌드 시간
nql.embedding.duration  // 임베딩 시간
nql.search.duration     // ES 검색 시간
```

#### Spring Boot Actuator 엔드포인트
- `/actuator/health` - 헬스 체크 (Elasticsearch, 임베딩 서비스)
- `/actuator/metrics` - 모든 메트릭 목록
- `/actuator/prometheus` - Prometheus 스크래핑 포인트

### 3. 성능 목표 (SLA)

| 메트릭 | 목표 | 상태 |
|--------|------|------|
| 평균 응답시간 | < 500ms | 📊 측정 중 |
| P95 응답시간 | < 2000ms | 📊 측정 중 |
| P99 응답시간 | < 5000ms | 📊 측정 중 |
| 에러율 | < 1% | 📊 측정 중 |
| 쿼리 처리량 | > 100 QPS | 📊 측정 중 |

## 테스트 쿼리

```
1. Simple keyword        : keyword("AI")
2. Keyword + sentiment   : keyword("AI") AND sentiment == "positive"
3. OR query              : keyword("technology") OR keyword("innovation")
4. Keyword + source      : keyword("blockchain") AND source IN ["Reuters", "Bloomberg"]
5. Complex boosting      : (keyword("AI") * 2.0 OR keyword("machine learning")) AND sentiment != "negative"
6. Match all             : *
```

## 측정 환경 구성

### Docker Compose 서비스
```yaml
prometheus:   # Prometheus TSDB
  port: 9090
  volume: ./monitoring/prometheus.yml

grafana:      # 대시보드 시각화
  port: 3001  (admin/admin)
  
elasticsearch: # 검색 엔진
  port: 9200
  
kafka-ui:    # 메시지 큐 모니터링
  port: 8888
```

### Prometheus 설정
- Spring Boot 메트릭: `/actuator/prometheus`
- 스크래핑 간격: 5초
- 데이터 보존: 30일

## Grafana 대시보드 계획

### 대시보드 1: Query Performance
- 쿼리 응답시간 (avg, p95, p99)
- 쿼리 처리량 (QPS)
- 에러율

### 대시보드 2: Pipeline Breakdown
- 파싱 시간
- 쿼리 빌드 시간
- 임베딩 시간
- ES 검색 시간

### 대시보드 3: System Health
- Elasticsearch 상태
- 임베딩 서비스 상태
- Spring Boot JVM 메트릭

## Phase 2 개선 후 재측정

Phase 2에서 다음 기능을 추가한 후 성능을 다시 측정할 예정:

1. **BETWEEN 연산자** - 날짜 범위 검색
2. **CONTAINS/LIKE** - 패턴 매칭
3. **Aggregation** - 집계 (카테고리, 감성 분포)
4. **Boosting 함수** - 최신도 가중치, 트렌드 부스팅

## 성능 측정 스크립트

```bash
# 초기 성능 측정
python3 scripts/performance_test.py

# 개선 후 성능 측정
python3 scripts/performance_test.py > performance_after.json

# 성능 비교
python3 scripts/performance_test.py compare \
  performance_before.json \
  performance_after.json
```

## 다음 단계

1. ✅ Prometheus + Grafana 셋업
2. ✅ Spring Boot Actuator 통합
3. ⏳ Elasticsearch 시작 및 샘플 데이터 적재
4. ⏳ 초기 성능 측정 (Phase 1)
5. ⏳ Phase 2 기능 개발
6. ⏳ 성능 재측정 및 비교 분석
7. ⏳ 블로그 글 작성

---

**저작자**: Claude Code  
**최종 수정**: 2026-04-23 08:30 KST
