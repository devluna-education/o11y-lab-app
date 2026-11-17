"""Deliberately CPU-bound recommendation scoring, to give profilers something to look at."""

import math

from app.models import Product


def _vector(product: Product, dims: int = 64) -> list[float]:
    seed = sum(ord(c) for c in product.name) + product.id
    return [math.sin(seed * (i + 1)) * math.log1p(product.price) for i in range(dims)]


def _cosine(a: list[float], b: list[float]) -> float:
    dot = sum(x * y for x, y in zip(a, b))
    norm = math.sqrt(sum(x * x for x in a)) * math.sqrt(sum(y * y for y in b))
    return dot / norm if norm else 0.0


def recommend(target: Product, candidates: list[Product], limit: int = 5) -> list[Product]:
    target_vec = _vector(target)
    scored = []
    for candidate in candidates:
        if candidate.id == target.id:
            continue
        score = _cosine(target_vec, _vector(candidate))
        if candidate.category == target.category:
            score += 0.5
        scored.append((score, candidate))
    scored.sort(key=lambda item: item[0], reverse=True)
    return [product for _, product in scored[:limit]]
