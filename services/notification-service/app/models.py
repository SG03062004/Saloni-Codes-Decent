from __future__ import annotations
from pydantic import BaseModel
from pydantic_settings import BaseSettings


class Settings(BaseSettings):
    db_url: str = "mysql+aiomysql://root:root@localhost:3306/notification_db"
    kafka_bootstrap_servers: str = "localhost:9092"
    kafka_group_id: str = "notification-service"

    class Config:
        env_file = ".env"


# ── Kafka event shapes ────────────────────────────────────────────────────────

class OrderCreatedPayload(BaseModel):
    orderId: str
    customerId: str
    restaurantId: str
    totalCents: int


class OrderDeliveredPayload(BaseModel):
    orderId: str
    driverId: str
    deliveredAt: str


class KafkaEvent(BaseModel):
    eventId: str
    eventType: str
    timestamp: str
    version: str
    payload: dict  # parsed loosely; handlers cast to specific payload types
