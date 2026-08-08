import pytest
from app.calculator import predict_eta, MIN_ETA


def test_baseline_no_traffic_full_availability():
    # 6 km at 30 km/h = 12 min drive + 10 min prep = 22 min
    result = predict_eta(
        distance_km=6.0,
        prep_time_minutes=10,
        driver_availability=1.0,
        traffic_factor=0.0,
    )
    assert result == 22


def test_traffic_inflates_eta():
    low  = predict_eta(6.0, 10, 1.0, traffic_factor=0.0)
    high = predict_eta(6.0, 10, 1.0, traffic_factor=1.0)
    assert high > low


def test_low_availability_inflates_eta():
    full   = predict_eta(6.0, 10, driver_availability=1.0, traffic_factor=0.0)
    scarce = predict_eta(6.0, 10, driver_availability=0.0, traffic_factor=0.0)
    assert scarce > full


def test_floor_applied_for_tiny_distance():
    result = predict_eta(
        distance_km=0.01,
        prep_time_minutes=1,
        driver_availability=1.0,
        traffic_factor=0.0,
    )
    assert result >= MIN_ETA


def test_returns_integer():
    result = predict_eta(5.0, 15, 0.8, 0.3)
    assert isinstance(result, int)


def test_worst_case_is_sane():
    # 50 km, gridlock, no drivers — should still be a reasonable number
    result = predict_eta(50.0, 30, 0.0, 1.0)
    assert 60 <= result <= 300
