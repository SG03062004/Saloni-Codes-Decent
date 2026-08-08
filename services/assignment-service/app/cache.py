from cachetools import TTLCache

# Keyed by driver_id; avoids re-querying the same row within 2 s.
# Pure in-process — no distributed cache needed at this scale.
driver_cache: TTLCache = TTLCache(maxsize=1024, ttl=2)
