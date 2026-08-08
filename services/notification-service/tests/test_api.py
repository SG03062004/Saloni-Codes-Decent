from unittest.mock import AsyncMock, patch
from fastapi.testclient import TestClient


def _make_client():
    with patch("app.main.init_db"), \
         patch("app.main.consume_loop", new_callable=AsyncMock):
        from app.main import app
        return TestClient(app)


def test_health():
    client = _make_client()
    r = client.get("/health")
    assert r.status_code == 200
    assert r.json() == {"status": "UP"}
