from __future__ import annotations

from sqlalchemy.ext.asyncio import AsyncSession

from app.channels import NotificationChannel
from app.db import record_notification
from app.logging_config import get_logger
from app.models import OrderCreatedPayload, OrderDeliveredPayload

log = get_logger("notification-service.handlers")

# In a real system, recipient would be looked up from a customer/profile service.
# Here we derive a deterministic placeholder so the mock channel has something to log.
_MOCK_RECIPIENT = "customer@example.com"


async def handle_order_created(
    payload: dict,
    session: AsyncSession,
    channel: NotificationChannel,
) -> None:
    p = OrderCreatedPayload(**payload)
    body = {
        "orderId": p.orderId,
        "totalCents": p.totalCents,
        "message": "Your order has been placed and is being prepared.",
    }
    await channel.send(
        recipient=_MOCK_RECIPIENT,
        subject="Order Confirmed",
        body=body,
    )
    await record_notification(
        session,
        order_id=p.orderId,
        event_type="order-created",
        channel=channel.name,
        recipient=_MOCK_RECIPIENT,
        payload=body,
    )
    log.info("order_created_notification_sent", extra={"orderId": p.orderId})


async def handle_order_delivered(
    payload: dict,
    session: AsyncSession,
    channel: NotificationChannel,
) -> None:
    p = OrderDeliveredPayload(**payload)
    body = {
        "orderId": p.orderId,
        "deliveredAt": p.deliveredAt,
        "message": "Your order has been delivered. Enjoy!",
    }
    await channel.send(
        recipient=_MOCK_RECIPIENT,
        subject="Order Delivered",
        body=body,
    )
    await record_notification(
        session,
        order_id=p.orderId,
        event_type="order-delivered",
        channel=channel.name,
        recipient=_MOCK_RECIPIENT,
        payload=body,
    )
    log.info("order_delivered_notification_sent", extra={"orderId": p.orderId})
