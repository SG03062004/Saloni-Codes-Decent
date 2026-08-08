from __future__ import annotations
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
    """Return estimatedDeliveryMinutes from the ETA service."""
    payload = {
        "distanceKm": round(candidate.distance_m / 1000, 3),
        "prepTimeMinutes": prep_time_minutes,
        "driverAvailability": 1.0,
        "trafficFactor": traffic_factor,
    }
    if order_id:
        payload["orderId"] = order_id

    r = await client.post(
        f"{settings.eta_service_url}/predict-eta",
        json=payload,
        timeout=5.0,
    )
    r.raise_for_status()
    return r.json()["estimatedDeliveryMinutes"]
