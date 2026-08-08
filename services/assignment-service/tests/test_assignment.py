import pytest
import httpx
from unittest.mock import AsyncMock, patch

from app.assignment import pick_best_driver, DISTANCE_WEIGHT, ETA_WEIGHT
from app.models import DriverCandidate, Settings

SETTINGS = Settings(
    db_url="mysql+aiomysql://x:x@localhost/x",
    kafka_bootstrap_servers="localhost:9092",
    eta_service_url="http://eta-service:8084",
)

CANDIDATES = [
    DriverCandidate(driver_id="d1", lat=51.51, lng=-0.09, distance_m=500),
    DriverCandidate(driver_id="d2", lat=51.52, lng=-0.10, distance_m=1500),
    DriverCandidate(driver_id="d3", lat=51.53, lng=-0.11, distance_m=3000),
]


@pytest.mark.asyncio
async def test_picks_best_by_score():
    # d1 is closest and gets lowest ETA → should win
    eta_map = {"d1": 20, "d2": 25, "d3": 35}

    async def fake_get_eta(client, settings, candidate, prep_time, **_):
        return eta_map[candidate.driver_id]

    with patch("app.assignment.get_eta", side_effect=fake_get_eta):
        async with httpx.AsyncClient() as client:
            result = await pick_best_driver(CANDIDATES, 10, client, SETTINGS)

    assert result is not None
    best, eta = result
    assert best.driver_id == "d1"
    assert eta == 20


@pytest.mark.asyncio
async def test_returns_none_for_empty_candidates():
    async with httpx.AsyncClient() as client:
        result = await pick_best_driver([], 10, client, SETTINGS)
    assert result is None


@pytest.mark.asyncio
async def test_skips_candidate_when_eta_raises():
    async def fake_get_eta(client, settings, candidate, prep_time, **_):
        if candidate.driver_id == "d1":
            raise httpx.ConnectError("timeout")
        return 30

    with patch("app.assignment.get_eta", side_effect=fake_get_eta):
        async with httpx.AsyncClient() as client:
            result = await pick_best_driver(CANDIDATES, 10, client, SETTINGS)

    assert result is not None
    best, _ = result
    assert best.driver_id != "d1"


@pytest.mark.asyncio
async def test_returns_none_when_all_eta_calls_fail():
    async def fake_get_eta(*_, **__):
        raise httpx.ConnectError("down")

    with patch("app.assignment.get_eta", side_effect=fake_get_eta):
        async with httpx.AsyncClient() as client:
            result = await pick_best_driver(CANDIDATES, 10, client, SETTINGS)

    assert result is None


@pytest.mark.asyncio
async def test_farther_driver_wins_if_much_lower_eta():
    # d2 is farther but has a dramatically lower ETA — score weighting should prefer it
    eta_map = {"d1": 60, "d2": 10, "d3": 55}

    async def fake_get_eta(client, settings, candidate, prep_time, **_):
        return eta_map[candidate.driver_id]

    with patch("app.assignment.get_eta", side_effect=fake_get_eta):
        async with httpx.AsyncClient() as client:
            result = await pick_best_driver(CANDIDATES, 10, client, SETTINGS)

    assert result is not None
    best, _ = result
    assert best.driver_id == "d2"
