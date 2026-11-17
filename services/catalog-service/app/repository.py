"""Redis-backed product repository with a read-through cache for listings."""

import json
import logging
import random

from redis import Redis

from app.models import Product

logger = logging.getLogger(__name__)

PRODUCT_KEY = "product:{id}"
PRODUCT_IDS_KEY = "products:ids"
LIST_CACHE_KEY = "cache:products:{category}:{q}"

CATEGORIES = ["books", "electronics", "games", "garden", "kitchen", "sports", "toys"]
ADJECTIVES = ["Smart", "Classic", "Ultra", "Eco", "Pro", "Mini", "Deluxe", "Rapid"]
NOUNS = ["Lamp", "Kettle", "Drone", "Novel", "Racket", "Puzzle", "Speaker", "Shovel", "Backpack", "Watch"]


class ProductNotFoundError(Exception):
    pass


class OutOfStockError(Exception):
    pass


class ProductRepository:
    def __init__(self, redis: Redis, cache_ttl_seconds: int = 30):
        self._redis = redis
        self._cache_ttl = cache_ttl_seconds

    def ping(self) -> bool:
        return bool(self._redis.ping())

    def seed(self, count: int, initial_stock: int) -> int:
        """Create a deterministic product catalog if none exists. Returns products created."""
        if self._redis.scard(PRODUCT_IDS_KEY) > 0:
            return 0
        rng = random.Random(42)
        pipe = self._redis.pipeline()
        for pid in range(1, count + 1):
            pipe.hset(
                PRODUCT_KEY.format(id=pid),
                mapping={
                    "id": pid,
                    "name": f"{rng.choice(ADJECTIVES)} {rng.choice(NOUNS)} {pid}",
                    "category": rng.choice(CATEGORIES),
                    "price": round(rng.uniform(5, 500), 2),
                    "stock": initial_stock,
                },
            )
            pipe.sadd(PRODUCT_IDS_KEY, pid)
        pipe.execute()
        logger.info("Seeded %d products", count)
        return count

    def get(self, product_id: int) -> Product:
        data = self._redis.hgetall(PRODUCT_KEY.format(id=product_id))
        if not data:
            raise ProductNotFoundError(product_id)
        return Product(**data)

    def _all(self) -> list[Product]:
        ids = sorted(int(i) for i in self._redis.smembers(PRODUCT_IDS_KEY))
        pipe = self._redis.pipeline()
        for pid in ids:
            pipe.hgetall(PRODUCT_KEY.format(id=pid))
        return [Product(**row) for row in pipe.execute() if row]

    def search(self, category: str | None = None, q: str | None = None) -> list[Product]:
        cache_key = LIST_CACHE_KEY.format(category=category or "*", q=(q or "").lower())
        cached = self._redis.get(cache_key)
        if cached:
            logger.debug("Cache hit for %s", cache_key)
            return [Product(**p) for p in json.loads(cached)]

        logger.debug("Cache miss for %s", cache_key)
        products = self._all()
        if category:
            products = [p for p in products if p.category == category]
        if q:
            products = [p for p in products if q.lower() in p.name.lower()]
        self._redis.setex(cache_key, self._cache_ttl, json.dumps([p.model_dump() for p in products]))
        return products

    def reserve(self, product_id: int, quantity: int) -> int:
        key = PRODUCT_KEY.format(id=product_id)
        if not self._redis.exists(key):
            raise ProductNotFoundError(product_id)
        remaining = self._redis.hincrby(key, "stock", -quantity)
        if remaining < 0:
            self._redis.hincrby(key, "stock", quantity)
            raise OutOfStockError(product_id)
        return remaining

    def restock(self, threshold: int, target: int) -> int:
        """Bring every product whose stock is below `threshold` back to `target`."""
        restocked = 0
        for product in self._all():
            if product.stock < threshold:
                self._redis.hset(PRODUCT_KEY.format(id=product.id), "stock", target)
                restocked += 1
        if restocked:
            logger.info("Restocked %d products", restocked)
        return restocked
