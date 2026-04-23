"""Iceberg 테이블 조회 — 데이터 검증 스크립트.

사용법:
    cd c:/project/newsquery

    # 통계 + 스냅샷 정보 (기본, Spark 불필요)
    python scripts/query_iceberg.py

    # 최근 N건 조회 (Spark 사용)
    python scripts/query_iceberg.py --mode recent --limit 10

    # 타임 트래블 (특정 시각 기준 조회, Spark 사용)
    python scripts/query_iceberg.py --mode snapshot --as-of-timestamp "2025-01-16T00:00:00"
"""
import argparse
import datetime
import json
import os
import sys
import urllib.request

sys.path.insert(0, os.path.join(os.path.dirname(__file__), "..", "pipeline"))
from config import ICEBERG_WAREHOUSE, ICEBERG_DB, ICEBERG_TABLE

FULL_TABLE = f"{ICEBERG_DB}.{ICEBERG_TABLE}"
PACKAGES   = "org.apache.iceberg:iceberg-spark-runtime-3.5_2.12:1.4.3"

_WINUTILS_BASE = "https://github.com/cdarlint/winutils/raw/master/hadoop-3.3.5/bin"
_WIN_BINS      = ["winutils.exe", "hadoop.dll"]


# ── Windows winutils 설정 (Spark 사용 모드에서 필요) ──────────────────────

def setup_winutils_windows():
    if sys.platform != "win32":
        return
    hadoop_home = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", "hadoop-winutils"))
    bin_dir     = os.path.join(hadoop_home, "bin")
    os.makedirs(bin_dir, exist_ok=True)
    for fname in _WIN_BINS:
        fpath = os.path.join(bin_dir, fname)
        if not os.path.exists(fpath):
            try:
                urllib.request.urlretrieve(f"{_WINUTILS_BASE}/{fname}", fpath)
            except Exception:
                pass
    os.environ["HADOOP_HOME"]     = hadoop_home
    os.environ["hadoop.home.dir"] = hadoop_home


# ── 메타데이터 경로 유틸 ─────────────────────────────────────────────────

def _metadata_dir() -> str:
    return os.path.join(ICEBERG_WAREHOUSE, ICEBERG_DB, ICEBERG_TABLE, "metadata")


def _current_metadata_path() -> str | None:
    hint = os.path.join(_metadata_dir(), "version-hint.text")
    if not os.path.exists(hint):
        return None
    version = open(hint).read().strip()
    return os.path.join(_metadata_dir(), f"v{version}.metadata.json")


def _load_metadata() -> dict | None:
    path = _current_metadata_path()
    if not path or not os.path.exists(path):
        return None
    with open(path, encoding="utf-8") as f:
        return json.load(f)


# ── stats 모드 (Spark 불필요 — 메타데이터 JSON 직접 파싱) ─────────────────

def mode_stats():
    table_path = os.path.join(ICEBERG_WAREHOUSE, ICEBERG_DB, ICEBERG_TABLE)
    if not os.path.isdir(table_path):
        print(f"[ERROR] 테이블 디렉토리 없음: {table_path}")
        print("  setup_iceberg.py 를 먼저 실행하세요.")
        sys.exit(1)

    meta = _load_metadata()
    if not meta:
        print("[ERROR] 메타데이터 파일을 찾을 수 없습니다.")
        sys.exit(1)

    snapshots   = meta.get("snapshots", [])
    current_id  = meta.get("current-snapshot-id", -1)
    schema      = meta["schemas"][0]
    partition   = meta["partition-specs"][0]["fields"]
    properties  = meta.get("properties", {})

    print(f"워크하우스  : {ICEBERG_WAREHOUSE}")
    print(f"테이블      : hadoop_catalog.{FULL_TABLE}")
    print(f"포맷 버전   : v{meta.get('format-version', '?')}")
    print(f"컬럼 수     : {len(schema['fields'])}")
    print(f"파티션      : {[f['name'] for f in partition]}")
    print(f"스냅샷 수   : {len(snapshots)}")

    if current_id != -1 and snapshots:
        cur = next((s for s in snapshots if s["snapshot-id"] == current_id), None)
        if cur:
            ts_ms   = cur.get("timestamp-ms", 0)
            ts_str  = datetime.datetime.fromtimestamp(ts_ms / 1000).strftime("%Y-%m-%d %H:%M:%S")
            records = cur.get("summary", {}).get("total-records", "?")
            size    = cur.get("summary", {}).get("total-files-size", "?")
            print(f"현재 스냅샷 : id={current_id}")
            print(f"  시각      : {ts_str}")
            print(f"  레코드 수 : {records}")
            print(f"  파일 크기 : {size} bytes")
    else:
        print("현재 스냅샷 : (없음 — 아직 데이터가 없습니다)")

    if properties:
        print(f"속성        : {properties}")


# ── recent / snapshot 모드 (PySpark 사용) ────────────────────────────────

def _build_spark():
    setup_winutils_windows()
    from pyspark.sql import SparkSession
    spark = (
        SparkSession.builder
        .appName("newsquery-iceberg-query")
        .config("spark.sql.extensions",
                "org.apache.iceberg.spark.extensions.IcebergSparkSessionExtensions")
        .config("spark.sql.catalog.hadoop_catalog",
                "org.apache.iceberg.spark.SparkCatalog")
        .config("spark.sql.catalog.hadoop_catalog.type", "hadoop")
        .config("spark.sql.catalog.hadoop_catalog.warehouse", ICEBERG_WAREHOUSE)
        .config("spark.jars.packages", PACKAGES)
        .master("local[1]")
        .getOrCreate()
    )
    spark.sparkContext.setLogLevel("ERROR")
    return spark


def mode_recent(limit: int = 10):
    spark = _build_spark()
    try:
        df = spark.sql(
            f"SELECT title, source, sentiment, ingested_at "
            f"FROM hadoop_catalog.{FULL_TABLE} "
            f"ORDER BY ingested_at DESC "
            f"LIMIT {limit}"
        )
        total = spark.sql(f"SELECT COUNT(*) FROM hadoop_catalog.{FULL_TABLE}").collect()[0][0]
        print(f"총 건수: {total:,}")
        print()
        df.show(truncate=40)
    finally:
        spark.stop()


def mode_snapshot(as_of_timestamp: str):
    try:
        dt    = datetime.datetime.fromisoformat(as_of_timestamp)
        ts_ms = int(dt.timestamp() * 1000)
    except ValueError as e:
        print(f"[ERROR] 타임스탬프 파싱 실패: {e}  형식 예시: 2025-01-16T00:00:00")
        sys.exit(1)

    # 메타데이터에서 대상 스냅샷 탐색
    meta      = _load_metadata()
    snapshots = meta.get("snapshots", []) if meta else []
    past      = [s for s in snapshots if s.get("timestamp-ms", 0) <= ts_ms]
    if not past:
        print(f"[INFO] {as_of_timestamp} 이전 스냅샷 없음")
        return

    target = max(past, key=lambda s: s["timestamp-ms"])
    snap_id = target["snapshot-id"]
    ts_str  = datetime.datetime.fromtimestamp(target["timestamp-ms"] / 1000).strftime("%Y-%m-%d %H:%M:%S")
    print(f"타임 트래블  : {as_of_timestamp}")
    print(f"사용 스냅샷  : id={snap_id}, 시각={ts_str}")
    print(f"레코드 수    : {target.get('summary', {}).get('total-records', '?')}")

    spark = _build_spark()
    try:
        df = spark.sql(
            f"SELECT title, source, sentiment, ingested_at "
            f"FROM hadoop_catalog.{FULL_TABLE} "
            f"VERSION AS OF {snap_id} "
            f"LIMIT 10"
        )
        df.show(truncate=40)
    finally:
        spark.stop()


# ── main ─────────────────────────────────────────────────────────────────

def main():
    parser = argparse.ArgumentParser(description="Iceberg 테이블 조회")
    parser.add_argument("--mode", choices=["stats", "recent", "snapshot"], default="stats")
    parser.add_argument("--limit", type=int, default=10)
    parser.add_argument("--as-of-timestamp", default=None)
    args = parser.parse_args()

    if args.mode == "stats":
        mode_stats()
    elif args.mode == "recent":
        mode_recent(args.limit)
    elif args.mode == "snapshot":
        if not args.as_of_timestamp:
            parser.error("--mode snapshot 시 --as-of-timestamp 필요")
        mode_snapshot(args.as_of_timestamp)


if __name__ == "__main__":
    main()
