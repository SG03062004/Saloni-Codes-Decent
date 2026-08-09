"""
conftest.py — shared fixtures for the food-delivery E2E suite.

Requires all services running (docker compose up or blue-green stack).
Override base URLs via environment variables:
  ORDER_URL        default http://localhost:8082
  RESTAURANT_URL   default http://localhost:8081
  DELIVERY_URL     default http://localhost:8083
  ASSIGNMENT_URL   default http://localhost:8085
"""

from __future__ import annotations

import asyncio
import os
import time
import uuid

import httpx
import pytest
import pytest_asyncio

# ── Base URLs ─────────────────────────────────────────────────────────────────

ORDER_URL      = os.getenv("ORDER_URL",      "http://localhost:8082")
RESTAURANT_URL = os.getenv("RESTAURANT_URL", "http://localhost:8081")
DELIVERY_URL   = os.getenv("DELIVERY_URL",   "http://localhost:8083")
ASSIGNMENT_URL = os.getenv("ASSIGNMENT_URL", "http://localhost:8085")


# ── Async HTTP client ─────────────────────────────────────────────────────────

@pytest_asyncio.fixture
async def client():
    async with httpx.AsyncClient(timeout=10.0) as c:
        yield c


# ── Seed fixtures ─────────────────────────────────────────────────────────────

@pytest_asyncio.fixture
async def restaurant(client: httpx.AsyncClient) -> dict:
    """Create a restaurant and return its response body."""
    r = await client.post(
        f"{RESTAURANT_URL}/restaurants",
        json={"name": f"E2E Bistro {uuid.uuid4().hex[:6]}", "avgPrepTimeMinutes": 10},
    )
    assert r.status_code == 201, f"Failed to create restaurant: {r.text}"
    return r.json()


@pytest_asyncio.fixture
async def driver(client: httpx.AsyncClient) -> str:
    """Register a driver near the delivery address and return driver_id."""
    driver_id = f"driver-{uuid.uuid4().hex[:8]}"
    r = await client.patch(
        f"{ASSIGNMENT_URL}/drivers/{driver_id}/location",
        json={"lat": 37.771, "lng": -122.411, "is_available": True},
    )
    assert r.status_code == 204, f"Failed to register driver: {r.text}"
    return driver_id


# ── Polling helper ────────────────────────────────────────────────────────────

async def poll_order_status(
    client: httpx.AsyncClient,
    order_id: str,
    expected_status: str,
    timeout: float = 60.0,
    interval: float = 2.0,
) -> dict:
    """
    Poll GET /orders/{id} until status == expected_status or timeout.
    Returns the full order response body.
    """
    deadline = time.monotonic() + timeout
    while time.monotonic() < deadline:
        r = await client.get(f"{ORDER_URL}/orders/{order_id}")
        assert r.status_code == 200, f"GET /orders/{order_id} returned {r.status_code}"
        body = r.json()
        if body.get("status") == expected_status:
            return body
        await asyncio.sleep(interval)
    raise TimeoutError(
        f"Order {order_id} did not reach status={expected_status} within {timeout}s"
    )
