@echo off
REM Kafka -> Iceberg Structured Streaming 실행 (Windows)
REM
REM 사용법:
REM   scripts\run_spark_archive.bat
REM
REM 환경 변수 오버라이드:
REM   set KAFKA_BOOTSTRAP_SERVERS=localhost:9092
REM   set ICEBERG_WAREHOUSE=D:\mydata\iceberg
REM   scripts\run_spark_archive.bat

cd /d "%~dp0.."

echo [INFO] Kafka -^> Iceberg Structured Streaming 시작...

spark-submit ^
  --master "local[*]" ^
  --packages "org.apache.spark:spark-sql-kafka-0-10_2.12:3.5.1,org.apache.iceberg:iceberg-spark-runtime-3.5_2.12:1.4.3" ^
  --conf "spark.sql.extensions=org.apache.iceberg.spark.extensions.IcebergSparkSessionExtensions" ^
  pipeline\spark_archive.py

if %ERRORLEVEL% neq 0 (
    echo [ERROR] spark-submit 실패. spark-submit 이 PATH 에 있는지 확인하세요.
    echo         pip install pyspark 로 설치한 경우:
    echo         python -m pyspark.bin.spark-submit 를 시도하세요.
    exit /b 1
)
