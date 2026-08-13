from __future__ import annotations

from typing import Any

import httpx

from app.models import DriverCandidate, Settings


async def get_eta(
    client: httpx.AsyncClient,
    settings: Settings,
    candidate: DriverCandidate,
    prep_time_minutes: int,
    traffic_factor: float = 0.3,
    order_id: str | None = None,
) -> int:
    """Return estimated_delivery_minutes from the ETA service."""

    payload: dict[str, Any] = {
        "distance_km": round(candidate.distance_m / 1000, 3),
        "prep_time_minutes": prep_time_minutes,
        "driver_availability": 1.0,
        "traffic_factor": traffic_factor,
    }

    if order_id:
        payload["order_id"] = order_id

    print("ETA PAYLOAD:", payload, flush=True)
    
    response = await client.post(
        f"{settings.eta_service_url}/predict-eta",
        json=payload,
        timeout=5.0,
    )

    response.raise_for_status()

    return int(response.json()["estimated_delivery_minutes"])