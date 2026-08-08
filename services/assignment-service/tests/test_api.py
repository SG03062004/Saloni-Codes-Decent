import pytest
from unittest.mock import AsyncMock, patch
from fastapi.testclient import TestClient


@pytest.fixture
def client():
    # Patch lifespan infrastructure so TestClient doesn't need real Kafka/MySQL
    with patch("app.main.init_db"), \
         patch("app.main.start_producer", new_callable=AsyncMock), \
         patch("app.main.stop_producer", new_callable=AsyncMock), \
         patch("app.main.consume_loop", new_callable=AsyncMock):
        from app.main import app
        with TestClient(app, raise_server_exceptions=True) as c:
            yield c


def test_health(client):
    r = client.get("/health")
    assert r.status_code == 200
    assert r.json() == {"status": "UP"}


def test_patch_location_valid(client):
    mock_session = AsyncMock()
    mock_cm = AsyncMock()
    mock_cm.__aenter__ = AsyncMock(return_value=mock_session)
    mock_cm.__aexit__ = AsyncMock(return_value=False)

    with patch("app.main.get_session_factory") as mock_factory, \
         patch("app.main.upsert_driver_location", new_callable=AsyncMock) as mock_upsert:
        mock_factory.return_value = lambda: mock_cm
        r = client.patch(
            "/drivers/driver-abc/location",
            json={"lat": 51.5, "lng": -0.1, "is_available": True},
        )

    assert r.status_code == 204


def test_patch_location_invalid_lat(client):
    r = client.patch(
        "/drivers/driver-abc/location",
        json={"lat": 999.0, "lng": -0.1, "is_available": True},
    )
    assert r.status_code == 422


def test_patch_location_invalid_lng(client):
    r = client.patch(
        "/drivers/driver-abc/location",
        json={"lat": 51.5, "lng": 200.0, "is_available": True},
    )
    assert r.status_code == 422


def test_patch_location_missing_fields(client):
    r = client.patch("/drivers/driver-abc/location", json={"lat": 51.5})
    assert r.status_code == 422
