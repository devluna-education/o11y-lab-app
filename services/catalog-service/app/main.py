import logging
import threading
from contextlib import asynccontextmanager

from fastapi import FastAPI, HTTPException, Query, Request
from fastapi.responses import JSONResponse
from redis import Redis

from app import chaos
from app.config import Settings, get_settings
from app.models import Product, ProductPage, ReserveRequest, ReserveResponse
from app.recommendations import recommend
from app.repository import OutOfStockError, ProductNotFoundError, ProductRepository

logger = logging.getLogger("catalog-service")


def configure_logging(level: str) -> None:
    logging.basicConfig(
        level=level.upper(),
        format="%(asctime)s %(levelname)s [%(name)s] %(message)s",
    )


def _restock_loop(repo: ProductRepository, settings: Settings, stop: threading.Event) -> None:
    while not stop.wait(settings.restock_interval_seconds):
        try:
            repo.restock(settings.restock_threshold, settings.initial_stock)
        except Exception:  # keep the background loop alive on transient Redis errors
            logger.exception("Restock iteration failed")


def create_app(settings: Settings | None = None, repo: ProductRepository | None = None) -> FastAPI:
    settings = settings or get_settings()
    configure_logging(settings.log_level)

    @asynccontextmanager
    async def lifespan(app: FastAPI):
        repository = repo or ProductRepository(
            Redis.from_url(settings.redis_url, decode_responses=True, socket_timeout=2),
            settings.cache_ttl_seconds,
        )
        app.state.repo = repository
        try:
            repository.seed(settings.seed_products, settings.initial_stock)
        except Exception:
            logger.exception("Could not seed catalog (Redis unavailable?); will retry on readiness")

        stop = threading.Event()
        worker = threading.Thread(target=_restock_loop, args=(repository, settings, stop), daemon=True)
        worker.start()
        logger.info("%s started", settings.service_name)
        yield
        stop.set()
        logger.info("%s stopped", settings.service_name)

    app = FastAPI(title="catalog-service", version="1.0.0", lifespan=lifespan)
    app.add_middleware(
        chaos.ChaosMiddleware,
        error_rate=settings.chaos_error_rate,
        latency_ms=settings.chaos_latency_ms,
    )
    app.include_router(chaos.router)

    def repository(request: Request) -> ProductRepository:
        return request.app.state.repo

    @app.exception_handler(Exception)
    async def unhandled(_: Request, exc: Exception):
        logger.exception("Unhandled error: %s", exc)
        return JSONResponse(status_code=500, content={"detail": str(exc)})

    @app.get("/health/live", tags=["health"])
    def live():
        return {"status": "UP"}

    @app.get("/health/ready", tags=["health"])
    def ready(request: Request):
        repo_ = repository(request)
        try:
            repo_.ping()
            repo_.seed(settings.seed_products, settings.initial_stock)
        except Exception as exc:
            logger.warning("Readiness check failed: %s", exc)
            return JSONResponse(status_code=503, content={"status": "DOWN"})
        return {"status": "UP"}

    @app.get("/products", response_model=ProductPage, tags=["products"])
    def list_products(
        request: Request,
        category: str | None = None,
        q: str | None = Query(None, max_length=50),
        skip: int = Query(0, ge=0),
        limit: int = Query(10, ge=1, le=100),
    ):
        products = repository(request).search(category, q)
        return ProductPage(total=len(products), items=products[skip : skip + limit])

    @app.get("/products/{product_id}", response_model=Product, tags=["products"])
    def get_product(request: Request, product_id: int):
        try:
            return repository(request).get(product_id)
        except ProductNotFoundError:
            logger.warning("Product %d not found", product_id)
            raise HTTPException(status_code=404, detail="Product not found")

    @app.get("/products/{product_id}/recommendations", response_model=list[Product], tags=["products"])
    def get_recommendations(request: Request, product_id: int, limit: int = Query(5, ge=1, le=20)):
        repo_ = repository(request)
        try:
            target = repo_.get(product_id)
        except ProductNotFoundError:
            raise HTTPException(status_code=404, detail="Product not found")
        return recommend(target, repo_.search(), limit)

    @app.post("/products/{product_id}/reserve", response_model=ReserveResponse, tags=["products"])
    def reserve(request: Request, product_id: int, body: ReserveRequest):
        try:
            remaining = repository(request).reserve(product_id, body.quantity)
        except ProductNotFoundError:
            raise HTTPException(status_code=404, detail="Product not found")
        except OutOfStockError:
            logger.warning("Out of stock: product=%d requested=%d", product_id, body.quantity)
            raise HTTPException(status_code=409, detail="Out of stock")
        logger.info("Reserved %d units of product %d (remaining=%d)", body.quantity, product_id, remaining)
        return ReserveResponse(product_id=product_id, reserved=body.quantity, remaining_stock=remaining)

    return app


app = create_app()
