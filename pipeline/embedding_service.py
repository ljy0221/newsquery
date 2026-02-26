"""FastAPI 임베딩 서비스 — sentence-transformers 모델을 HTTP로 제공."""
import logging
from contextlib import asynccontextmanager
from typing import Any

from fastapi import FastAPI, HTTPException
from pydantic import BaseModel
from sentence_transformers import SentenceTransformer

from config import EMBEDDING_DIMS, EMBEDDING_MODEL

logging.basicConfig(level=logging.INFO, format="%(asctime)s [EMBED] %(message)s")
log = logging.getLogger(__name__)

# ── 모델 인스턴스 (앱 수명 동안 단일 로드) ──────────────────────────────────
_model: SentenceTransformer | None = None


@asynccontextmanager
async def lifespan(app: FastAPI):
    global _model
    log.info("모델 로드 시작: %s", EMBEDDING_MODEL)
    _model = SentenceTransformer(EMBEDDING_MODEL)
    log.info("모델 로드 완료 (dims=%d)", EMBEDDING_DIMS)
    yield
    _model = None
    log.info("모델 해제")


app = FastAPI(title="Embedding Service", lifespan=lifespan)


# ── Request / Response 스키마 ──────────────────────────────────────────────

class EmbedRequest(BaseModel):
    texts: list[str]


class EmbedSingleRequest(BaseModel):
    text: str


class EmbedResponse(BaseModel):
    vectors: list[list[float]]
    dims: int


class EmbedSingleResponse(BaseModel):
    vector: list[float]
    dims: int


# ── 엔드포인트 ─────────────────────────────────────────────────────────────

@app.post("/embed", response_model=EmbedResponse)
def embed_batch(req: EmbedRequest) -> Any:
    if _model is None:
        raise HTTPException(status_code=503, detail="모델이 아직 로드되지 않았습니다.")
    if not req.texts:
        raise HTTPException(status_code=400, detail="texts가 비어있습니다.")

    vectors = _model.encode(
        req.texts,
        normalize_embeddings=True,
        show_progress_bar=False,
    )
    return {"vectors": [v.tolist() for v in vectors], "dims": EMBEDDING_DIMS}


@app.post("/embed/single", response_model=EmbedSingleResponse)
def embed_single(req: EmbedSingleRequest) -> Any:
    if _model is None:
        raise HTTPException(status_code=503, detail="모델이 아직 로드되지 않았습니다.")
    if not req.text.strip():
        raise HTTPException(status_code=400, detail="text가 비어있습니다.")

    vector = _model.encode(req.text, normalize_embeddings=True, show_progress_bar=False)
    return {"vector": vector.tolist(), "dims": EMBEDDING_DIMS}


@app.get("/health")
def health() -> dict:
    return {"status": "ok", "model": EMBEDDING_MODEL, "dims": EMBEDDING_DIMS}


if __name__ == "__main__":
    import uvicorn
    uvicorn.run("embedding_service:app", host="0.0.0.0", port=8000, reload=False)
