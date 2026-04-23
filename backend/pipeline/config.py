"""파이프라인 공통 설정 — 환경 변수로 오버라이드 가능."""
import os

# ── Kafka ──────────────────────────────────────────────────────────────────
KAFKA_BOOTSTRAP_SERVERS = os.getenv("KAFKA_BOOTSTRAP_SERVERS", "localhost:9092")
KAFKA_TOPIC             = os.getenv("KAFKA_TOPIC", "news-raw")
KAFKA_GROUP_ID          = os.getenv("KAFKA_GROUP_ID", "news-worker-group")

# ── Elasticsearch ──────────────────────────────────────────────────────────
ES_HOST  = os.getenv("ES_HOST", "http://localhost:9200")
ES_INDEX = os.getenv("ES_INDEX", "news")

# ── Embedding service ──────────────────────────────────────────────────────
EMBEDDING_SERVICE_URL = os.getenv("EMBEDDING_SERVICE_URL", "http://localhost:8000")
EMBEDDING_MODEL       = os.getenv("EMBEDDING_MODEL", "all-MiniLM-L6-v2")
EMBEDDING_DIMS        = int(os.getenv("EMBEDDING_DIMS", "384"))

# ── GDELT ──────────────────────────────────────────────────────────────────
GDELT_MASTER_URL    = "http://data.gdeltproject.org/gdeltv2/lastupdate.txt"
GDELT_POLL_INTERVAL = int(os.getenv("GDELT_POLL_INTERVAL", "900"))  # 15분
GDELT_STATE_FILE    = os.getenv("GDELT_STATE_FILE", "/tmp/gdelt_last.txt")

# ── RSS ────────────────────────────────────────────────────────────────────
RSS_FEEDS = [
    # 글로벌 종합
    "https://feeds.reuters.com/reuters/topNews",
    "https://feeds.bbci.co.uk/news/rss.xml",
    "https://rss.nytimes.com/services/xml/rss/nyt/World.xml",
    "https://feeds.feedburner.com/businessinsider",
    "https://www.wsj.com/xml/rss/3_7085.xml",
    # 테크/경제
    "https://feeds.feedburner.com/TechCrunch",
    "https://feeds.arstechnica.com/arstechnica/index",
    "https://rss.nytimes.com/services/xml/rss/nyt/Technology.xml",
    # 한국
    "https://www.yna.co.kr/rss/news.xml",
    "https://rss.hankyung.com/economy.xml",
]
RSS_POLL_INTERVAL = int(os.getenv("RSS_POLL_INTERVAL", "300"))   # 5분
RSS_DEDUP_FILE    = os.getenv("RSS_DEDUP_FILE", "/tmp/rss_seen_urls.txt")
RSS_DEDUP_MAX     = int(os.getenv("RSS_DEDUP_MAX", "50000"))

# ── Iceberg / Spark ────────────────────────────────────────────────────────
# 기본값: 프로젝트 루트/iceberg-warehouse (재부팅 후에도 유지됨)
# 환경 변수 ICEBERG_WAREHOUSE 로 오버라이드 가능
_PROJECT_ROOT     = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ICEBERG_WAREHOUSE = os.getenv("ICEBERG_WAREHOUSE", os.path.join(_PROJECT_ROOT, "iceberg-warehouse"))
ICEBERG_DB          = os.getenv("ICEBERG_DB", "newsdb")
ICEBERG_TABLE       = os.getenv("ICEBERG_TABLE", "news_archive")
SPARK_OFFSETS_START = os.getenv("SPARK_OFFSETS_START", "earliest")
