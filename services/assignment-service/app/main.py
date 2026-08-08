from __future__ import annotations
import asyncio
from contextlib import asynccontextmanager

from fastapi import FastAPI, HTTPException
from fastapi.responses import JSONResponse

from app.db import init_db, get_session_factory, upsert_driver_location
from app.kafka_consumer import consume_loop
from app.kafka_producer import start_producer, stop_producer
from app.logging_config import get_logger
from app.models import HealthResponse, LocationUpdate, Settings

settings = Settings()
log = get_logger("assignment-service")


@asynccontextmanager
async def lifespan(app: FastAPI):
    init_db(settings)
    await start_producer(settings.kafka_bootstrap_servers)
    consumer_task = asyncio.create_task(consume_loop(settings))
    log.info("assignment_service_started")
    yield
    consumer_task.cancel()
    await stop_producer()
    log.info("assignment_service_stopped")


app = FastAPI(title="assignment-service", version="0.1.0", lifespan=lifespan)


@app.get("/health", response_model=HealthResponse)
def health():
    return HealthResponse(status="UP")


@app.patch("/drivers/{driver_id}/location", status_code=204)
async def update_driver_location(driver_id: str, body: LocationUpdate):
    session_factory = get_session_factory()
    if session_factory is None:
        raise HTTPException(status_code=503, detail="db_not_ready")
    async with session_factory() as session:
        await upsert_driver_location(session, driver_id, body.lat, body.lng, body.is_available)
