"""
Deterministic ETA formula — swap this module for an ML model later
without changing the API contract.

Formula (all weights tunable):
  drive_minutes  = (distance_km / AVG_SPEED_KMH) * 60
                   * (1 + TRAFFIC_WEIGHT * traffic_factor)
                   * (1 + AVAILABILITY_PENALTY * (1 - driver_availability))
  total_minutes  = drive_minutes + prep_time_minutes
  result         = max(MIN_ETA, round(total_minutes))
"""

# ── Tunable constants ────────────────────────────────────────────────────────
AVG_SPEED_KMH       = 30.0   # average urban delivery speed
TRAFFIC_WEIGHT      = 0.6    # how much traffic_factor inflates drive time
AVAILABILITY_PENALTY = 0.3   # extra wait when drivers are scarce
MIN_ETA             = 5      # floor — never return less than 5 minutes


def predict_eta(
    distance_km: float,
    prep_time_minutes: int,
    driver_availability: float,
    traffic_factor: float,
) -> int:
    """Return estimated delivery minutes as a positive integer."""
    drive_minutes = (distance_km / AVG_SPEED_KMH) * 60
    drive_minutes *= 1 + TRAFFIC_WEIGHT * traffic_factor
    drive_minutes *= 1 + AVAILABILITY_PENALTY * (1 - driver_availability)
    total = drive_minutes + prep_time_minutes
    return max(MIN_ETA, round(total))
