"""PySpark Structured Streaming — Kafka 'news-raw' → Iceberg HadoopCatalog 아카이브."""
import json
import logging
import sys

from pyspark.sql import SparkSession
from pyspark.sql.functions import col, from_json, to_timestamp
from pyspark.sql.types import (
    DoubleType,
    StringType,
    StructField,
    StructType,
)

sys.path.insert(0, ".")
from config import (
    ICEBERG_DB,
    ICEBERG_TABLE,
    ICEBERG_WAREHOUSE,
    KAFKA_BOOTSTRAP_SERVERS,
    KAFKA_TOPIC,
    SPARK_OFFSETS_START,
)

logging.basicConfig(level=logging.INFO, format="%(asctime)s [SPARK] %(message)s")
log = logging.getLogger(__name__)

NEWS_SCHEMA = StructType([
    StructField("source",      StringType(), True),
    StructField("title",       StringType(), True),
    StructField("content",     StringType(), True),
    StructField("url",         StringType(), True),
    StructField("sentiment",   StringType(), True),
    StructField("country",     StringType(), True),
    StructField("category",    StringType(), True),
    StructField("score",       DoubleType(), True),
    StructField("publishedAt", StringType(), True),
    StructField("pipeline",    StringType(), True),
])

FULL_TABLE = f"{ICEBERG_DB}.{ICEBERG_TABLE}"
CHECKPOINT = f"{ICEBERG_WAREHOUSE}/checkpoint/{ICEBERG_TABLE}"


def build_spark() -> SparkSession:
    return (
        SparkSession.builder
        .appName("newsquery-spark-archive")
        .config("spark.sql.extensions",
                "org.apache.iceberg.spark.extensions.IcebergSparkSessionExtensions")
        .config("spark.sql.catalog.hadoop_catalog",
                "org.apache.iceberg.spark.SparkCatalog")
        .config("spark.sql.catalog.hadoop_catalog.type", "hadoop")
        .config("spark.sql.catalog.hadoop_catalog.warehouse", ICEBERG_WAREHOUSE)
        # Kafka 커넥터 JAR (spark-submit 시 --packages 로도 제공 가능)
        .config("spark.jars.packages",
                "org.apache.spark:spark-sql-kafka-0-10_2.12:3.5.1,"
                "org.apache.iceberg:iceberg-spark-runtime-3.5_2.12:1.4.3")
        .getOrCreate()
    )


def ensure_table(spark: SparkSession):
    spark.sql(f"CREATE DATABASE IF NOT EXISTS hadoop_catalog.{ICEBERG_DB}")
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
    log.info("Iceberg 테이블 준비: hadoop_catalog.%s", FULL_TABLE)


def main():
    spark = build_spark()
    spark.sparkContext.setLogLevel("WARN")
    ensure_table(spark)

    raw_df = (
        spark.readStream
        .format("kafka")
        .option("kafka.bootstrap.servers", KAFKA_BOOTSTRAP_SERVERS)
        .option("subscribe", KAFKA_TOPIC)
        .option("startingOffsets", SPARK_OFFSETS_START)
        .option("failOnDataLoss", "false")
        .load()
    )

    parsed_df = (
        raw_df
        .select(
            from_json(col("value").cast("string"), NEWS_SCHEMA).alias("data"),
            col("timestamp").alias("ingested_at"),
        )
        .select("data.*", "ingested_at")
    )

    query = (
        parsed_df.writeStream
        .format("iceberg")
        .outputMode("append")
        .option("path", f"hadoop_catalog.{FULL_TABLE}")
        .option("checkpointLocation", CHECKPOINT)
        .trigger(processingTime="60 seconds")
        .start()
    )

    log.info("Spark Structured Streaming 시작 — Ctrl+C로 종료")
    query.awaitTermination()


if __name__ == "__main__":
    main()
