import pytest
from unittest.mock import AsyncMock, MagicMock, patch

from app.handlers import handle_order_created, handle_order_delivered


def _mock_session():
    session = AsyncMock()
    cm = AsyncMock()
    cm.__aenter__ = AsyncMock(return_value=session)
    cm.__aexit__ = AsyncMock(return_value=False)
    return session


def _mock_channel(name="mock"):
    ch = AsyncMock()
    ch.name = name
    return ch


ORDER_CREATED_PAYLOAD = {
    "orderId": "order-001",
    "customerId": "cust-001",
    "restaurantId": "rest-001",
    "totalCents": 2500,
}

ORDER_DELIVERED_PAYLOAD = {
    "orderId": "order-001",
    "driverId": "driver-001",
    "deliveredAt": "2024-01-01T12:00:00Z",
}


# ── handle_order_created ──────────────────────────────────────────────────────

@pytest.mark.asyncio
async def test_order_created_calls_channel():
    session = _mock_session()
    channel = _mock_channel()

    with patch("app.handlers.record_notification", new_callable=AsyncMock):
        await handle_order_created(ORDER_CREATED_PAYLOAD, session, channel)

    channel.send.assert_awaited_once()
    _, kwargs = channel.send.call_args
    assert kwargs["subject"] == "Order Confirmed"
    assert kwargs["body"]["orderId"] == "order-001"


@pytest.mark.asyncio
async def test_order_created_records_notification():
    session = _mock_session()
    channel = _mock_channel()

    with patch("app.handlers.record_notification", new_callable=AsyncMock) as mock_record:
        await handle_order_created(ORDER_CREATED_PAYLOAD, session, channel)

    mock_record.assert_awaited_once()
    call_kwargs = mock_record.call_args.kwargs
    assert call_kwargs["order_id"] == "order-001"
    assert call_kwargs["event_type"] == "order-created"
    assert call_kwargs["channel"] == "mock"


@pytest.mark.asyncio
async def test_order_created_invalid_payload_raises():
    session = _mock_session()
    channel = _mock_channel()

    with pytest.raises(Exception):
        await handle_order_created({"bad": "data"}, session, channel)


# ── handle_order_delivered ────────────────────────────────────────────────────

@pytest.mark.asyncio
async def test_order_delivered_calls_channel():
    session = _mock_session()
    channel = _mock_channel()

    with patch("app.handlers.record_notification", new_callable=AsyncMock):
        await handle_order_delivered(ORDER_DELIVERED_PAYLOAD, session, channel)

    channel.send.assert_awaited_once()
    _, kwargs = channel.send.call_args
    assert kwargs["subject"] == "Order Delivered"
    assert kwargs["body"]["orderId"] == "order-001"
    assert kwargs["body"]["deliveredAt"] == "2024-01-01T12:00:00Z"


@pytest.mark.asyncio
async def test_order_delivered_records_notification():
    session = _mock_session()
    channel = _mock_channel()

    with patch("app.handlers.record_notification", new_callable=AsyncMock) as mock_record:
        await handle_order_delivered(ORDER_DELIVERED_PAYLOAD, session, channel)

    mock_record.assert_awaited_once()
    call_kwargs = mock_record.call_args.kwargs
    assert call_kwargs["order_id"] == "order-001"
    assert call_kwargs["event_type"] == "order-delivered"


@pytest.mark.asyncio
async def test_order_delivered_invalid_payload_raises():
    session = _mock_session()
    channel = _mock_channel()

    with pytest.raises(Exception):
        await handle_order_delivered({"bad": "data"}, session, channel)
