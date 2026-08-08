import time

from fastapi import FastAPI, Request
from fastapi.responses import JSONResponse

from app import metrics
from app.calculator import predict_eta
from app.logging_config import get_logger
from app.models import EtaRequest, EtaResponse, HealthResponse, MetricsLiteResponse

app = FastAPI(title="eta-service", version="0.0.1")
log = get_logger("eta-service")


# ── Middleware: record latency + errors for every request ────────────────────

@app.middleware("http")
async def metrics_middleware(request: Request, call_next):
    start = time.monotonic()
    error = False
    try:
        response = await call_next(request)
        if response.status_code >= 500:
            error = True
        return response
    except Exception:
        error = True
        raise
    finally:
        latency_ms = (time.monotonic() - start) * 1000
        metrics.record_request(latency_ms, error=error)


# ── Routes ───────────────────────────────────────────────────────────────────

@app.get("/health", response_model=HealthResponse)
def health():
    return HealthResponse(status="UP")


@app.get("/metrics-lite", response_model=MetricsLiteResponse)
def metrics_lite():
    snap = metrics.snapshot()
    return MetricsLiteResponse(
        service="eta-service",
        status="UP",
        **snap,
    )


@app.post("/predict-eta", response_model=EtaResponse)
def predict(body: EtaRequest):
    extra = {"service": "eta-service"}
    if body.order_id:
        extra["orderId"] = body.order_id

    log.info("predict-eta request", extra={
        **extra,
        "distance_km":          body.distance_km,
        "prep_time_minutes":    body.prep_time_minutes,
        "driver_availability":  body.driver_availability,
        "traffic_factor":       body.traffic_factor,
    })

    result = predict_eta(
        distance_km=body.distance_km,
        prep_time_minutes=body.prep_time_minutes,
        driver_availability=body.driver_availability,
        traffic_factor=body.traffic_factor,
    )

    log.info("predict-eta response", extra={**extra, "estimated_delivery_minutes": result})
    return EtaResponse(estimated_delivery_minutes=result)
