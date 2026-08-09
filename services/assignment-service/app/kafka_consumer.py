from __future__ import annotations

import asyncio
import json
import uuid
from datetime import datetime, timezone

import httpx
from aiokafka import AIOKafkaConsumer

from app.assignment import pick_best_driver
from app.db import find_nearest_drivers, get_session_factory
from app.kafka_producer import publish
from app.logging_config import get_logger
from app.models import OrderCreatedEvent, Settings

log = get_logger("assignment-service.consumer")

TOPIC_ORDER_CREATED = "order-created"
TOPIC_DRIVER_ASSIGNED = "driver-assigned"
TOPIC_DLQ = "driver-assignment-dlq"


async def _handle_order(
    event: OrderCreatedEvent, settings: Settings, http_client: httpx.AsyncClient
) -> None:
    p = event.payload
    log_ctx = {"orderId": p.orderId, "service": "assignment-service"}

    session_factory = get_session_factory()
    async with session_factory() as session:
        candidates = await find_nearest_drivers(
            session, p.deliveryAddress.lat, p.deliveryAddress.lng
        )

    if not candidates:
        raise RuntimeError("no_drivers_available")

    result = await pick_best_driver(
        candidates,
        prep_time_minutes=getattr(p, "prepTimeMinutes", 15),
        http_client=http_client,
        settings=settings,
        order_id=p.orderId,
    )
    if result is None:
        raise RuntimeError("no_drivers_scored")

    best, eta = result
    log.info(
        "driver_selected",
        extra={**log_ctx, "driverId": best.driver_id, "etaMinutes": eta},
    )

    await publish(
        TOPIC_DRIVER_ASSIGNED,
        {
            "eventId": str(uuid.uuid4()),
            "eventType": "driver-assigned",
            "timestamp": datetime.now(timezone.utc).isoformat(),
            "version": "1",
            "payload": {
                "orderId": p.orderId,
                "driverId": best.driver_id,
                "etaMinutes": eta,
                "driverLocation": {"lat": best.lat, "lng": best.lng},
                "restaurantLocation": {
                    "lat": p.deliveryAddress.lat,
                    "lng": p.deliveryAddress.lng,
                },
                "deliveryLocation": {
                    "lat": p.deliveryAddress.lat,
                    "lng": p.deliveryAddress.lng,
                },
            },
        },
        key=p.orderId,
    )


async def _handle_with_retry(
    raw: bytes,
    settings: Settings,
    http_client: httpx.AsyncClient,
) -> None:
    try:
        data = json.loads(raw)
        event = OrderCreatedEvent(**data)
    except Exception as exc:
        log.error("event_parse_error", extra={"error": str(exc)})
        return

    order_id = event.payload.orderId
    log_ctx = {"orderId": order_id, "service": "assignment-service"}

    for attempt in range(1, settings.max_retry_attempts + 1):
        try:
            await _handle_order(event, settings, http_client)
            return
        except Exception as exc:
            log.warning(
                "assignment_attempt_failed",
                extra={**log_ctx, "attempt": attempt, "error": str(exc)},
            )
            if attempt < settings.max_retry_attempts:
                await asyncio.sleep(settings.retry_backoff_seconds * attempt)

    # Exhausted retries → dead-letter
    log.error("assignment_exhausted_retries_dlq", extra=log_ctx)
    await publish(TOPIC_DLQ, json.loads(raw), key=order_id)


async def consume_loop(settings: Settings) -> None:
    consumer = AIOKafkaConsumer(
        TOPIC_ORDER_CREATED,
        bootstrap_servers=settings.kafka_bootstrap_servers,
        group_id=settings.kafka_group_id,
        auto_offset_reset="earliest",
        value_deserializer=lambda b: b,  # raw bytes — we parse manually
    )
    await consumer.start()
    log.info("consumer_started", extra={"topic": TOPIC_ORDER_CREATED})

    async with httpx.AsyncClient() as http_client:
        try:
            async for msg in consumer:
                await _handle_with_retry(msg.value, settings, http_client)
        finally:
            await consumer.stop()
