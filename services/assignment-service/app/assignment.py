from __future__ import annotations

import httpx

from app.eta_client import get_eta
from app.models import DriverCandidate, Settings

# Weight for combining distance and ETA into a single score (tunable).
# Lower score = better candidate.
DISTANCE_WEIGHT = 0.4
ETA_WEIGHT = 0.6


async def pick_best_driver(
    candidates: list[DriverCandidate],
    prep_time_minutes: int,
    http_client: httpx.AsyncClient,
    settings: Settings,
    order_id: str | None = None,
) -> tuple[DriverCandidate, int] | None:
    """
    Score each candidate by a weighted sum of normalised distance and ETA.
    Returns (best_candidate, eta_minutes) or None if candidates is empty.
    """
    if not candidates:
        return None

    scored: list[tuple[float, DriverCandidate, int]] = []
    max_dist = max(c.distance_m for c in candidates) or 1.0

    for candidate in candidates:
        try:
            eta = await get_eta(
                http_client, settings, candidate, prep_time_minutes, order_id=order_id
            )
        except Exception:
            continue  # skip unreachable ETA service for this candidate

        norm_dist = candidate.distance_m / max_dist
        score = DISTANCE_WEIGHT * norm_dist + ETA_WEIGHT * eta
        scored.append((score, candidate, eta))

    if not scored:
        return None

    scored.sort(key=lambda t: t[0])
    _, best, best_eta = scored[0]
    return best, best_eta
