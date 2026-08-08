"""
Integration tests — require Docker.
Run with: pytest tests/test_integration.py -v -s
"""
import asyncio
import json
import time
import uuid
from datetime import datetime, timezone

import pytest
import sqlalchemy
from aiokafka import AIOKafkaConsumer, AIOKafkaProducer
from sqlalchemy import text
from testcontainers.kafka import KafkaContainer
from testcontainers.mysql import MySqlContainer

from app.assignment import pick_best_driver
from app.db import find_nearest_drivers, upsert_driver_location
from app.models import DriverCandidate, Settings

# ── Helpers ───────────────────────────────────────────────────────────────────

def _make_order_event(order_id: str, lat: float, lng: float) -> dict:
    return {
        "eventId": str(uuid.uuid4()),
        "eventType": "order-created",
        "timestamp": datetime.now(timezone.utc).isoformat(),
        "version": "1",
        "payload": {
            "orderId": order_id,
            "customerId": str(uuid.uuid4()),
            "restaurantId": str(uuid.uuid4()),
            "items": [{"menuItemId": str(uuid.uuid4()), "quantity": 1, "unitPriceCents": 1000}],
            "deliveryAddress": {"lat": lat, "lng": lng, "street": "1 Test St", "city": "London"},
            "totalCents": 1000,
            "timestamp": datetime.now(timezone.utc).isoformat(),
        },
    }


# ── MySQL spatial integration ─────────────────────────────────────────────────

@pytest.fixture(scope="module")
def mysql_container():
    with MySqlContainer("mysql:8.0", dbname="assignment_db") as mysql:
        # Create the spatial table
        engine = sqlalchemy.create_engine(mysql.get_connection_url())
        with engine.connect() as conn:
            conn.execute(text("""
                CREATE TABLE IF NOT EXISTS driver_locations (
                    driver_id    CHAR(36)   NOT NULL PRIMARY KEY,
                    location     POINT      NOT NULL SRID 4326,
                    is_available TINYINT(1) NOT NULL DEFAULT 1,
                    updated_at   DATETIME   NOT NULL DEFAULT CURRENT_TIMESTAMP
                                            ON UPDATE CURRENT_TIMESTAMP,
                    SPATIAL INDEX idx_location (location)
                )
            """))
            conn.commit()
        yield mysql


@pytest.mark.asyncio
async def test_upsert_and_nearest_driver(mysql_container):
    from sqlalchemy.ext.asyncio import create_async_engine, async_sessionmaker

    url = mysql_container.get_connection_url().replace("mysql+pymysql", "mysql+aiomysql")
    engine = create_async_engine(url)
    factory = async_sessionmaker(engine, expire_on_commit=False)

    driver_id = str(uuid.uuid4())
    async with factory() as session:
        await upsert_driver_location(session, driver_id, lat=51.5074, lng=-0.1278, is_available=True)

    async with factory() as session:
        results = await find_nearest_drivers(session, order_lat=51.5080, order_lng=-0.1270)

    assert any(r.driver_id == driver_id for r in results)
    await engine.dispose()


@pytest.mark.asyncio
async def test_unavailable_driver_excluded(mysql_container):
    from sqlalchemy.ext.asyncio import create_async_engine, async_sessionmaker

    url = mysql_container.get_connection_url().replace("mysql+pymysql", "mysql+aiomysql")
    engine = create_async_engine(url)
    factory = async_sessionmaker(engine, expire_on_commit=False)

    driver_id = str(uuid.uuid4())
    async with factory() as session:
        await upsert_driver_location(session, driver_id, lat=51.5074, lng=-0.1278, is_available=False)

    async with factory() as session:
        results = await find_nearest_drivers(session, order_lat=51.5080, order_lng=-0.1270)

    assert all(r.driver_id != driver_id for r in results)
    await engine.dispose()


# ── Kafka round-trip integration ──────────────────────────────────────────────

@pytest.fixture(scope="module")
def kafka_container():
    with KafkaContainer("apache/kafka:3.7.0") as kafka:
        yield kafka


@pytest.mark.asyncio
async def test_kafka_publish_and_consume(kafka_container):
    bootstrap = kafka_container.get_bootstrap_server()
    order_id = str(uuid.uuid4())
    event = _make_order_event(order_id, lat=51.5074, lng=-0.1278)

    producer = AIOKafkaProducer(
        bootstrap_servers=bootstrap,
        value_serializer=lambda v: json.dumps(v).encode(),
    )
    await producer.start()
    await producer.send_and_wait("order-created", value=event, key=order_id.encode())
    await producer.stop()

    consumer = AIOKafkaConsumer(
        "order-created",
        bootstrap_servers=bootstrap,
        group_id=f"test-{uuid.uuid4()}",
        auto_offset_reset="earliest",
        consumer_timeout_ms=5000,
    )
    await consumer.start()
    received = []
    async for msg in consumer:
        received.append(json.loads(msg.value))
        break
    await consumer.stop()

    assert len(received) == 1
    assert received[0]["payload"]["orderId"] == order_id
