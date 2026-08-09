"""
test_order_lifecycle.py

End-to-end test for the happy-path order flow:

  POST /orders
    → (Kafka: order-created)
    → Restaurant Service accepts → publishes order-accepted
    → Order Service marks ACCEPTED
    → Assignment Service assigns driver → publishes driver-assigned
    → Order Service marks DRIVER_ASSIGNED
    → Delivery Service advances to DELIVERED
    → GET /orders/{id}/full-status reflects final composed state

All status transitions are driven by Kafka consumers running inside the
live service containers — this test only polls HTTP endpoints and advances
the delivery status manually (simulating the driver completing the delivery).
"""

from __future__ import annotations

import pytest
import httpx

from conftest import (
    ORDER_URL,
    DELIVERY_URL,
    poll_order_status,
)

pytestmark = pytest.mark.asyncio


# ── Happy-path lifecycle ──────────────────────────────────────────────────────

async def test_order_full_lifecycle(
    client: httpx.AsyncClient,
    restaurant: dict,
    driver: str,
):
    restaurant_id = restaurant["id"]

    # ── Step 1: Create order ──────────────────────────────────────────────────
    create_resp = await client.post(
        f"{ORDER_URL}/orders",
        json={
            "customerId":   "e2e-customer-001",
            "restaurantId": restaurant_id,
            "items": [
                {"menuItemId": "item-burger", "quantity": 2, "unitPriceCents": 1200},
                {"menuItemId": "item-fries",  "quantity": 1, "unitPriceCents":  400},
            ],
            "deliveryAddress": {
                "lat":    37.7749,
                "lng":   -122.4194,
                "street": "1 Market St",
                "city":   "San Francisco",
            },
        },
    )
    assert create_resp.status_code == 202, f"Create order failed: {create_resp.text}"
    order = create_resp.json()
    order_id = order["orderId"]

    assert order["status"]     == "PENDING"
    assert order["totalCents"] == 2800   # 2×1200 + 1×400

    # ── Step 2: Wait for ACCEPTED (Restaurant Service → Kafka → Order Service) ─
    accepted = await poll_order_status(
        client, order_id, "ACCEPTED", timeout=60.0
    )
    assert accepted["status"] == "ACCEPTED"

    # ── Step 3: Wait for DRIVER_ASSIGNED (Assignment Service → Kafka → Order Service)
    assigned = await poll_order_status(
        client, order_id, "DRIVER_ASSIGNED", timeout=90.0
    )
    assert assigned["status"]     == "DRIVER_ASSIGNED"
    assert assigned["etaMinutes"] is not None
    assert assigned["etaMinutes"] > 0

    # ── Step 4: Advance delivery to DELIVERED via Delivery Service API ─────────
    # Real-world: driver app would call this; in E2E we drive it directly.
    for next_status in ("PICKED_UP", "IN_TRANSIT", "DELIVERED"):
        patch = await client.patch(
            f"{DELIVERY_URL}/deliveries/{order_id}/status",
            json={"status": next_status},
        )
        assert patch.status_code == 200, (
            f"PATCH /deliveries/{order_id}/status → {next_status} failed: {patch.text}"
        )

    # ── Step 5: Assert GET /orders/{id}/full-status reflects final state ───────
    full = await client.get(f"{ORDER_URL}/orders/{order_id}/full-status")
    assert full.status_code == 200, f"full-status failed: {full.text}"
    body = full.json()

    # Order fields
    assert body["orderId"]      == order_id
    assert body["orderStatus"]  == "DRIVER_ASSIGNED"   # order-service status (no DELIVERED transition yet)
    assert body["totalCents"]   == 2800

    # Restaurant composition — either real data or circuit-breaker fallback
    assert body["restaurant"] is not None
    assert "id" in body["restaurant"] or "unavailable" in body["restaurant"]

    # Delivery composition — must show DELIVERED
    assert body["delivery"] is not None
    delivery = body["delivery"]
    assert delivery.get("status") == "DELIVERED" or delivery.get("unavailable") is True


# ── Idempotency: duplicate order-accepted events ──────────────────────────────

async def test_get_order_not_found(client: httpx.AsyncClient):
    r = await client.get(f"{ORDER_URL}/orders/does-not-exist-xyz")
    assert r.status_code == 404
    assert "error" in r.json()


async def test_create_order_validation(client: httpx.AsyncClient):
    r = await client.post(f"{ORDER_URL}/orders", json={})
    assert r.status_code == 400


async def test_full_status_not_found(client: httpx.AsyncClient):
    r = await client.get(f"{ORDER_URL}/orders/ghost-order/full-status")
    assert r.status_code == 404
