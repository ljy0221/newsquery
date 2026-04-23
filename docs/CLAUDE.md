# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

**N-QL Intelligence** — 전문가용 뉴스 랭킹 엔진.
JQL 스타일의 NQL(News Query Language)로 뉴스 검색 조건과 가중치를 직접 쿼리로 표현.

```
keyword("HBM") > 5 AND sentiment != "negative" AND source IN ["Reuters", "Bloomberg"]
```

핵심 역할: **NQL → Elasticsearch Query DSL 변환** + 하이브리드 스코어링(RRF).

## Tech Stack

| Layer | Technology |
|---|---|
| Ingestion | Kafka, Python Scraper |
| Processing | Apache Spark, Python Worker, LLM 요약, Vector Embedding |
| Storage | Apache Iceberg (아카이빙), Elasticsearch 8.x (하이브리드 검색) |
| Backend | Spring Boot 3.x, ANTLR4 |
| Frontend | React, Next.js |

## Build & Run Commands

```bash
# ANTLR4 Grammar 파일에서 파서 코드 생성 (NQL.g4 변경 후 실행)
./gradlew generateGrammarSource

# 전체 빌드
./gradlew build

# 실행
./gradlew bootRun

# 전체 테스트
./gradlew test

# 단일 테스트 클래스 실행
./gradlew test --tests "com.newsquery.nql.NQLParserTest"

# 단일 테스트 메서드 실행
./gradlew test --tests "com.newsquery.nql.NQLParserTest.testKeywordQuery"
```

## Architecture

### NQL Processing Pipeline

```
NQL 문자열
  → ANTLR4 Lexer/Parser (src/main/antlr4/NQL.g4)
  → AST (Abstract Syntax Tree)
  → NQLVisitor (AST 순회)
  → ESQueryBuilder (ES Query DSL JSON 생성)
  → Elasticsearch 8.x 실행
  → RRF 스코어링 (BM25 + 벡터 유사도)
  → 랭킹된 뉴스 목록 반환
```

### Key Directory Structure (planned)

```
src/main/
  antlr4/com/newsquery/
    NQL.g4              # NQL 문법 정의 (핵심 파일)
  java/com/newsquery/
    nql/
      NQLVisitorImpl.java    # AST → 중간 표현 변환
    query/
      ESQueryBuilder.java    # 중간 표현 → ES Query DSL
    scoring/
      RRFScorer.java         # Reciprocal Rank Fusion 스코어링
    api/
      QueryController.java   # POST /api/query 엔드포인트
    config/
      ElasticsearchConfig.java
```

### Scoring Strategy

하이브리드 스코어링은 **RRF(Reciprocal Rank Fusion)** 방식:
- BM25 기반 키워드 매칭 점수
- Dense vector 유사도 점수
- 두 랭크를 `1/(k + rank)` 공식으로 합산

### NQL Supported Fields (GDELT + RSS)

| Field | Type | Description |
|---|---|---|
| `keyword` | text | 뉴스 본문 키워드 |
| `sentiment` | keyword | GDELT Tone 기반 감성 ("positive", "negative", "neutral") |
| `source` | keyword | 뉴스 출처 도메인 |
| `category` | keyword | GDELT 이벤트 분류 코드 |
| `country` | keyword | 뉴스 발생 국가 코드 |
| `publishedAt` | date | 발행 시각 |
| `score` | float | GDELT Goldstein Scale |

## Data Sources

- **GDELT**: 전 세계 뉴스 15분 단위 업데이트. Tone/감성/이벤트 분류 메타데이터 포함.
- **RSS 피드**: 실시간 뉴스 수집. 제목, 내용 요약, 원문 링크 제공.

## ANTLR4 Development Notes

- Grammar 파일 수정 후 반드시 `./gradlew generateGrammarSource` 실행
- 생성된 파서 코드는 `build/generated-src/antlr/main/` 에 위치 (버전 관리 제외)
- Visitor 패턴 사용 권장 (Listener보다 반환값 처리에 유리)
- ANTLR4 IntelliJ 플러그인으로 Grammar 파일에서 직접 파싱 테스트 가능
