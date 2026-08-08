import pytest
from fastapi.testclient import TestClient

# Reset metrics state before each test so counter assertions are deterministic
import app.metrics as _metrics_module


@pytest.fixture(autouse=True)
def reset_metrics():
    """Reset in-memory counters before every test."""
    import app.metrics as m
    m._requests_total = 0
    m._errors_total   = 0
    m._latency_sum_ms = 0.0
    yield


@pytest.fixture
def client():
    from app.main import app
    return TestClient(app)


VALID_PAYLOAD = {
    "distance_km":         6.0,
    "prep_time_minutes":   10,
    "driver_availability": 0.8,
    "traffic_factor":      0.2,
}


# ── /predict-eta contract ────────────────────────────────────────────────────

def test_predict_eta_returns_200(client):
    r = client.post("/predict-eta", json=VALID_PAYLOAD)
    assert r.status_code == 200


def test_predict_eta_response_schema(client):
    r = client.post("/predict-eta", json=VALID_PAYLOAD)
    body = r.json()
    assert "estimated_delivery_minutes" in body
    assert isinstance(body["estimated_delivery_minutes"], int)
    assert body["estimated_delivery_minutes"] >= 5


def test_predict_eta_with_order_id(client):
    payload = {**VALID_PAYLOAD, "order_id": "order-abc-123"}
    r = client.post("/predict-eta", json=payload)
    assert r.status_code == 200


def test_predict_eta_rejects_negative_distance(client):
    r = client.post("/predict-eta", json={**VALID_PAYLOAD, "distance_km": -1.0})
    assert r.status_code == 422


def test_predict_eta_rejects_traffic_above_1(client):
    r = client.post("/predict-eta", json={**VALID_PAYLOAD, "traffic_factor": 1.5})
    assert r.status_code == 422


def test_predict_eta_rejects_missing_field(client):
    payload = {k: v for k, v in VALID_PAYLOAD.items() if k != "distance_km"}
    r = client.post("/predict-eta", json=payload)
    assert r.status_code == 422


# ── /health ──────────────────────────────────────────────────────────────────

def test_health_returns_up(client):
    r = client.get("/health")
    assert r.status_code == 200
    assert r.json() == {"status": "UP"}


# ── /metrics-lite ────────────────────────────────────────────────────────────

def test_metrics_lite_schema(client):
    r = client.get("/metrics-lite")
    assert r.status_code == 200
    body = r.json()
    for key in ("service", "status", "uptime_seconds", "requests_total",
                "errors_total", "avg_latency_ms"):
        assert key in body, f"missing key: {key}"


def test_metrics_lite_counters_increment(client):
    client.post("/predict-eta", json=VALID_PAYLOAD)
    client.post("/predict-eta", json=VALID_PAYLOAD)
    r = client.get("/metrics-lite")
    # TestClient runs middleware synchronously: the 2 predict-eta calls are
    # fully recorded before /metrics-lite is read, giving requests_total >= 2.
    assert r.json()["requests_total"] >= 2


def test_metrics_lite_service_name(client):
    r = client.get("/metrics-lite")
    assert r.json()["service"] == "eta-service"
