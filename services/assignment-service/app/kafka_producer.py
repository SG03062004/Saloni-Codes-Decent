from __future__ import annotations

import json

from aiokafka import AIOKafkaProducer

_producer: AIOKafkaProducer | None = None


async def start_producer(bootstrap_servers: str) -> None:
    global _producer
    _producer = AIOKafkaProducer(
        bootstrap_servers=bootstrap_servers,
        value_serializer=lambda v: json.dumps(v).encode(),
    )
    await _producer.start()


async def stop_producer() -> None:
    if _producer:
        await _producer.stop()


async def publish(topic: str, payload: dict, key: str | None = None) -> None:
    key_bytes = key.encode() if key else None
    await _producer.send_and_wait(topic, value=payload, key=key_bytes)
