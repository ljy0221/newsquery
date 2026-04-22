#!/usr/bin/env python3
"""
N-QL Intelligence 샘플 데이터 생성 스크립트
1000+ 건의 뉴스 문서 생성 후 Elasticsearch에 적재
"""

import json
import random
from datetime import datetime, timedelta
from elasticsearch import Elasticsearch
import sys

# Elasticsearch 연결
es = Elasticsearch(["http://localhost:9200"])

# 샘플 데이터 설정
SOURCES = ["Reuters", "Bloomberg", "BBC", "CNN", "TechCrunch", "The Verge", "Wired", "Forbes", "Medium", "Dev.to"]
CATEGORIES = ["TECHNOLOGY", "BUSINESS", "SCIENCE", "HEALTH", "FINANCE", "ENTERTAINMENT", "POLITICS", "SPORTS"]
SENTIMENTS = ["positive", "neutral", "negative"]
COUNTRIES = ["US", "KR", "JP", "CN", "UK", "DE", "FR", "IN", "BR", "CA"]

KEYWORDS_POOL = [
    "AI", "machine learning", "blockchain", "cryptocurrency", "cloud computing",
    "5G", "IoT", "quantum computing", "cyber security", "data science",
    "Python", "JavaScript", "Java", "Go", "Rust",
    "Docker", "Kubernetes", "microservices", "API", "REST",
    "digital transformation", "startup", "innovation", "technology",
    "artificial intelligence", "neural network", "deep learning"
]

def generate_sample_news(count=1000):
    """샘플 뉴스 문서 생성"""
    docs = []
    base_date = datetime.now() - timedelta(days=30)

    for i in range(count):
        # 랜덤 날짜 (지난 30일)
        days_ago = random.randint(0, 30)
        published_date = base_date + timedelta(days=days_ago)

        # 키워드 조합
        keywords = random.sample(KEYWORDS_POOL, k=random.randint(2, 5))

        doc = {
            "title": f"{' '.join(keywords)} news update #{i}",
            "content": f"Latest news about {keywords[0]}. {' '.join(keywords[1:])} are trending. "
                      f"This is a comprehensive report on {keywords[0]} in {COUNTRIES[random.randint(0, len(COUNTRIES)-1)]}.",
            "source": random.choice(SOURCES),
            "category": random.choice(CATEGORIES),
            "sentiment": random.choice(SENTIMENTS),
            "country": random.choice(COUNTRIES),
            "publishedAt": published_date.isoformat() + "Z",
            "score": round(random.uniform(1.0, 10.0), 1),
            "trend_score": random.randint(0, 100),
            "view_count": random.randint(10, 10000),
            "share_count": random.randint(0, 1000),
            "url": f"https://news.example.com/article/{i}"
        }
        docs.append(doc)

    return docs

def bulk_insert_to_elasticsearch(docs, index_name="news"):
    """Elasticsearch에 벌크 적재"""
    try:
        # 인덱스 생성 (이미 있으면 무시)
        if not es.indices.exists(index=index_name):
            print(f"📝 '{index_name}' 인덱스 생성 중...")
            es.indices.create(
                index=index_name,
                mappings={
                    "properties": {
                        "title": {"type": "text"},
                        "content": {"type": "text"},
                        "source": {"type": "keyword"},
                        "category": {"type": "keyword"},
                        "sentiment": {"type": "keyword"},
                        "country": {"type": "keyword"},
                        "publishedAt": {"type": "date"},
                        "score": {"type": "float"},
                        "trend_score": {"type": "integer"},
                        "view_count": {"type": "integer"},
                        "share_count": {"type": "integer"},
                        "url": {"type": "keyword"}
                    }
                }
            )
        else:
            print(f"✅ '{index_name}' 인덱스 이미 존재")

        # 벌크 적재
        print(f"📤 {len(docs)}개 문서 적재 중...")

        actions = []
        for i, doc in enumerate(docs):
            actions.append({"index": {"_index": index_name, "_id": str(i)}})
            actions.append(doc)

        # 10,000개씩 나누어 적재 (메모리 효율)
        batch_size = 10000
        for batch_idx in range(0, len(actions), batch_size):
            batch = actions[batch_idx:batch_idx + batch_size]
            result = es.bulk(operations=batch)
            errors = [item for item in result["items"] if "error" in item.get("index", {})]

            if errors:
                print(f"⚠️  배치 {batch_idx//2}: {len(errors)}개 오류")
            else:
                print(f"✅ 배치 {batch_idx//2}: {len(batch)//2}개 완료")

        # 최종 확인
        count = es.count(index=index_name)["count"]
        print(f"\n🎉 적재 완료! 총 {count}개 문서")
        return count

    except Exception as e:
        print(f"❌ 오류: {e}")
        return 0

def show_statistics(index_name="news"):
    """데이터 통계 표시"""
    try:
        print("\n" + "="*60)
        print("📊 데이터 통계")
        print("="*60)

        # 전체 문서 수
        total = es.count(index=index_name)["count"]
        print(f"총 문서 수: {total}")

        # 카테고리별 분포
        agg_response = es.search(
            index=index_name,
            size=0,
            aggs={
                "categories": {
                    "terms": {"field": "category", "size": 20}
                },
                "sentiments": {
                    "terms": {"field": "sentiment", "size": 10}
                },
                "sources": {
                    "terms": {"field": "source", "size": 20}
                }
            }
        )

        print("\n📂 카테고리별 분포:")
        for bucket in agg_response["aggregations"]["categories"]["buckets"]:
            print(f"  {bucket['key']}: {bucket['doc_count']}")

        print("\n😊 감성별 분포:")
        for bucket in agg_response["aggregations"]["sentiments"]["buckets"]:
            print(f"  {bucket['key']}: {bucket['doc_count']}")

        print("\n📰 출처별 분포:")
        for bucket in agg_response["aggregations"]["sources"]["buckets"][:5]:
            print(f"  {bucket['key']}: {bucket['doc_count']}")

        print("="*60 + "\n")

    except Exception as e:
        print(f"❌ 통계 오류: {e}")

if __name__ == "__main__":
    # 연결 확인
    try:
        info = es.info()
        print(f"✅ Elasticsearch 연결 성공: {info['version']['number']}\n")
    except Exception as e:
        print(f"❌ Elasticsearch 연결 실패: {e}")
        print("💡 다음 명령으로 Elasticsearch를 시작하세요:")
        print("   docker-compose up -d elasticsearch")
        sys.exit(1)

    # 샘플 데이터 생성
    print("🔨 샘플 데이터 생성 중...")
    docs = generate_sample_news(1000)
    print(f"✅ {len(docs)}개 문서 생성 완료\n")

    # Elasticsearch에 적재
    bulk_insert_to_elasticsearch(docs)

    # 통계 표시
    show_statistics()
