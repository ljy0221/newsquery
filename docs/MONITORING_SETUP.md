# Prometheus + Grafana 모니터링 셋업 가이드

## 개요

N-QL Intelligence 프로젝트에 Prometheus와 Grafana를 통합하여 실시간 성능 모니터링을 구현했습니다.

## 아키텍처

```
Spring Boot (Port 8080)
├── /actuator/health → HealthController
├── /actuator/metrics → Spring Boot Metrics
└── /actuator/prometheus → Prometheus 메트릭 (Micrometer)
       ↓
   Prometheus (Port 9090)
   ├── 메트릭 수집 (15초 간격)
   ├── 데이터 저장 (30일 보존)
   └── 시계열 데이터 저장
       ↓
   Grafana (Port 3001)
   ├── 대시보드 시각화
   ├── 알림 설정
   └── 트렌드 분석
```

## 설치 및 실행

### 1. Docker Compose로 Prometheus + Grafana 시작

```bash
cd c:/project/newsquery
docker-compose up -d prometheus grafana
```

### 2. Spring Boot 서비스 시작

```bash
./gradlew bootRun
```

### 3. 접근 URL

| 서비스 | URL | 설명 |
|--------|-----|------|
| Spring Boot | http://localhost:8080 | API 엔드포인트 |
| Health Check | http://localhost:8080/api/health | 헬스 체크 |
| Prometheus | http://localhost:9090 | 메트릭 UI |
| Grafana | http://localhost:3001 | 대시보드 |
| Kafka UI | http://localhost:8888 | 메시지 큐 모니터링 |

## Prometheus 설정

**파일**: `monitoring/prometheus.yml`

```yaml
global:
  scrape_interval: 15s  # 메트릭 수집 간격
  evaluation_interval: 15s

scrape_configs:
  - job_name: 'spring-boot'
    static_configs:
      - targets: ['localhost:8080']
    metrics_path: '/actuator/prometheus'
    scrape_interval: 5s  # Spring Boot는 더 자주 수집
```

## Spring Boot Actuator 설정

**파일**: `src/main/resources/application.yml`

```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,prometheus
  metrics:
    export:
      prometheus:
        enabled: true
  endpoint:
    health:
      show-details: always
```

## 커스텀 메트릭 수집

**파일**: `src/main/java/com/newsquery/monitoring/QueryMetrics.java`

### 등록된 메트릭

#### 카운터 (Counter)
- `nql.query.total` - 총 NQL 쿼리 수
- `nql.query.errors` - 쿼리 에러 수

#### 타이머 (Timer)
- `nql.query.duration` - 전체 응답시간 (ms, P50/P95/P99)
- `nql.parse.duration` - NQL 파싱 시간
- `nql.build_query.duration` - 쿼리 빌드 시간
- `nql.embedding.duration` - 임베딩 호출 시간
- `nql.search.duration` - Elasticsearch 검색 시간

### 메트릭 기록 로직

```java
// QueryController에서 자동 기록
queryMetrics.recordQuery();  // 쿼리 카운트
queryMetrics.recordParseTime(duration);
queryMetrics.recordBuildQueryTime(duration);
queryMetrics.recordEmbeddingTime(duration);
queryMetrics.recordSearchTime(duration);
queryMetrics.recordQueryTime(startTime);
```

## Grafana 대시보드

### 대시보드 1: Query Performance Dashboard

**URI**: http://localhost:3001/d/nql-performance

#### 패널 1: 쿼리 응답시간 분석
- 전체 응답시간 추이
- 단계별 시간 분해 (파싱, 빌드, 임베딩, 검색)
- 범례: 평균, 최대, 최소값

#### 패널 2: 쿼리 처리량 (QPS)
- 초당 처리량 변화 추이
- 목표선: > 100 QPS

#### 패널 3: 성공/실패 비율 (1시간)
- 성공 쿼리 수
- 에러 쿼리 수
- 도넛 차트로 시각화

#### 패널 4: 쿼리 요청 분포
- 시간대별 요청 수
- 피크 시간 파악

## 성능 메트릭 분석 쿼리

### PromQL 예제

```promql
# 1. 평균 응답시간
rate(nql_query_duration[5m])

# 2. P95 응답시간
histogram_quantile(0.95, rate(nql_query_duration[5m]))

# 3. 에러율
rate(nql_query_errors[5m]) / rate(nql_query_total[5m])

# 4. 파이프라인 단계별 분석
rate(nql_parse_duration[5m])
rate(nql_build_query_duration[5m])
rate(nql_embedding_duration[5m])
rate(nql_search_duration[5m])
```

## 헬스 체크 엔드포인트

**URL**: http://localhost:8080/api/health

### 응답 예제

```json
{
  "status": "UP",
  "timestamp": 1776900503869,
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

| 상태 | 의미 | HTTP 상태 |
|------|------|----------|
| UP | 모든 서비스 정상 | 200 |
| DEGRADED | 일부 서비스 다운 | 503 |
| DOWN | 서비스 불가능 | 503 |

## 디버깅 팁

### 1. Prometheus가 메트릭을 수집하지 않는 경우

```bash
# Spring Boot 메트릭 확인
curl http://localhost:8080/actuator/prometheus | grep nql_

# Prometheus 타겟 상태 확인
# http://localhost:9090/targets
```

### 2. Grafana 데이터 소스 추가

1. 좌측 메뉴 → Configuration → Data Sources
2. Add Data Source → Prometheus
3. URL: `http://prometheus:9090`
4. Save & Test

### 3. 커스텀 대시보드 생성

1. Create → Dashboard → Add New Panel
2. Data Source: Prometheus 선택
3. Metrics: `nql_query_duration` 등 입력
4. Visualization: Time series / Gauge 선택

## 성능 최적화 체크리스트

- [ ] Prometheus 설정에서 `scrape_interval` 적절히 조정
  - 높은 빈도: 데이터 정확도 ↑, 저장소 사용량 ↑
  - 낮은 빈도: 저장소 효율 ↑, 해상도 ↓
- [ ] Grafana 대시보드 그래프 시간 범위 조정
  - 단기: 최근 1시간 (기본값)
  - 장기: 1주일 (트렌드 분석)
- [ ] 알림 규칙 설정 (향후)
  - 응답시간 > 1000ms
  - 에러율 > 5%

## 다음 단계

1. ✅ Prometheus + Grafana 설치
2. ✅ Spring Boot Actuator 통합
3. ✅ 커스텀 메트릭 구현
4. ⏳ 초기 성능 측정 (Phase 1)
5. ⏳ Phase 2 기능 개발 + 성능 비교
6. ⏳ 알림 규칙 설정 (Alertmanager)

---

**참고**:
- Prometheus 공식 문서: https://prometheus.io/docs/
- Grafana 공식 문서: https://grafana.com/docs/grafana/latest/
- Micrometer 공식 문서: https://micrometer.io/

