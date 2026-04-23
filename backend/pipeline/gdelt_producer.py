"""GDELT 2.0 15분 주기 이벤트 → Kafka 'news-raw' 프로듀서."""
import csv
import io
import json
import logging
import time
import zipfile
from pathlib import Path

import requests
from confluent_kafka import Producer

from config import (
    GDELT_MASTER_URL,
    GDELT_POLL_INTERVAL,
    GDELT_STATE_FILE,
    KAFKA_BOOTSTRAP_SERVERS,
    KAFKA_TOPIC,
)

logging.basicConfig(level=logging.INFO, format="%(asctime)s [GDELT] %(message)s")
log = logging.getLogger(__name__)

# GDELT 2.0 export CSV 컬럼 인덱스 (61컬럼)
COL_GLOBAL_EVENT_ID = 0
COL_DATE_ADDED      = 1   # YYYYMMDDHHMMSS
COL_SOURCEURL       = 60
COL_ACTOR1NAME      = 6
COL_ACTOR2NAME      = 16
COL_EVENT_CODE      = 26
COL_GOLDSTEIN       = 30  # Goldstein Scale (-10 ~ +10)
COL_AVG_TONE        = 34
COL_ACTION_GEO_COUNTRY = 51


def delivery_report(err, msg):
    if err:
        log.error("전송 실패 [%s]: %s", msg.topic(), err)


def load_last_url() -> str:
    p = Path(GDELT_STATE_FILE)
    return p.read_text().strip() if p.exists() else ""


def save_last_url(url: str):
    Path(GDELT_STATE_FILE).write_text(url)


def tone_to_sentiment(tone: float) -> str:
    if tone > 2:
        return "positive"
    if tone < -2:
        return "negative"
    return "neutral"


def fetch_export_url() -> str | None:
    """lastupdate.txt 에서 export CSV URL을 파싱."""
    try:
        resp = requests.get(GDELT_MASTER_URL, timeout=10)
        resp.raise_for_status()
        for line in resp.text.strip().splitlines():
            parts = line.split()
            if len(parts) >= 3 and "export" in parts[2]:
                return parts[2]
    except requests.RequestException as e:
        log.error("lastupdate.txt 조회 실패: %s", e)
    return None


def parse_and_produce(producer: Producer, zip_url: str):
    log.info("다운로드: %s", zip_url)
    try:
        resp = requests.get(zip_url, timeout=60)
        resp.raise_for_status()
    except requests.RequestException as e:
        log.error("CSV 다운로드 실패: %s", e)
        return

    count = 0
    with zipfile.ZipFile(io.BytesIO(resp.content)) as zf:
        csv_name = zf.namelist()[0]
        with zf.open(csv_name) as f:
            reader = csv.reader(io.TextIOWrapper(f, encoding="utf-8"), delimiter="\t")
            for row in reader:
                if len(row) < 61:
                    continue
                try:
                    tone      = float(row[COL_AVG_TONE]) if row[COL_AVG_TONE] else 0.0
                    goldstein = float(row[COL_GOLDSTEIN]) if row[COL_GOLDSTEIN] else 0.0
                    raw_date  = row[COL_DATE_ADDED]
                    published_at = (
                        f"{raw_date[:4]}-{raw_date[4:6]}-{raw_date[6:8]}T"
                        f"{raw_date[8:10]}:{raw_date[10:12]}:{raw_date[12:14]}Z"
                        if len(raw_date) >= 14
                        else None
                    )
                    doc = {
                        "source":      "GDELT",
                        "title":       f"{row[COL_ACTOR1NAME]} — {row[COL_EVENT_CODE]}",
                        "content":     f"{row[COL_ACTOR1NAME]} {row[COL_ACTOR2NAME]} {row[COL_EVENT_CODE]}",
                        "url":         row[COL_SOURCEURL],
                        "sentiment":   tone_to_sentiment(tone),
                        "country":     row[COL_ACTION_GEO_COUNTRY] or "UNKNOWN",
                        "category":    row[COL_EVENT_CODE],
                        "score":       goldstein,
                        "publishedAt": published_at,
                        "pipeline":    "gdelt",
                    }
                    producer.produce(
                        KAFKA_TOPIC,
                        key=row[COL_GLOBAL_EVENT_ID].encode(),
                        value=json.dumps(doc, ensure_ascii=False).encode(),
                        callback=delivery_report,
                    )
                    count += 1
                    if count % 500 == 0:
                        producer.poll(0)
                except (ValueError, IndexError) as e:
                    log.debug("행 파싱 오류: %s", e)

    producer.flush()
    log.info("Kafka 전송 완료: %d건", count)


def main():
    producer = Producer({"bootstrap.servers": KAFKA_BOOTSTRAP_SERVERS})
    log.info("GDELT 프로듀서 시작 (주기: %ds)", GDELT_POLL_INTERVAL)

    while True:
        export_url = fetch_export_url()
        if export_url and export_url != load_last_url():
            parse_and_produce(producer, export_url)
            save_last_url(export_url)
        else:
            log.info("새 GDELT 파일 없음")
        time.sleep(GDELT_POLL_INTERVAL)


if __name__ == "__main__":
    main()
