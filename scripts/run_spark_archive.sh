#!/usr/bin/env bash
# Kafka -> Iceberg Structured Streaming 실행 (Linux / macOS / WSL)
#
# 사용법:
#   bash scripts/run_spark_archive.sh
#
# 환경 변수 오버라이드:
#   KAFKA_BOOTSTRAP_SERVERS=localhost:9092 bash scripts/run_spark_archive.sh
#   ICEBERG_WAREHOUSE=/data/iceberg bash scripts/run_spark_archive.sh

set -euo pipefail

# 프로젝트 루트로 이동
cd "$(dirname "$0")/.."

echo "[INFO] Kafka -> Iceberg Structured Streaming 시작..."

spark-submit \
  --master "local[*]" \
  --packages "org.apache.spark:spark-sql-kafka-0-10_2.12:3.5.1,org.apache.iceberg:iceberg-spark-runtime-3.5_2.12:1.4.3" \
  --conf "spark.sql.extensions=org.apache.iceberg.spark.extensions.IcebergSparkSessionExtensions" \
  pipeline/spark_archive.py
