# 문서 인덱스

N-QL Intelligence 프로젝트의 모든 문서를 한눈에 볼 수 있는 인덱스입니다.

---

## 시작하기

### 초보자를 위한 문서

1. **[README.md](../README.md)** (메인 문서)
   - 프로젝트 개요
   - 기술 스택
   - 빠른 시작 가이드
   - API 문서

2. **[SETUP_GUIDE.md](SETUP_GUIDE.md)**
   - 단계별 환경 설정
   - Docker Compose 실행
   - 각 서비스 시작 순서

---

## 시각적 자료

### 플로우 및 다이어그램

**[VISUAL_GUIDE.md](VISUAL_GUIDE.md)** — 상세한 ASCII 다이어그램

1. **전체 시스템 아키텍처**
   - 데이터 소스 → Kafka → 저장소의 전체 흐름
   - 각 컴포넌트의 역할 명확화

2. **NQL 쿼리 처리 플로우**
   - ANTLR4 파싱 과정
   - AST 생성 및 변환
   - 최종 응답 생성

3. **RRF 스코어링 메커니즘**
   - BM25 + Vector 통합 예시
   - 점수 계산 과정
   - 최종 순위 결정

4. **Kafka 데이터 흐름**
   - Producer/Consumer 구조
   - 파티션 기반 병렬 처리
   - Consumer Group 관리

5. **데이터 저장소 비교**
   - Elasticsearch vs Iceberg
   - 각 저장소의 특징
   - 데이터 이동 전략

6. **프론트엔드 컴포넌트 트리**
   - Next.js 구조
   - React 컴포넌트 계층
   - 상태 관리

7. **에러 처리 플로우**
   - 각 계층별 에러 타입
   - Graceful Degradation
   - 응답 코드

8. **성능 특성**
   - 쿼리 복잡도별 응답 시간
   - 최적화 팁

9. **배포 토폴로지**
   - 고가용성 구성
   - 로드 밸런싱
   - 클러스터 구조

### Excalidraw 다이어그램 (편집 가능)

프로젝트 루트 → `docs/diagrams/` 폴더

1. **[architecture.excalidraw.json](diagrams/architecture.excalidraw.json)**
   - NQL 처리 파이프라인
   - 각 단계별 색상 구분
   - Excalidraw에서 편집 가능

2. **[data-pipeline.excalidraw.json](diagrams/data-pipeline.excalidraw.json)**
   - 데이터 수집부터 저장소까지
   - Producer/Consumer 구조

3. **[rrf-scoring.excalidraw.json](diagrams/rrf-scoring.excalidraw.json)**
   - RRF 스코어링 과정
   - BM25 + Vector 통합

**사용 방법:**
1. [excalidraw.com](https://excalidraw.com) 접속
2. "Open" → "Open from device" → JSON 파일 선택
3. 또는 프로젝트에 `@excalidraw/excalidraw` 통합 가능

---

## 실행 및 테스트

### 스크린샷 가이드

**[SCREENSHOTS_GUIDE.md](SCREENSHOTS_GUIDE.md)** — 각 단계별 실행 화면

1. **Docker Compose 시작** (1단계)
   - 명령어
   - 예상 출력
   - 상태 확인 방법

2. **Spring Boot 빌드** (2단계)
   - 문법 생성 (ANTLR4)
   - 빌드 과정
   - 개발 서버 로그

3. **Elasticsearch 확인** (3단계)
   - 상태 체크
   - 인덱스 매핑 확인
   - 필드 구조

4. **임베딩 서비스** (4단계)
   - FastAPI 실행
   - 서비스 테스트
   - 임베딩 응답 포맷

5. **Kafka 메시지 흐름** (5단계)
   - 샘플 데이터 인덱싱
   - Kafka UI 모니터링
   - 메시지 포맷

6. **NQL 쿼리 API** (6단계)
   - 요청/응답 예시
   - 에러 응답
   - 200 OK 예시

7. **프론트엔드 UI** (7단계)
   - 페이지 요소 설명
   - 쿼리 입력 예시
   - 로딩 및 결과 표시

8. **Iceberg 데이터** (8단계)
   - 데이터 조회
   - 스냅샷 히스토리
   - 아카이빙 확인

9. **모니터링 및 로그** (9단계)
   - Spring Boot 로그
   - ES 통계
   - 실시간 모니터링

10. **성능 벤치마크** (10단계)
    - 응답 시간 측정
    - 처리 단계별 분석
    - 성능 최적화

---

## 기술 문서

### NQL 문법 가이드

[README.md - NQL 쿼리 문법](../README.md#nql-쿼리-문법) 참고

- 지원 필드 목록
- 연산자 및 문법
- 쿼리 작성 예제 6가지

### API 문서

[README.md - API 문서](../README.md#api-문서) 참고

- `POST /api/query` 엔드포인트
- 요청/응답 스키마
- 필드 설명

### 설정 문서

[README.md - 설정 및 매개변수](../README.md#설정-및-매개변수) 참고

- Elasticsearch 설정 테이블
- RRF 스코어링 매개변수
- Python 파이프라인 설정
- 환경 변수

---

## 아키텍처 및 설계

### 주요 설계 결정

[README.md - 주요 설계 결정](../README.md#주요-설계-결정) 참고

1. **Java 17 패턴 매칭**
   - sealed interface 사용
   - instanceof 패턴 활용
   - 타입 안전성

2. **EmbeddingClient의 Graceful Degradation**
   - 서비스 장애 시 자동 전환
   - BM25 단독 검색 가능
   - 사용자 경험 유지

3. **at-least-once 처리 (Kafka)**
   - 수동 커밋 전략
   - Elasticsearch 성공 후 커밋
   - 재처리 메커니즘

4. **RRF를 통한 하이브리드 검색**
   - 두 순위 신호 통합
   - 보완적 검색 결과

---

## 배포 및 운영

### 배포 가이드

[README.md - 배포 가이드](../README.md#배포-가이드) 참고

- Docker 이미지 빌드 (Spring Boot)
- Docker 이미지 빌드 (Next.js)
- Kubernetes 배포 예시

### 환경 변수

[README.md - 환경 변수](../README.md#환경-변수) 참고

- Backend (application.properties)
- Python (pipeline/config.py)
- Frontend (.env.local)

### 디버깅 및 문제 해결

[README.md - 디버깅](../README.md#디버깅) 참고

- Elasticsearch 연결 오류
- Kafka 연결 오류
- 임베딩 서비스 오류
- Spring Boot 로그 확인

---

## FAQ

[README.md - 문제 해결 (FAQ)](../README.md#문제-해결-faq) 참고

**Q: Elasticsearch 연결이 안 됩니다.**
→ Docker 컨테이너 상태 확인 및 재시작

**Q: NQL 쿼리가 파싱되지 않습니다.**
→ `./gradlew generateGrammarSource` 실행

**Q: 임베딩 벡터가 생성되지 않습니다.**
→ FastAPI 서비스 실행 확인

**Q: Kafka 메시지가 처리되지 않습니다.**
→ Kafka UI에서 메시지 확인

---

## 참고 자료

### 외부 문서

- [ANTLR4 공식 문서](https://www.antlr.org/)
- [Elasticsearch 공식 문서](https://www.elastic.co/guide/en/elasticsearch/reference/current/)
- [Spring Boot 공식 문서](https://spring.io/projects/spring-boot)
- [Next.js 공식 문서](https://nextjs.org/docs)
- [Apache Kafka 공식 문서](https://kafka.apache.org/)
- [Apache Spark 공식 문서](https://spark.apache.org/)
- [Apache Iceberg 공식 문서](https://iceberg.apache.org/)
- [Excalidraw 다이어그램 편집기](https://excalidraw.com)

### 프로젝트 파일 구조

[README.md - 디렉토리 구조](../README.md#디렉토리-구조) 참고

---

## 문서 업데이트 이력

| 날짜 | 변경 사항 |
|---|---|
| 2026-04-22 | 초판 작성 (README, VISUAL_GUIDE, SCREENSHOTS_GUIDE, INDEX) |

---

## 빠른 링크

### 주요 설정 파일

| 파일 | 용도 |
|---|---|
| [`build.gradle`](../build.gradle) | Gradle 빌드 설정 |
| [`docker-compose.yml`](../docker-compose.yml) | 인프라 구성 |
| [`src/main/antlr4/com/newsquery/NQL.g4`](../src/main/antlr4/com/newsquery/NQL.g4) | NQL 문법 정의 |
| [`frontend/.env.local`](../frontend/.env.local) | 프론트엔드 환경 변수 |
| [`pipeline/config.py`](../pipeline/config.py) | Python 파이프라인 설정 |

### 주요 소스 코드

| 파일 | 역할 |
|---|---|
| [`src/main/java/com/newsquery/nql/NQLQueryParser.java`](../src/main/java/com/newsquery/nql/NQLQueryParser.java) | NQL 파싱 및 빌드 |
| [`src/main/java/com/newsquery/query/ESQueryBuilder.java`](../src/main/java/com/newsquery/query/ESQueryBuilder.java) | ES Query DSL 생성 |
| [`src/main/java/com/newsquery/scoring/RRFScorer.java`](../src/main/java/com/newsquery/scoring/RRFScorer.java) | RRF 스코어링 |
| [`src/main/java/com/newsquery/api/QueryController.java`](../src/main/java/com/newsquery/api/QueryController.java) | API 엔드포인트 |
| [`frontend/app/components/SearchPanel.tsx`](../frontend/app/components/SearchPanel.tsx) | 검색 UI |

---

**마지막 업데이트:** 2026-04-22  
**문서 버전:** 1.0
