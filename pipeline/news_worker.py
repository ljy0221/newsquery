"""Kafka consumer → sentence-transformers 임베딩 → Elasticsearch bulk 인덱서."""
import json
import logging
import re
import uuid
from concurrent.futures import ThreadPoolExecutor, as_completed
from typing import Any

import requests
from confluent_kafka import Consumer, KafkaError, KafkaException
from elasticsearch import Elasticsearch, helpers
from sentence_transformers import SentenceTransformer

from config import (
    EMBEDDING_DIMS,
    EMBEDDING_MODEL,
    ES_HOST,
    ES_INDEX,
    KAFKA_BOOTSTRAP_SERVERS,
    KAFKA_GROUP_ID,
    KAFKA_TOPIC,
)

logging.basicConfig(level=logging.INFO, format="%(asctime)s [WORKER] %(message)s")
log = logging.getLogger(__name__)

BATCH_SIZE     = 50    # ES bulk 사이즈
POLL_TIMEOUT   = 1.0   # Kafka poll 타임아웃 (초)
MAX_POLL_EMPTY = 5     # 연속 빈 poll 허용 횟수 (종료 조건 없이 계속 실행)

# GDELT 제목 보강
TITLE_RE       = re.compile(r'<title[^>]*>(.*?)</title>', re.IGNORECASE | re.DOTALL)
FETCH_WORKERS  = 10    # 병렬 HTTP 요청 수
FETCH_TIMEOUT  = 1.5   # URL당 타임아웃 (초)
FETCH_DEADLINE = 6.0   # 배치 전체 최대 대기 (초)


def fetch_title(url: str) -> str | None:
    """URL에서 HTML <title> 태그를 가져와 반환. 실패 시 None."""
    try:
        resp = requests.get(
            url, timeout=FETCH_TIMEOUT,
            headers={"User-Agent": "Mozilla/5.0"},
            allow_redirects=True,
        )
        if resp.status_code == 200:
            m = TITLE_RE.search(resp.text[:8192])
            if m:
                title = m.group(1).strip()
                for sep in (" | ", " - ", " – ", " :: "):
                    if sep in title:
                        title = title.split(sep)[0].strip()
                        break
                return title[:200] if title else None
    except Exception:
        pass
    return None


def enrich_gdelt_titles(
    docs: list[dict], msgs: list
) -> tuple[list[dict], list]:
    """GDELT 문서의 SOURCEURL에서 기사 제목을 병렬로 fetch.
    제목을 가져오지 못한 GDELT 문서는 리스트에서 제거해 반환."""
    gdelt_indices = {
        i for i, doc in enumerate(docs)
        if doc.get("pipeline") == "gdelt" and doc.get("url")
    }
    if not gdelt_indices:
        return docs, msgs

    log.info("GDELT 제목 fetch: %d건", len(gdelt_indices))
    fetched: set[int] = set()
    with ThreadPoolExecutor(max_workers=FETCH_WORKERS) as executor:
        futures = {
            executor.submit(fetch_title, docs[i]["url"]): i
            for i in gdelt_indices
        }
        try:
            for future in as_completed(futures, timeout=FETCH_DEADLINE):
                idx = futures[future]
                title = future.result()
                if title:
                    docs[idx]["title"] = title
                    fetched.add(idx)
        except Exception:
            pass  # deadline 초과 — 나머지는 제거 대상

    drop = gdelt_indices - fetched
    if not drop:
        log.info("GDELT 제목 fetch 완료: %d건 성공", len(fetched))
        return docs, msgs

    keep = [i for i in range(len(docs)) if i not in drop]
    log.info("GDELT 제목 fetch: 성공 %d / 제거 %d", len(fetched), len(drop))
    return [docs[i] for i in keep], [msgs[i] for i in keep]


def build_consumer() -> Consumer:
    return Consumer({
        "bootstrap.servers":  KAFKA_BOOTSTRAP_SERVERS,
        "group.id":           KAFKA_GROUP_ID,
        "auto.offset.reset":  "earliest",
        "enable.auto.commit": False,   # 수동 커밋 — ES 저장 후 커밋
    })


def embed_batch(model: SentenceTransformer, texts: list[str]) -> list[list[float]]:
    vectors = model.encode(texts, normalize_embeddings=True, show_progress_bar=False)
    return [v.tolist() for v in vectors]


def build_es_action(doc: dict[str, Any], vector: list[float]) -> dict:
    doc_id = str(uuid.uuid5(uuid.NAMESPACE_URL, doc.get("url", str(uuid.uuid4()))))
    source = {
        "title":          doc.get("title", ""),
        "content":        doc.get("content", ""),
        "url":            doc.get("url", ""),
        "source":         doc.get("source", ""),
        "sentiment":      doc.get("sentiment", "neutral"),
        "country":        doc.get("country", "UNKNOWN"),
        "category":       doc.get("category", ""),
        "score":          float(doc.get("score", 0.0)),
        "publishedAt":    doc.get("publishedAt"),
        "pipeline":       doc.get("pipeline", ""),
        "content_vector": vector,
    }
    return {
        "_index":  ES_INDEX,
        "_id":     doc_id,
        "_source": source,
    }


def flush_batch(
    es: Elasticsearch,
    model: SentenceTransformer,
    batch_docs: list[dict],
    batch_msgs: list,
    consumer: Consumer,
):
    if not batch_docs:
        return

    batch_docs, batch_msgs = enrich_gdelt_titles(batch_docs, batch_msgs)

    if not batch_docs:
        consumer.commit(asynchronous=False)
        return

    texts   = [d.get("content") or d.get("title", "") for d in batch_docs]
    vectors = embed_batch(model, texts)
    actions = [build_es_action(doc, vec) for doc, vec in zip(batch_docs, vectors)]

    success, failed = helpers.bulk(es, actions, raise_on_error=False)
    log.info("ES bulk: 성공 %d / 실패 %d", success, len(failed))

    # ES 저장 성공 후 Kafka offset 커밋
    consumer.commit(asynchronous=False)
    log.debug("Kafka offset 커밋 완료 (%d건)", len(batch_msgs))


def main():
    log.info("모델 로드: %s", EMBEDDING_MODEL)
    model = SentenceTransformer(EMBEDDING_MODEL)
    log.info("모델 로드 완료 (dims=%d)", EMBEDDING_DIMS)

    es       = Elasticsearch(ES_HOST)
    consumer = build_consumer()
    consumer.subscribe([KAFKA_TOPIC])

    log.info("Kafka 구독 시작: topic=%s, group=%s", KAFKA_TOPIC, KAFKA_GROUP_ID)

    batch_docs: list[dict] = []
    batch_msgs: list       = []

    try:
        while True:
            msg = consumer.poll(POLL_TIMEOUT)

            if msg is None:
                # 배치가 남아있으면 flush
                if batch_docs:
                    flush_batch(es, model, batch_docs, batch_msgs, consumer)
                    batch_docs.clear()
                    batch_msgs.clear()
                continue

            if msg.error():
                if msg.error().code() == KafkaError._PARTITION_EOF:
                    log.debug("파티션 끝: %s[%d]", msg.topic(), msg.partition())
                else:
                    raise KafkaException(msg.error())
                continue

            try:
                doc = json.loads(msg.value().decode("utf-8"))
                batch_docs.append(doc)
                batch_msgs.append(msg)
            except json.JSONDecodeError as e:
                log.warning("JSON 파싱 오류: %s", e)
                continue

            if len(batch_docs) >= BATCH_SIZE:
                flush_batch(es, model, batch_docs, batch_msgs, consumer)
                batch_docs.clear()
                batch_msgs.clear()

    except KeyboardInterrupt:
        log.info("종료 신호 수신")
    finally:
        if batch_docs:
            flush_batch(es, model, batch_docs, batch_msgs, consumer)
        consumer.close()
        log.info("워커 종료")


if __name__ == "__main__":
    main()
