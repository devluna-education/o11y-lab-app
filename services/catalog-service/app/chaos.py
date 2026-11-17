"""Fault injection: random latency/errors via middleware plus on-demand endpoints."""

import asyncio
import logging
import random
import time

from fastapi import APIRouter, Query, Request
from fastapi.responses import JSONResponse
from starlette.middleware.base import BaseHTTPMiddleware

logger = logging.getLogger(__name__)

router = APIRouter(prefix="/chaos", tags=["chaos"])


class ChaosMiddleware(BaseHTTPMiddleware):
    """Applies configured latency and error rate to /products requests."""

    def __init__(self, app, error_rate: float, latency_ms: int):
        super().__init__(app)
        self.error_rate = error_rate
        self.latency_ms = latency_ms

    async def dispatch(self, request: Request, call_next):
        if not request.url.path.startswith("/products"):
            return await call_next(request)
        if self.latency_ms > 0:
            await asyncio.sleep(random.uniform(0, self.latency_ms) / 1000)
        if self.error_rate > 0 and random.random() < self.error_rate:
            logger.error("Chaos: injected failure for %s %s", request.method, request.url.path)
            return JSONResponse(status_code=500, content={"detail": "Injected failure"})
        return await call_next(request)


@router.get("/error")
def chaos_error():
    logger.error("Chaos: raising exception on demand")
    raise RuntimeError("Chaos: exception triggered on demand")


@router.get("/cpu")
def chaos_cpu(duration_ms: int = Query(500, ge=1, le=10000)):
    end = time.perf_counter() + duration_ms / 1000
    acc = 0.0
    while time.perf_counter() < end:
        acc += random.random() ** 0.5
    logger.warning("Chaos: burned CPU for %d ms", duration_ms)
    return {"burned_ms": duration_ms, "result": acc}


@router.get("/slow")
def chaos_slow(duration_ms: int = Query(2000, ge=1, le=30000)):
    time.sleep(duration_ms / 1000)
    logger.warning("Chaos: slow response of %d ms", duration_ms)
    return {"slept_ms": duration_ms}
