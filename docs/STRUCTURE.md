# 프로젝트 폴더 구조

```
newsquery/
│
├── src/                          # Java 소스 코드 (Spring Boot)
│   ├── main/
│   │   ├── antlr4/              # ANTLR4 문법 파일 (NQL.g4)
│   │   ├── java/com/newsquery/  # 주요 패키지
│   │   │   ├── api/             # REST 컨트롤러
│   │   │   ├── nql/             # NQL 파서 및 비지터
│   │   │   ├── query/           # ES 쿼리 빌더
│   │   │   ├── scoring/         # RRF 스코어링
│   │   │   ├── search/          # 검색 서비스
│   │   │   ├── embedding/       # 임베딩 클라이언트
│   │   │   ├── event/           # 이벤트 발행 시스템
│   │   │   ├── notification/    # 알림 규칙 & 채널
│   │   │   ├── config/          # Spring 설정
│   │   │   └── ...
│   │   └── resources/           # application.yml, SQL
│   │
│   └── test/
│       ├── java/com/newsquery/  # 테스트 클래스
│       │   ├── benchmark/       # JMH 벤치마크 테스트
│       │   └── ...
│       └── resources/
│
├── frontend/                     # Next.js 14 프론트엔드
│   ├── app/
│   │   ├── page.tsx             # 메인 페이지 (검색)
│   │   ├── layout.tsx           # 루트 레이아웃
│   │   ├── dashboard/
│   │   │   ├── page.tsx         # 대시보드 페이지
│   │   │   └── DashboardClient.tsx
│   │   ├── components/
│   │   │   ├── SearchPanel.tsx  # 검색 인터페이스
│   │   │   ├── NewsCard.tsx     # 뉴스 카드 컴포넌트
│   │   │   ├── NavTabs.tsx      # 네비게이션
│   │   │   └── dashboard/       # 대시보드 컴포넌트들
│   │   ├── hooks/
│   │   │   └── useDashboard.ts  # 대시보드 API 훅
│   │   ├── types/
│   │   │   └── dashboard.ts     # TS 타입 정의
│   │   └── globals.css
│   ├── public/
│   │   └── screenshots/         # 스크린샷 (문서용)
│   ├── package.json
│   ├── tailwind.config.ts       # Tailwind CSS 설정
│   ├── next.config.ts
│   └── tsconfig.json
│
├── pipeline/                     # Python 데이터 파이프라인
│   ├── config.py                # 환경 설정
│   ├── gdelt_producer.py        # GDELT 뉴스 수집
│   ├── rss_producer.py          # RSS 피드 수집
│   ├── news_worker.py           # Kafka 컨슈머 + ES 인덱싱
│   ├── embedding_service.py     # FastAPI 임베딩 서비스
│   └── spark_archive.py         # PySpark Iceberg 아카이빙
│
├── scripts/                      # 유틸리티 스크립트
│   ├── screenshot.js            # Playwright 스크린샷 자동화
│   ├── ingest_sample.py         # 샘플 데이터 인덱싱
│   ├── update_es_mapping.py     # ES 매핑 업데이트
│   ├── setup_iceberg.py         # Iceberg 초기화
│   ├── query_iceberg.py         # Iceberg 조회
│   ├── run_spark_archive.bat    # Windows Spark 실행
│   └── run_spark_archive.sh     # Linux/Mac Spark 실행
│
├── monitoring/                   # 모니터링 (Grafana + Prometheus)
│   └── grafana/
│       └── provisioning/
│           └── dashboards/      # Grafana 대시보드 JSON
│
├── docs/                         # 문서
│   ├── STRUCTURE.md             # 이 파일 (폴더 구조)
│   ├── VISUAL_GUIDE.md          # NQL 시각적 가이드
│   ├── PERFORMANCE_VERIFICATION_REPORT.md
│   ├── EVENT_DRIVEN_ARCHITECTURE_REPORT.md
│   └── diagrams/                # 아키텍처 다이어그램
│
├── gradle/                       # Gradle 래퍼
│   └── wrapper/
│
├── build.gradle                  # Gradle 설정 (JMH, 테스트)
├── settings.gradle
├── gradlew                       # Gradle 실행 스크립트 (Linux/Mac)
├── gradlew.bat                   # Gradle 실행 스크립트 (Windows)
│
├── pom.xml                       # Maven 설정 (선택사항)
├── package.json                  # 루트 Node.js (Playwright)
├── .gitignore
├── .env.example
├── CLAUDE.md                     # 개발 가이드 (Claude Code)
├── MEMORY.md                     # 프로젝트 메모리 인덱스
└── README.md                     # 프로젝트 개요
```

## 주요 폴더별 역할

### `src/` — Spring Boot 백엔드
- **ANTLR4**: NQL 문법 파일 및 생성된 파서
- **java/**: Java 소스 코드
  - `nql/`: NQL 파싱 로직 (NQLVisitorImpl, NQLExpression)
  - `query/`: Elasticsearch Query DSL 생성 (ESQueryBuilder)
  - `scoring/`: RRF 스코어링 (RRFScorer)
  - `search/`: 검색 서비스 (NewsSearchService)
  - `api/`: REST 엔드포인트 (QueryController)
  - `embedding/`: FastAPI 임베딩 클라이언트
  - `event/`: 알림 이벤트 발행 시스템
  - `notification/`: 알림 규칙 & 채널

### `frontend/` — Next.js 14 프론트엔드
- **app/**: App Router 구조
  - `page.tsx`: 검색 페이지 (Server Component)
  - `dashboard/`: 대시보드 페이지
  - `components/`: React 컴포넌트 (일부 `use client`)
  - `hooks/`: React Hooks (useDashboard)
  - `types/`: TypeScript 타입 정의
- **public/screenshots/**: Playwright로 생성된 스크린샷

### `pipeline/` — Python 데이터 파이프라인
- `gdelt_producer.py`: GDELT 2.0 → Kafka
- `rss_producer.py`: RSS → Kafka (중복 제거)
- `news_worker.py`: Kafka 컨슈머 → 임베딩 → ES 인덱싱
- `embedding_service.py`: FastAPI 벡터 임베딩 (port 8000)
- `spark_archive.py`: PySpark Structured Streaming → Iceberg

### `scripts/` — 유틸리티 및 자동화
- `screenshot.js`: Playwright 기반 UI 스크린샷 자동 촬영
- `ingest_sample.py`: 테스트 데이터 인덱싱
- `setup_iceberg.py`: Iceberg 메타스토어 및 테이블 초기화
- `query_iceberg.py`: Iceberg 데이터 조회

### `monitoring/` — 모니터링 대시보드
- Grafana 자동 프로비저닝 (대시보드 JSON)
- Prometheus 메트릭 수집
- Micrometer 연동 (Spring Boot)

### `docs/` — 학습 자료 및 문서
- `VISUAL_GUIDE.md`: NQL 예시 및 플로우 설명
- `PERFORMANCE_VERIFICATION_REPORT.md`: Phase 6 성능 검증 보고서
- `EVENT_DRIVEN_ARCHITECTURE_REPORT.md`: Phase 5 알림 시스템 설계
- `diagrams/`: Mermaid 아키텍처 다이어그램

## Git 관리 정책

### Tracked (버전 관리)
- 모든 소스 코드 (src/, frontend/app, pipeline/)
- 문서 (docs/, README.md)
- 설정 파일 (build.gradle, package.json, CLAUDE.md)
- 스크린샷 (frontend/public/screenshots/)

### Ignored (로컬 전용)
- `build/`: Gradle 빌드 결과
- `bin/`: Java 컴파일 결과
- `frontend/.next/`: Next.js 빌드 캐시
- `node_modules/`: npm 패키지
- `__pycache__/`, `venv/`: Python 환경
- `iceberg-warehouse/`: 로컬 데이터 (재생성 가능)
- `hadoop-winutils/`: 자동 다운로드 바이너리

## 실행 순서

```bash
# 1. 백엔드 빌드 및 실행
./gradlew build
./gradlew bootRun  # port 8080

# 2. 프론트엔드 실행
cd frontend && npm run dev  # port 3000

# 3. Python 서비스 (별도 터미널)
python pipeline/embedding_service.py  # port 8000
python pipeline/news_worker.py

# 4. 데이터 수집 (선택사항)
python pipeline/gdelt_producer.py
python pipeline/rss_producer.py

# 5. 스크린샷 생성
node scripts/screenshot.js
```

## 폴더별 기술 스택

| 폴더 | 기술 | 버전 |
|------|------|------|
| `src/` | Spring Boot, ANTLR4, Java | 3.x, 4.13, 17 |
| `frontend/` | Next.js, React, Tailwind | 14, 18, v4 |
| `pipeline/` | Python, Kafka, PySpark | 3.10+, 3.0, 3.4 |
| `scripts/` | Node.js, Playwright | 20+, 1.59 |
| `monitoring/` | Grafana, Prometheus | 9.x, 2.x |

