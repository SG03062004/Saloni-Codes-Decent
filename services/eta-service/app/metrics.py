import threading
import time

_lock = threading.Lock()

_start_time: float = time.monotonic()
_requests_total: int = 0
_errors_total: int = 0
_latency_sum_ms: float = 0.0


def record_request(latency_ms: float, *, error: bool = False) -> None:
    global _requests_total, _errors_total, _latency_sum_ms
    with _lock:
        _requests_total += 1
        _latency_sum_ms += latency_ms
        if error:
            _errors_total += 1


def snapshot() -> dict:
    with _lock:
        avg = _latency_sum_ms / _requests_total if _requests_total else 0.0
        return {
            "requests_total": _requests_total,
            "errors_total": _errors_total,
            "avg_latency_ms": round(avg, 2),
            "uptime_seconds": round(time.monotonic() - _start_time, 1),
        }
