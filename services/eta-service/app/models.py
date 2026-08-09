from pydantic import BaseModel, Field


class EtaRequest(BaseModel):
    distance_km: float = Field(
        ..., gt=0, le=200, description="Straight-line delivery distance in km"
    )
    prep_time_minutes: int = Field(
        ..., ge=1, le=120, description="Restaurant kitchen prep time"
    )
    driver_availability: float = Field(
        ..., ge=0.0, le=1.0, description="Fraction of drivers available (0=none, 1=all)"
    )
    traffic_factor: float = Field(
        ..., ge=0.0, le=1.0, description="Traffic multiplier (0=clear, 1=gridlock)"
    )
    order_id: str | None = Field(
        default=None, description="Optional — used as log correlation key"
    )


class EtaResponse(BaseModel):
    estimated_delivery_minutes: int


class HealthResponse(BaseModel):
    status: str  # "UP" | "DOWN"


class MetricsLiteResponse(BaseModel):
    service: str
    status: str
    uptime_seconds: float
    requests_total: int
    errors_total: int
    avg_latency_ms: float
