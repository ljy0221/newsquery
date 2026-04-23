# 프로젝트 폴더 구조

## 최상위 디렉토리

```
newsquery/
│
├── backend/                      # Spring Boot 백엔드 + 데이터 파이프라인
│   ├── src/
│   │   ├── main/
│   │   │   ├── antlr4/          # ANTLR4 NQL 문법 (NQL.g4)
│   │   │   ├── java/            # Java 소스 코드
│   │   │   └── resources/       # application.yml, SQL
│   │   └── test/
│   │       ├── java/            # JUnit, JMH 테스트
│   │       └── resources/
│   ├── gradle/                  # Gradle 래퍼
│   ├── build.gradle             # 빌드 설정
│   ├── settings.gradle
│   ├── gradlew / gradlew.bat
│   ├── pipeline/                # Python 데이터 파이프라인
│   │   ├── gdelt_producer.py    # GDELT 2.0 → Kafka
│   │   ├── rss_producer.py      # RSS → Kafka
│   │   ├── news_worker.py       # Kafka → ES 인덱싱
│   │   ├── embedding_service.py # FastAPI 임베딩
│   │   ├── spark_archive.py     # Spark → Iceberg
│   │   └── config.py
│   ├── scripts/                 # 유틸리티 스크립트
│   │   ├── screenshot.js        # Playwright 스크린샷
│   │   ├── ingest_sample.py     # 샘플 데이터 로드
│   │   ├── update_es_mapping.py # ES 매핑 업데이트
│   │   ├── setup_iceberg.py     # Iceberg 초기화
│   │   ├── query_iceberg.py     # Iceberg 조회
│   │   └── run_spark_archive.*  # Spark 실행 래퍼
│   ├── monitoring/              # 모니터링 (Grafana)
│   │   └── grafana/
│   │       └── provisioning/
│   │           └── dashboards/  # 대시보드 JSON
│   └── docker-compose.yml       # Kafka, Zookeeper 컨테이너
│
├── frontend/                     # Next.js 14 프론트엔드
│   ├── app/
│   │   ├── page.tsx             # 검색 페이지
│   │   ├── layout.tsx           # 루트 레이아웃
│   │   ├── dashboard/
│   │   │   ├── page.tsx
│   │   │   └── DashboardClient.tsx
│   │   ├── components/
│   │   │   ├── SearchPanel.tsx
│   │   │   ├── NewsCard.tsx
│   │   │   ├── NavTabs.tsx
│   │   │   └── dashboard/       # 대시보드 컴포넌트
│   │   ├── hooks/
│   │   │   └── useDashboard.ts
│   │   ├── types/
│   │   │   └── dashboard.ts
│   │   └── globals.css
│   ├── public/
│   │   └── screenshots/         # UI 스크린샷
│   ├── package.json / package-lock.json
│   ├── tailwind.config.ts
│   ├── next.config.ts
│   ├── tsconfig.json
│   ├── eslint.config.mjs
│   ├── postcss.config.mjs
│   └── .env.local
│
├── docs/                         # 모든 문서 및 가이드
│   ├── STRUCTURE.md             # 이 파일 (폴더 구조)
│   ├── CLAUDE.md                # 개발 가이드
│   ├── INDEX.md                 # 문서 인덱스
│   ├── VISUAL_GUIDE.md          # NQL 시각적 가이드
│   ├── QUICK_REFERENCE.md       # 빠른 참조
│   ├── API_GUIDE.md
│   │
│   ├── 📋 Phase 별 문서
│   ├── PHASE2_IMPROVEMENTS.md
│   ├── PHASE3_FEATURES.md
│   ├── PHASE4_5_ROADMAP.md
│   ├── PHASE4_5_SUMMARY.md
│   ├── PHASE4_COMPLETE.md
│   ├── PHASE5_COMPLETE.md
│   ├── PHASE5_NOTIFICATION_COMPLETE.md
│   ├── PHASE5_SUMMARY.md
│   ├── PHASES_5_6_7_COMPLETE_ROADMAP.md
│   ├── PHASE_5_1_COMPLETION_REPORT.md
│   ├── PHASE_5_1_SETUP.md
│   ├── PHASE_5_1_LEARNING_*.md (3개 파일)
│   ├── PHASE_6_COMPLETION_REPORT.md
│   ├── PHASE_6_LEARNING_*.md (3개 파일)
│   │
│   ├── 📚 학습 자료 & 기술 인터뷰
│   ├── TECH_INTERVIEW_GUIDE_INDEX.md
│   ├── TECH_INTERVIEW_GUIDE_SERIES_*.md (4개 파일)
│   ├── EVENT_DRIVEN_ARCHITECTURE_REPORT.md
│   ├── NOTIFICATION_SYSTEM_BLOG_POST.md
│   ├── DB_MIGRATION_STRATEGY.md
│   │
│   ├── 📊 성능 & 최적화
│   ├── PERFORMANCE_BASELINE.md
│   ├── PERFORMANCE_MEASUREMENT_REPORT.md
│   ├── PERFORMANCE_OPTIMIZATION_JOURNEY.md
│   ├── PERFORMANCE_VERIFICATION_REPORT.md
│   ├── REALTIME_NOTIFICATION_COMPARISON.md
│   ├── COLD_START_ANALYSIS.md
│   ├── BLOG_CACHING_STORY.md
│   ├── performance_phase*.json (6개 벤치마크)
│   │
│   ├── 📝 블로그 & 요약
│   ├── BLOG_POST.md
│   ├── PROJECT_SUMMARY.md
│   ├── FINAL_PROJECT_SUMMARY.md
│   ├── FINAL_LONG_TERM_ROADMAP.md
│   ├── FINAL_REPORT.md
│   ├── COMPLETION_SUMMARY.md
│   ├── DOCUMENTATION_SUMMARY.md
│   ├── IMPLEMENTATION_COMPLETE.md
│   │
│   ├── diagrams/                # Mermaid 아키텍처 다이어그램
│   ├── plans/                   # 프로젝트 계획 아카이브
│   └── test-output.log
│
├── .gitignore
├── README.md                     # 프로젝트 개요
├── package.json                  # 루트 (Playwright)
├── package-lock.json
└── docker-compose.yml            # 루트에도 복사 (참조용)
```

## 폴더별 역할

### `/backend` — Spring Boot 백엔드 및 데이터 파이프라인

**Java (Spring Boot 3.x)**
- `src/main/antlr4/`: NQL 문법 파일
  - `NQL.g4`: ANTLR4 토큰화 및 파싱 규칙
- `src/main/java/com/newsquery/`:
  - `api/`: REST 엔드포인트 (QueryController)
  - `nql/`: NQL 파서 및 Visitor (NQLVisitorImpl, NQLExpression)
  - `query/`: ES Query DSL 생성 (ESQueryBuilder)
  - `search/`: 검색 서비스 (NewsSearchService)
  - `scoring/`: RRF 스코어링 (RRFScorer)
  - `embedding/`: FastAPI 임베딩 클라이언트
  - `event/`: 이벤트 발행 시스템 (QueryExecutionEvent, EventPublisher)
  - `notification/`: 알림 규칙 & 채널 (RuleEngine, NotificationService)
  - `config/`: Spring 설정 및 어댑터

**Python 파이프라인** (`pipeline/`)
- `gdelt_producer.py`: GDELT 2.0 뉴스 → Kafka
- `rss_producer.py`: RSS 피드 → Kafka (URL 중복 제거)
- `news_worker.py`: Kafka 컨슈머 → 벡터 임베딩 → ES 인덱싱
- `embedding_service.py`: FastAPI 임베딩 서비스 (port 8000)
- `spark_archive.py`: PySpark Structured Streaming → Iceberg 장기 저장

**유틸리티** (`scripts/`)
- `screenshot.js`: Playwright 기반 UI 스크린샷 자동 촬영
- `ingest_sample.py`: 테스트 데이터 인덱싱
- `update_es_mapping.py`: ES 매핑 업데이트 (vector 필드)
- `setup_iceberg.py`: Iceberg 초기화 및 테이블 생성
- `query_iceberg.py`: Iceberg 데이터 조회

**모니터링** (`monitoring/`)
- Grafana 자동 프로비저닝 (대시보드 JSON)
- Prometheus 메트릭 수집
- Micrometer 메트릭 내보내기

**설정**
- `build.gradle`: 의존성, 플러그인 (JMH, ANTLR4)
- `docker-compose.yml`: Kafka, Zookeeper 컨테이너 구성

### `/frontend` — Next.js 14 프론트엔드

**App Router 구조** (`app/`)
- `page.tsx`: 메인 검색 페이지 (Server Component)
- `dashboard/page.tsx`: 대시보드 페이지
- `components/`: React 컴포넌트
  - `SearchPanel.tsx`: NQL 입력 및 검색 결과
  - `NewsCard.tsx`: 뉴스 아이템 표시
  - `NavTabs.tsx`: 페이지 네비게이션
  - `dashboard/`: 대시보드 컴포넌트 (StatCard, HistoryTable, TrendingChart)
- `hooks/useDashboard.ts`: 대시보드 API 커스텀 훅
- `types/dashboard.ts`: TypeScript 타입 정의
- `globals.css`: 글로벌 스타일

**정적 자산** (`public/`)
- `screenshots/`: Playwright로 생성한 UI 스크린샷
  - `search-main.png`
  - `search-result.png`
  - `dashboard-stats.png`
  - `dashboard-chart.png`
  - `dashboard-full.png`

**설정**
- `tailwind.config.ts`: Tailwind CSS v4 설정 (dark mode)
- `next.config.ts`: Next.js 설정
- `tsconfig.json`: TypeScript 설정
- `package.json`: 의존성 (React, Next.js, Recharts, Tailwind)

### `/docs` — 문서 및 학습 자료

**프로젝트 가이드**
- `STRUCTURE.md`: 이 파일 (폴더 구조)
- `CLAUDE.md`: Claude Code 개발 가이드
- `README.md`: 프로젝트 개요 (루트)

**학습 자료**
- `VISUAL_GUIDE.md`: NQL 처리 플로우 및 예시
- `TECH_INTERVIEW_GUIDE_*.md`: 기술 면접 대비 Q&A
- `EVENT_DRIVEN_ARCHITECTURE_REPORT.md`: Phase 5 알림 시스템
- `NOTIFICATION_SYSTEM_BLOG_POST.md`: 알림 구현 상세 가이드
- `DB_MIGRATION_STRATEGY.md`: JPA 마이그레이션 전략

**성능 및 최적화**
- `PERFORMANCE_VERIFICATION_REPORT.md`: Phase 6 벤치마킹 결과
- `PERFORMANCE_OPTIMIZATION_JOURNEY.md`: 성능 최적화 과정
- `COLD_START_ANALYSIS.md`: Cold Start 분석
- `BLOG_CACHING_STORY.md`: Redis 캐싱 전략

**Phase별 보고서**
- `PHASE4_COMPLETE.md`: Phase 4 완료 보고서
- `PHASE5_COMPLETE.md`: Phase 5 완료 보고서
- `PHASE_6_COMPLETION_REPORT.md`: Phase 6 완료 보고서
- 각 Phase별 상세 학습 자료 (`PHASE_*_LEARNING_*.md`)

**아키텍처**
- `diagrams/`: Mermaid 다이어그램 저장소

## 기술 스택

| 계층 | 기술 | 버전 |
|------|------|------|
| **쿼리 처리** | ANTLR4, Java | 4.13, 17 |
| **검색 엔진** | Elasticsearch | 8.x |
| **백엔드** | Spring Boot | 3.x |
| **캐싱** | Redis | 7.0+ |
| **메시징** | Kafka | 3.0+ |
| **데이터 파이프라인** | Python, PySpark | 3.10+, 3.4 |
| **임베딩** | FastAPI, sentence-transformers | 0.36+ |
| **장기 저장** | Apache Iceberg | |
| **프론트엔드** | Next.js, React, Tailwind | 14, 18, v4 |
| **모니터링** | Grafana, Prometheus | 9.x, 2.x |
| **자동화** | Playwright | 1.59+ |

## Git 관리 정책

### ✅ 버전 관리 (Tracked)
- 모든 소스 코드 (`backend/src/`, `frontend/app/`)
- 설정 파일 (`build.gradle`, `package.json`)
- 문서 (`docs/`, `README.md`)
- 스크린샷 (`frontend/public/screenshots/`)

### ❌ 제외 (Ignored)
- `build/`, `bin/`: Gradle 빌드 결과
- `frontend/.next/`: Next.js 캐시
- `node_modules/`: npm 패키지
- `__pycache__/`, `venv/`: Python 환경
- `iceberg-warehouse/`: 로컬 데이터 (재생성 가능)
- `hadoop-winutils/`: 자동 다운로드 바이너리

## 빌드 및 실행

### 백엔드

```bash
cd backend

# 빌드
./gradlew build

# ANTLR4 파서 생성 (NQL.g4 변경 후)
./gradlew generateGrammarSource

# Spring Boot 실행 (port 8080)
./gradlew bootRun

# 테스트
./gradlew test
```

### 프론트엔드

```bash
cd frontend

# 의존성 설치
npm install

# 개발 서버 (port 3000)
npm run dev

# 프로덕션 빌드
npm run build
npm run start
```

### 데이터 파이프라인

```bash
cd backend/pipeline

# FastAPI 임베딩 서비스 (port 8000)
python embedding_service.py

# Kafka 컨슈머 (ES 인덱싱)
python news_worker.py

# GDELT 뉴스 수집 → Kafka
python gdelt_producer.py

# RSS 피드 수집 → Kafka
python rss_producer.py
```

### 유틸리티

```bash
cd backend

# Playwright 스크린샷 생성
node scripts/screenshot.js

# 샘플 데이터 인덱싱
python scripts/ingest_sample.py

# Iceberg 초기화
python scripts/setup_iceberg.py
```

## 디렉토리 관계도

```
newsquery (root)
│
├── /backend          Java + Python 백엔드
│   ├── /src          Spring Boot 소스
│   ├── /pipeline     데이터 파이프라인
│   ├── /scripts      유틸리티
│   ├── /monitoring   Grafana
│   └── docker-compose.yml
│
├── /frontend         Next.js 프론트엔드
│   ├── /app          페이지 및 컴포넌트
│   └── /public       정적 자산
│
├── /docs             모든 문서
│   ├── /diagrams     아키텍처
│   ├── /plans        계획 아카이브
│   └── *.md          55+ 학습 문서
│
├── README.md         프로젝트 개요
├── STRUCTURE.md      이 파일
├── .gitignore
└── package.json      루트 (Playwright)
```

