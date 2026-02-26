"""Iceberg HadoopCatalog 초기화 — 최초 1회 실행.

실행 방법:
    cd c:/project/newsquery
    python scripts/setup_iceberg.py

동작:
  1. [Windows] winutils.exe 자동 다운로드 → HADOOP_HOME 설정
  2. iceberg-warehouse 디렉토리 생성
  3. PySpark + Iceberg JAR 로드 (첫 실행 시 Maven Central에서 ~30MB 다운로드)
  4. hadoop_catalog.newsdb 데이터베이스 생성
  5. news_archive 테이블 생성 (PARTITIONED BY days(ingested_at))
  6. 성공 여부 출력 + 다음 단계 안내
"""
import os
import sys
import logging
import urllib.request

# pipeline 디렉토리를 경로에 추가
sys.path.insert(0, os.path.join(os.path.dirname(__file__), "..", "pipeline"))
from config import ICEBERG_WAREHOUSE, ICEBERG_DB, ICEBERG_TABLE

logging.basicConfig(level=logging.WARN)  # Spark 내부 로그 최소화

PACKAGES   = "org.apache.iceberg:iceberg-spark-runtime-3.5_2.12:1.4.3"
FULL_TABLE = f"{ICEBERG_DB}.{ICEBERG_TABLE}"

# Windows PySpark 실행에 필요한 Hadoop 바이너리 (Hadoop 3.3.5 빌드)
_WINUTILS_BASE = "https://github.com/cdarlint/winutils/raw/master/hadoop-3.3.5/bin"
_WIN_BINS      = ["winutils.exe", "hadoop.dll"]   # hadoop.dll: native IO 필수


def setup_winutils_windows():
    """Windows에서 PySpark + Iceberg가 필요로 하는 Hadoop 바이너리를 자동으로 준비."""
    if sys.platform != "win32":
        return

    hadoop_home = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", "hadoop-winutils"))
    bin_dir     = os.path.join(hadoop_home, "bin")
    os.makedirs(bin_dir, exist_ok=True)

    for fname in _WIN_BINS:
        fpath = os.path.join(bin_dir, fname)
        if not os.path.exists(fpath):
            print(f"[..] {fname} 다운로드 중...")
            try:
                urllib.request.urlretrieve(f"{_WINUTILS_BASE}/{fname}", fpath)
                print(f"[OK] {fname} 다운로드 완료")
            except Exception as e:
                print(f"[WARN] {fname} 다운로드 실패: {e}")
                print("  수동 설치: https://github.com/cdarlint/winutils")
                print(f"  hadoop-3.3.5/bin/{fname} → hadoop-winutils/bin/{fname}")
        else:
            print(f"[OK] {fname} 확인")

    os.environ["HADOOP_HOME"]     = hadoop_home
    os.environ["hadoop.home.dir"] = hadoop_home


def build_spark():
    from pyspark.sql import SparkSession
    return (
        SparkSession.builder
        .appName("newsquery-iceberg-setup")
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


def main():
    # 0. Windows winutils 설정 (HADOOP_HOME 설정 전에 import 전 실행 필요)
    setup_winutils_windows()

    # 1. Warehouse 디렉토리 생성
    os.makedirs(ICEBERG_WAREHOUSE, exist_ok=True)
    print(f"[OK] Iceberg 워크하우스: {ICEBERG_WAREHOUSE}")

    # 2. Spark 세션 시작 (첫 실행 시 JAR 다운로드)
    print("[..] PySpark + Iceberg JAR 로드 중 (첫 실행 시 수십 초 소요)...")
    spark = build_spark()
    spark.sparkContext.setLogLevel("ERROR")
    print("[OK] PySpark 세션 준비 완료")

    # 3. 데이터베이스 생성
    spark.sql(f"CREATE DATABASE IF NOT EXISTS hadoop_catalog.{ICEBERG_DB}")
    print(f"[OK] 데이터베이스: hadoop_catalog.{ICEBERG_DB}")

    # 4. 테이블 생성
    spark.sql(f"""
        CREATE TABLE IF NOT EXISTS hadoop_catalog.{FULL_TABLE} (
            source      STRING,
            title       STRING,
            content     STRING,
            url         STRING,
            sentiment   STRING,
            country     STRING,
            category    STRING,
            score       DOUBLE,
            publishedAt STRING,
            pipeline    STRING,
            ingested_at TIMESTAMP
        )
        USING iceberg
        PARTITIONED BY (days(ingested_at))
    """)
    print(f"[OK] 테이블 생성: hadoop_catalog.{FULL_TABLE}")
    print(f"[OK] 파티션 전략: days(ingested_at)")

    # 5. 파일시스템으로 테이블 메타데이터 확인
    table_path    = os.path.join(ICEBERG_WAREHOUSE, ICEBERG_DB, ICEBERG_TABLE)
    metadata_path = os.path.join(table_path, "metadata")
    if os.path.isdir(metadata_path):
        meta_files = os.listdir(metadata_path)
        print(f"[OK] 메타데이터 파일 수: {len(meta_files)}")
    else:
        print(f"[WARN] 메타데이터 디렉토리 없음: {metadata_path}")

    spark.stop()

    print()
    print("=" * 55)
    print("  Iceberg 초기화 완료")
    print("=" * 55)
    print()
    print("다음 단계:")
    print("  1. docker-compose up -d                   # Kafka 시작")
    print("  2. python scripts/update_es_mapping.py    # ES 매핑 추가")
    print("  3. python pipeline/news_worker.py         # Kafka → ES")
    print("  4. scripts/run_spark_archive.bat          # Kafka → Iceberg")
    print("  5. python scripts/query_iceberg.py        # 데이터 확인")


if __name__ == "__main__":
    main()
