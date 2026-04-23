"""RSS 피드 수집 → Kafka 'news-raw' 프로듀서 (5분 주기, URL 중복 제거)."""
import json
import logging
import time
from pathlib import Path

import feedparser
from confluent_kafka import Producer

from config import (
    KAFKA_BOOTSTRAP_SERVERS,
    KAFKA_TOPIC,
    RSS_DEDUP_FILE,
    RSS_DEDUP_MAX,
    RSS_FEEDS,
    RSS_POLL_INTERVAL,
)

logging.basicConfig(level=logging.INFO, format="%(asctime)s [RSS] %(message)s")
log = logging.getLogger(__name__)


def delivery_report(err, msg):
    if err:
        log.error("전송 실패 [%s]: %s", msg.topic(), err)


# ── URL 중복 제거 세트 (파일 기반 영속) ──────────────────────────────────────

def load_seen_urls() -> set[str]:
    p = Path(RSS_DEDUP_FILE)
    if not p.exists():
        return set()
    lines = p.read_text(encoding="utf-8").splitlines()
    return set(lines[-RSS_DEDUP_MAX:])


def save_seen_urls(seen: set[str]):
    urls = list(seen)[-RSS_DEDUP_MAX:]
    Path(RSS_DEDUP_FILE).write_text("\n".join(urls), encoding="utf-8")


# ── 감성 추론 (제목 기반 단순 휴리스틱) ─────────────────────────────────────

_POS_WORDS = {"surge", "gain", "rally", "growth", "record", "win", "positive", "rise", "up"}
_NEG_WORDS = {"crash", "fall", "drop", "decline", "loss", "negative", "down", "cut", "warn", "fear"}


def infer_sentiment(text: str) -> str:
    lower = text.lower()
    pos = sum(1 for w in _POS_WORDS if w in lower)
    neg = sum(1 for w in _NEG_WORDS if w in lower)
    if pos > neg:
        return "positive"
    if neg > pos:
        return "negative"
    return "neutral"


# ── 피드 파싱 ─────────────────────────────────────────────────────────────

def parse_feed(feed_url: str, seen: set[str], producer: Producer) -> int:
    try:
        feed = feedparser.parse(feed_url)
    except Exception as e:
        log.warning("피드 파싱 오류 [%s]: %s", feed_url, e)
        return 0

    count = 0
    for entry in feed.entries:
        url = entry.get("link", "")
        if not url or url in seen:
            continue

        title   = entry.get("title", "")
        summary = entry.get("summary", "")
        source  = feed.feed.get("title", feed_url)

        # 발행 시각 (feedparser는 time.struct_time 반환)
        published_at = None
        if hasattr(entry, "published_parsed") and entry.published_parsed:
            import calendar
            ts = calendar.timegm(entry.published_parsed)
            from datetime import datetime, timezone
            published_at = datetime.fromtimestamp(ts, tz=timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")

        doc = {
            "source":      source,
            "title":       title,
            "content":     summary,
            "url":         url,
            "sentiment":   infer_sentiment(title + " " + summary),
            "country":     "UNKNOWN",
            "category":    "",
            "score":       0.0,
            "publishedAt": published_at,
            "pipeline":    "rss",
        }

        producer.produce(
            KAFKA_TOPIC,
            key=url.encode(),
            value=json.dumps(doc, ensure_ascii=False).encode(),
            callback=delivery_report,
        )
        seen.add(url)
        count += 1

    return count


def main():
    producer = Producer({"bootstrap.servers": KAFKA_BOOTSTRAP_SERVERS})
    seen     = load_seen_urls()
    log.info("RSS 프로듀서 시작 (피드 %d개, 주기: %ds)", len(RSS_FEEDS), RSS_POLL_INTERVAL)

    while True:
        total = 0
        for feed_url in RSS_FEEDS:
            n = parse_feed(feed_url, seen, producer)
            total += n
            if n:
                log.info("[%s] 신규 %d건", feed_url, n)

        producer.flush()
        save_seen_urls(seen)

        if total:
            log.info("이번 라운드 총 %d건 전송", total)
        else:
            log.info("신규 기사 없음")

        time.sleep(RSS_POLL_INTERVAL)


if __name__ == "__main__":
    main()
