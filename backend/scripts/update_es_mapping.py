"""ES 'news' 인덱스에 content_vector dense_vector 필드 추가 (동적 매핑 업데이트)."""
import argparse
import sys

from elasticsearch import Elasticsearch

VECTOR_FIELD = "content_vector"
VECTOR_DIMS  = 384


def main():
    parser = argparse.ArgumentParser(description="ES 인덱스에 content_vector 필드 추가")
    parser.add_argument("--es-url", default="http://localhost:9200")
    parser.add_argument("--index",  default="news")
    args = parser.parse_args()

    es = Elasticsearch(args.es_url)

    try:
        es.info()
        print(f"[OK] Elasticsearch 연결: {args.es_url}")
    except Exception as e:
        print(f"[ERROR] Elasticsearch 연결 실패: {e}")
        sys.exit(1)

    # 인덱스 존재 여부 확인
    if not es.indices.exists(index=args.index).body:
        print(f"[ERROR] 인덱스 '{args.index}'가 존재하지 않습니다.")
        print("  먼저 scripts/ingest_sample.py 를 실행하여 인덱스를 생성하세요.")
        sys.exit(1)

    # 현재 매핑 확인
    current = es.indices.get_mapping(index=args.index)
    props   = current[args.index]["mappings"].get("properties", {})

    if VECTOR_FIELD in props:
        existing = props[VECTOR_FIELD]
        print(f"[INFO] '{VECTOR_FIELD}' 필드가 이미 존재합니다: {existing}")
        print("  재실행이 필요 없습니다.")
        return

    # 매핑 업데이트
    mapping = {
        "properties": {
            VECTOR_FIELD: {
                "type":       "dense_vector",
                "dims":       VECTOR_DIMS,
                "index":      True,
                "similarity": "cosine",
            }
        }
    }

    resp = es.indices.put_mapping(index=args.index, **mapping)
    if resp.get("acknowledged"):
        print(f"[OK] '{VECTOR_FIELD}' 필드 추가 완료 (dims={VECTOR_DIMS}, cosine)")
    else:
        print(f"[WARN] 매핑 업데이트 응답: {resp}")


if __name__ == "__main__":
    main()
