from __future__ import annotations
import asyncio
from contextlib import asynccontextmanager

from fastapi import FastAPI

from app.channels import MockChannel
from app.db import init_db
from app.kafka_consumer import consume_loop
from app.logging_config import get_logger
from app.models import Settings

settings = Settings()
log = get_logger("notification-service")
channel = MockChannel()


@asynccontextmanager
async def lifespan(app: FastAPI):
    init_db(settings)
    task = asyncio.create_task(consume_loop(settings, channel))
    log.info("notification_service_started")
    yield
    task.cancel()
    log.info("notification_service_stopped")


app = FastAPI(title="notification-service", version="0.1.0", lifespan=lifespan)


@app.get("/health")
def health():
    return {"status": "UP"}
