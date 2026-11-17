from functools import lru_cache

from pydantic_settings import BaseSettings


class Settings(BaseSettings):
    """Runtime configuration, read from environment variables (case-insensitive)."""

    service_name: str = "catalog-service"
    log_level: str = "INFO"

    redis_url: str = "redis://localhost:6379/0"
    cache_ttl_seconds: int = 30

    seed_products: int = 50
    initial_stock: int = 100
    restock_threshold: int = 10
    restock_interval_seconds: int = 60

    # Fault injection
    chaos_error_rate: float = 0.0
    chaos_latency_ms: int = 0


@lru_cache
def get_settings() -> Settings:
    return Settings()
