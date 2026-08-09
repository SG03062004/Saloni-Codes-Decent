from __future__ import annotations

import json

from aiokafka import AIOKafkaConsumer

from app.channels import NotificationChannel
from app.db import get_session_factory
from app.handlers import handle_order_created, handle_order_delivered
from app.logging_config import get_logger
from app.models import KafkaEvent, Settings

log = get_logger("notification-service.consumer")

TOPICS = ["order-created", "order-delivered"]

_HANDLERS = {
    "order-created": handle_order_created,
    "order-delivered": handle_order_delivered,
}


async def consume_loop(settings: Settings, channel: NotificationChannel) -> None:
    consumer = AIOKafkaConsumer(
        *TOPICS,
        bootstrap_servers=settings.kafka_bootstrap_servers,
        group_id=settings.kafka_group_id,
        auto_offset_reset="earliest",
        value_deserializer=lambda b: b,
    )
    await consumer.start()
    log.info("consumer_started", extra={"topics": TOPICS})

    try:
        async for msg in consumer:
            await _dispatch(msg.value, channel)
    finally:
        await consumer.stop()


async def _dispatch(raw: bytes, channel: NotificationChannel) -> None:
    try:
        data = json.loads(raw)
        event = KafkaEvent(**data)
    except Exception as exc:
        log.error("event_parse_error", extra={"error": str(exc)})
        return

    handler = _HANDLERS.get(event.eventType)
    if handler is None:
        log.warning("unknown_event_type", extra={"eventType": event.eventType})
        return

    session_factory = get_session_factory()
    try:
        async with session_factory() as session:
            await handler(event.payload, session, channel)
    except Exception as exc:
        log.error(
            "handler_error",
            extra={
                "eventType": event.eventType,
                "orderId": event.payload.get("orderId"),
                "error": str(exc),
            },
        )
