from __future__ import annotations

from pydantic import BaseModel, Field
from pydantic_settings import BaseSettings

# ── Settings ─────────────────────────────────────────────────────────────────


class Settings(BaseSettings):
    db_url: str = "mysql+aiomysql://root:root@localhost:3306/assignment_db"
    kafka_bootstrap_servers: str = "localhost:9092"
    eta_service_url: str = "http://localhost:8084"
    kafka_group_id: str = "assignment-service"
    max_retry_attempts: int = 3
    retry_backoff_seconds: float = 2.0

    class Config:
        env_file = ".env"


# ── HTTP request/response ─────────────────────────────────────────────────────


class LocationUpdate(BaseModel):
    lat: float = Field(..., ge=-90, le=90)
    lng: float = Field(..., ge=-180, le=180)
    is_available: bool = True


class HealthResponse(BaseModel):
    status: str


# ── Kafka event shapes ────────────────────────────────────────────────────────


class GeoPoint(BaseModel):
    lat: float
    lng: float


class DeliveryAddress(BaseModel):
    lat: float
    lng: float
    street: str
    city: str


class OrderCreatedPayload(BaseModel):
    orderId: str
    customerId: str
    restaurantId: str
    deliveryAddress: DeliveryAddress
    prepTimeMinutes: int = 15  # default if not in event


class OrderCreatedEvent(BaseModel):
    eventId: str
    eventType: str
    timestamp: float
    version: str
    payload: OrderCreatedPayload


class DriverAssignedPayload(BaseModel):
    orderId: str
    driverId: str
    etaMinutes: int
    driverLocation: GeoPoint
    restaurantLocation: GeoPoint
    deliveryLocation: GeoPoint


class DriverAssignedEvent(BaseModel):
    eventId: str
    eventType: str = "driver-assigned"
    timestamp: str
    version: str = "1"
    payload: DriverAssignedPayload


# ── Internal ──────────────────────────────────────────────────────────────────


class DriverCandidate(BaseModel):
    driver_id: str
    lat: float
    lng: float
    distance_m: float
