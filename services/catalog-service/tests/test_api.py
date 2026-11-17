import fakeredis
import pytest
from fastapi.testclient import TestClient

from app.config import Settings
from app.main import create_app
from app.repository import ProductRepository


@pytest.fixture
def client():
    repo = ProductRepository(fakeredis.FakeRedis(decode_responses=True), cache_ttl_seconds=30)
    settings = Settings(seed_products=20, initial_stock=5, restock_interval_seconds=3600)
    with TestClient(create_app(settings=settings, repo=repo)) as c:
        yield c


def test_health(client):
    assert client.get("/health/live").json() == {"status": "UP"}
    assert client.get("/health/ready").status_code == 200


def test_list_products_paginates(client):
    body = client.get("/products", params={"limit": 5}).json()
    assert body["total"] == 20
    assert len(body["items"]) == 5


def test_get_product_and_404(client):
    assert client.get("/products/1").json()["id"] == 1
    assert client.get("/products/999").status_code == 404


def test_reserve_decrements_and_conflicts(client):
    resp = client.post("/products/2/reserve", json={"quantity": 3})
    assert resp.status_code == 200
    assert resp.json()["remaining_stock"] == 2
    assert client.post("/products/2/reserve", json={"quantity": 3}).status_code == 409
    assert client.get("/products/2").json()["stock"] == 2


def test_reserve_validates_quantity(client):
    assert client.post("/products/2/reserve", json={"quantity": 0}).status_code == 422


def test_recommendations(client):
    recs = client.get("/products/1/recommendations", params={"limit": 3}).json()
    assert len(recs) == 3
    assert all(r["id"] != 1 for r in recs)


def test_chaos_error_returns_500(client):
    client_no_raise = TestClient(client.app, raise_server_exceptions=False)
    assert client_no_raise.get("/chaos/error").status_code == 500
