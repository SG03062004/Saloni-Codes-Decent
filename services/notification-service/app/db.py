from __future__ import annotations
import json
import uuid
from sqlalchemy.ext.asyncio import create_async_engine, AsyncSession, async_sessionmaker
from sqlalchemy import text

from app.models import Settings

_session_factory = None


def init_db(settings: Settings) -> None:
    global _session_factory
    engine = create_async_engine(settings.db_url, pool_pre_ping=True)
    _session_factory = async_sessionmaker(engine, expire_on_commit=False)


def get_session_factory():
    return _session_factory


async def record_notification(
    session: AsyncSession,
    order_id: str,
    event_type: str,
    channel: str,
    recipient: str,
    payload: dict,
) -> None:
    await session.execute(
        text("""
            INSERT INTO notifications (id, order_id, event_type, channel, recipient, payload_json)
            VALUES (:id, :order_id, :event_type, :channel, :recipient, :payload_json)
        """),
        {
            "id": str(uuid.uuid4()),
            "order_id": order_id,
            "event_type": event_type,
            "channel": channel,
            "recipient": recipient,
            "payload_json": json.dumps(payload),
        },
    )
    await session.commit()
