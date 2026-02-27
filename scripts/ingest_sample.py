#!/usr/bin/env python3
"""
샘플 뉴스 데이터 → Elasticsearch 삽입 스크립트

사용법:
    pip install elasticsearch
    python scripts/ingest_sample.py
    python scripts/ingest_sample.py --es-url http://localhost:9200 --index news --count 500
"""

import argparse
import json
import random
import sys
import urllib.request
from datetime import datetime, timedelta

try:
    from elasticsearch import Elasticsearch, helpers
except ImportError:
    print("elasticsearch-py 패키지가 필요합니다: pip install elasticsearch")
    sys.exit(1)

# ─── ES Index 매핑 ────────────────────────────────────────────────────────────
INDEX_MAPPING = {
    "mappings": {
        "properties": {
            "title":          {"type": "text"},
            "content":        {"type": "text"},
            "sentiment":      {"type": "keyword"},
            "source":         {"type": "keyword"},
            "category":       {"type": "keyword"},
            "country":        {"type": "keyword"},
            "publishedAt":    {"type": "date"},
            "score":          {"type": "float"},
            "url":            {"type": "keyword"},
            "content_vector": {
                "type":       "dense_vector",
                "dims":       384,
                "index":      True,
                "similarity": "cosine",
            },
        }
    },
    "settings": {
        "number_of_shards": 1,
        "number_of_replicas": 0,
    }
}

# ─── 샘플 데이터 소스 ─────────────────────────────────────────────────────────
SOURCES = [
    "Reuters", "Bloomberg", "AP News", "BBC", "CNN",
    "The Guardian", "Financial Times", "Wall Street Journal", "Nikkei", "CNBC",
]
CATEGORIES = ["ECON", "TECH", "POL", "ENV", "MIL", "SOC", "BUS", "SCI"]
COUNTRIES   = ["US", "KR", "JP", "CN", "DE", "GB", "FR", "IN", "AU", "CA"]
SENTIMENTS  = ["positive", "negative", "neutral"]

KEYWORDS = [
    "HBM", "semiconductor", "AI", "chip", "GPU", "DRAM", "NAND", "memory",
    "interest rate", "inflation", "GDP", "earnings", "IPO", "bond yield",
    "election", "sanctions", "trade war", "tariff", "diplomacy",
    "climate change", "renewable energy", "EV", "battery", "hydrogen",
    "supply chain", "logistics", "shipping", "oil", "energy crisis",
]

TITLE_TEMPLATES = [
    "{kw} market shows strong growth amid global demand surge",
    "Analysis: {kw} sector faces new challenges in 2024",
    "{src} report: {kw} investment reaches record high",
    "Breaking: Major {kw} deal announced, markets react positively",
    "{cty} government announces new policy on {kw}",
    "{kw} stocks decline as investors weigh economic outlook",
    "Tech giants race to dominate {kw} market segment",
    "Global {kw} supply chain disruption concerns grow",
    "Exclusive: Inside the {kw} revolution reshaping industries",
    "{cty} and {src} partner on landmark {kw} initiative",
]

BODY_SENTENCES = [
    "The situation continues to evolve rapidly.",
    "Market participants are closely monitoring developments.",
    "Analysts predict continued momentum in this space.",
    "Regulatory bodies have yet to issue formal guidance.",
    "Investors remain cautious amid uncertainty.",
    "This marks a significant shift in the competitive landscape.",
    "Several major players have already signaled their intentions.",
    "Long-term implications remain a subject of debate.",
    "The move comes amid broader industry consolidation trends.",
    "Experts advise stakeholders to reassess their strategies.",
]


# ─── 임베딩 ──────────────────────────────────────────────────────────────────
def embed_batch(texts: list[str], embed_url: str) -> list[list[float]] | None:
    """임베딩 서비스 POST /embed 호출 (배치). 실패 시 None 반환."""
    payload = json.dumps({"texts": texts}).encode()
    req = urllib.request.Request(
        f"{embed_url}/embed",
        data=payload,
        headers={"Content-Type": "application/json"},
        method="POST",
    )
    try:
        with urllib.request.urlopen(req, timeout=30) as resp:
            return json.loads(resp.read())["vectors"]
    except Exception as e:
        print(f"[ERROR] 임베딩 서비스 호출 실패: {e}")
        return None


# ─── 문서 생성 ────────────────────────────────────────────────────────────────
def generate_doc(index_name: str, doc_id: int) -> dict:
    kw  = random.choice(KEYWORDS)
    src = random.choice(SOURCES)
    cty = random.choice(COUNTRIES)
    cat = random.choice(CATEGORIES)

    sentiment = random.choices(SENTIMENTS, weights=[3, 2, 5])[0]
    score     = round(random.uniform(-10.0, 10.0), 2)
    days_ago  = random.randint(0, 90)
    published = (datetime.utcnow() - timedelta(days=days_ago)).strftime("%Y-%m-%dT%H:%M:%SZ")

    title = random.choice(TITLE_TEMPLATES).format(kw=kw, src=src, cty=cty)
    body  = title + ". " + " ".join(random.sample(BODY_SENTENCES, k=4))

    return {
        "_index": index_name,
        "_id":    str(doc_id),
        "_source": {
            "title":       title,
            "content":     body,
            "sentiment":   sentiment,
            "source":      src,
            "category":    cat,
            "country":     cty,
            "publishedAt": published,
            "score":       score,
            "url":         "",
        },
    }


# ─── 진입점 ──────────────────────────────────────────────────────────────────
def main() -> None:
    parser = argparse.ArgumentParser(description="샘플 뉴스 데이터를 Elasticsearch에 삽입합니다.")
    parser.add_argument("--es-url", default="http://localhost:9200", help="ES URL")
    parser.add_argument("--index",  default="news",                  help="인덱스 이름")
    parser.add_argument("--count",  type=int, default=200,           help="삽입할 문서 수")
    parser.add_argument("--reset",     action="store_true",                  help="인덱스 재생성 (기존 데이터 삭제)")
    parser.add_argument("--embed",     action="store_true",                  help="임베딩 서비스 호출하여 content_vector 추가")
    parser.add_argument("--embed-url", default="http://localhost:8000",      help="임베딩 서비스 URL")
    args = parser.parse_args()

    es = Elasticsearch(args.es_url)
    try:
        es.info()
        print(f"[OK] Elasticsearch 연결: {args.es_url}")
    except Exception as e:
        print(f"[ERROR] Elasticsearch에 연결할 수 없습니다: {args.es_url}")
        print(f"        원인: {e}")
        sys.exit(1)

    # 인덱스 생성 / 재생성
    if args.reset and es.indices.exists(index=args.index).body:
        es.indices.delete(index=args.index)
        print(f"[INFO] 기존 인덱스 삭제: {args.index}")

    if not es.indices.exists(index=args.index).body:
        es.indices.create(
            index=args.index,
            mappings=INDEX_MAPPING["mappings"],
            settings=INDEX_MAPPING["settings"],
        )
        print(f"[INFO] 인덱스 생성: {args.index}")
    else:
        print(f"[INFO] 기존 인덱스 사용: {args.index}")

    # 문서 생성
    docs = [generate_doc(args.index, i) for i in range(1, args.count + 1)]

    # 임베딩 추가 (--embed 플래그)
    if args.embed:
        texts = [d["_source"]["content"] for d in docs]
        print(f"[INFO] 임베딩 중... (총 {len(texts)}건, 서비스: {args.embed_url})")
        vectors = embed_batch(texts, args.embed_url)
        if vectors is None:
            print("[WARN] 임베딩 실패 — content_vector 없이 삽입합니다.")
        else:
            for doc, vec in zip(docs, vectors):
                doc["_source"]["content_vector"] = vec
            print(f"[OK] 임베딩 완료: {len(vectors)}건")

    success, errors = helpers.bulk(es, docs, raise_on_error=False)
    print(f"[OK] 삽입 완료: {success}건 성공 / {len(errors)}건 실패")

    # 확인
    es.indices.refresh(index=args.index)
    count = es.count(index=args.index)["count"]
    print(f"[OK] 현재 인덱스 문서 수: {count}건")


if __name__ == "__main__":
    main()
