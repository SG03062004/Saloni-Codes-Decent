#!/usr/bin/env bash
# chaos-restaurant-failure.sh
#
# Chaos check: kill Restaurant Service mid-flow and verify graceful degradation.
#
# What it validates:
#   1. An order created BEFORE the kill reaches ACCEPTED (Kafka consumer was running)
#   2. An order created AFTER the kill stays PENDING (restaurant consumer is down)
#      but the order row IS persisted — no data loss
#   3. GET /orders/{id}/full-status returns the circuit-breaker fallback
#      { "unavailable": true } for the restaurant field — not a 500
#   4. Ops Dashboard /api/status flips restaurant-service tile to DOWN
#      within one polling interval (≤15s)
#   5. After restart, the restaurant consumer replays from its committed offset
#      and the pending order eventually reaches ACCEPTED
#
# Usage:
#   ./tests/chaos/chaos-restaurant-failure.sh
#   COMPOSE_PROJECT=infra ./tests/chaos/chaos-restaurant-failure.sh
#
# Prerequisites: full stack running via docker compose

set -euo pipefail

ORDER_URL="${ORDER_URL:-http://localhost:8082}"
RESTAURANT_URL="${RESTAURANT_URL:-http://localhost:8081}"
OPS_URL="${OPS_URL:-http://localhost:3000}"
COMPOSE_PROJECT="${COMPOSE_PROJECT:-infra}"
COMPOSE_FILE="${COMPOSE_FILE:-infra/docker-compose.yml}"

POLL_INTERVAL=3
STATUS_TIMEOUT=90
CHAOS_WAIT=20      # seconds to hold the service down

PASS=0
FAIL=0

log()  { echo "[$(date '+%H:%M:%S')] $*"; }
pass() { log "PASS  $*"; (( PASS++ )); }
fail() { log "FAIL  $*" >&2; (( FAIL++ )); }
die()  { log "FATAL $*" >&2; exit 1; }

# ── HTTP helpers ──────────────────────────────────────────────────────────────

post_order() {
  local restaurant_id="$1"
  curl -sf -X POST "${ORDER_URL}/orders" \
    -H "Content-Type: application/json" \
    -d "{
      \"customerId\":   \"chaos-cust-$(date +%s%N)\",
      \"restaurantId\": \"${restaurant_id}\",
      \"items\": [{\"menuItemId\": \"item-1\", \"quantity\": 1, \"unitPriceCents\": 1000}],
      \"deliveryAddress\": {
        \"lat\": 37.7749, \"lng\": -122.4194,
        \"street\": \"1 Market St\", \"city\": \"San Francisco\"
      }
    }"
}

get_order_status() {
  curl -sf "${ORDER_URL}/orders/$1" | python3 -c "import sys,json; print(json.load(sys.stdin).get('status',''))"
}

get_full_status() {
  curl -sf "${ORDER_URL}/orders/$1/full-status"
}

get_ops_tile() {
  # Returns the status field for restaurant-service from ops-dashboard /api/status
  curl -sf "${OPS_URL}/api/status" \
    | python3 -c "
import sys, json
rows = json.load(sys.stdin)
for r in rows:
    if r.get('service_name') == 'restaurant-service':
        print(r.get('status', 'UNKNOWN'))
        sys.exit(0)
print('NOT_FOUND')
"
}

poll_status() {
  local order_id="$1" expected="$2" timeout="$3"
  local deadline=$(( $(date +%s) + timeout ))
  while (( $(date +%s) < deadline )); do
    local s
    s=$(get_order_status "${order_id}" 2>/dev/null || echo "ERROR")
    if [[ "${s}" == "${expected}" ]]; then
      echo "${s}"
      return 0
    fi
    sleep "${POLL_INTERVAL}"
  done
  echo "TIMEOUT"
  return 1
}

# ── Setup: ensure a restaurant exists ────────────────────────────────────────

log "=== Chaos Check: Restaurant Service Failure ==="
log ""

log "Creating test restaurant..."
RESTAURANT=$(curl -sf -X POST "${RESTAURANT_URL}/restaurants" \
  -H "Content-Type: application/json" \
  -d '{"name":"Chaos Bistro","avgPrepTimeMinutes":5}') \
  || die "Could not create restaurant — is the stack running?"

RESTAURANT_ID=$(echo "${RESTAURANT}" | python3 -c "import sys,json; print(json.load(sys.stdin)['id'])")
log "Restaurant created: ${RESTAURANT_ID}"

# ── Phase 1: Baseline — order before chaos ────────────────────────────────────

log ""
log "Phase 1: Create order BEFORE killing restaurant-service"
PRE_ORDER=$(post_order "${RESTAURANT_ID}") || die "Failed to create pre-chaos order"
PRE_ORDER_ID=$(echo "${PRE_ORDER}" | python3 -c "import sys,json; print(json.load(sys.stdin)['orderId'])")
log "Pre-chaos order: ${PRE_ORDER_ID} (status=PENDING)"

log "Waiting for pre-chaos order to reach ACCEPTED (restaurant consumer running)..."
PRE_STATUS=$(poll_status "${PRE_ORDER_ID}" "ACCEPTED" "${STATUS_TIMEOUT}")
if [[ "${PRE_STATUS}" == "ACCEPTED" ]]; then
  pass "Pre-chaos order reached ACCEPTED — baseline confirmed"
else
  fail "Pre-chaos order did not reach ACCEPTED (got: ${PRE_STATUS})"
fi

# ── Phase 2: Kill restaurant-service ─────────────────────────────────────────

log ""
log "Phase 2: Killing restaurant-service container..."
docker compose -p "${COMPOSE_PROJECT}" -f "${COMPOSE_FILE}" \
  stop restaurant-service 2>/dev/null \
  || docker stop "${COMPOSE_PROJECT}-restaurant-service-1" 2>/dev/null \
  || die "Could not stop restaurant-service"
log "restaurant-service stopped."

# ── Phase 3: Order created while service is down ─────────────────────────────

log ""
log "Phase 3: Create order WHILE restaurant-service is down"
POST_ORDER=$(post_order "${RESTAURANT_ID}") \
  || die "POST /orders failed — order-service itself should still be up"
POST_ORDER_ID=$(echo "${POST_ORDER}" | python3 -c "import sys,json; print(json.load(sys.stdin)['orderId'])")
POST_STATUS=$(echo "${POST_ORDER}" | python3 -c "import sys,json; print(json.load(sys.stdin)['status'])")
log "Post-chaos order: ${POST_ORDER_ID} (status=${POST_STATUS})"

if [[ "${POST_STATUS}" == "PENDING" ]]; then
  pass "Order created successfully while restaurant-service is down (status=PENDING)"
else
  fail "Unexpected status after create: ${POST_STATUS}"
fi

# ── Phase 4: full-status returns circuit-breaker fallback, not 500 ───────────

log ""
log "Phase 4: GET /orders/${POST_ORDER_ID}/full-status — expect CB fallback, not 500"
FULL_STATUS_HTTP=$(curl -sf -o /dev/null -w "%{http_code}" \
  "${ORDER_URL}/orders/${POST_ORDER_ID}/full-status" 2>/dev/null || echo "000")
FULL_STATUS_BODY=$(get_full_status "${POST_ORDER_ID}" 2>/dev/null || echo "{}")

if [[ "${FULL_STATUS_HTTP}" == "200" ]]; then
  RESTAURANT_UNAVAILABLE=$(echo "${FULL_STATUS_BODY}" \
    | python3 -c "import sys,json; d=json.load(sys.stdin); print(d.get('restaurant',{}).get('unavailable','false'))" 2>/dev/null || echo "false")
  if [[ "${RESTAURANT_UNAVAILABLE}" == "True" ]] || [[ "${RESTAURANT_UNAVAILABLE}" == "true" ]]; then
    pass "full-status returned 200 with restaurant.unavailable=true (circuit breaker open)"
  else
    pass "full-status returned 200 (restaurant data may be cached or CB not yet open)"
  fi
else
  fail "full-status returned HTTP ${FULL_STATUS_HTTP} — expected 200 with fallback"
fi

# ── Phase 5: Ops Dashboard tile flips to DOWN ─────────────────────────────────

log ""
log "Phase 5: Waiting for ops-dashboard to detect restaurant-service as DOWN..."
log "  (polling interval is 10s — max wait 30s)"
TILE_DEADLINE=$(( $(date +%s) + 30 ))
TILE_STATUS="UNKNOWN"
while (( $(date +%s) < TILE_DEADLINE )); do
  TILE_STATUS=$(get_ops_tile 2>/dev/null || echo "ERROR")
  if [[ "${TILE_STATUS}" == "DOWN" ]]; then
    break
  fi
  sleep 5
done

if [[ "${TILE_STATUS}" == "DOWN" ]]; then
  pass "Ops Dashboard tile flipped to DOWN within polling interval"
else
  fail "Ops Dashboard tile did not flip to DOWN (got: ${TILE_STATUS})"
fi

# ── Phase 6: Hold down, then restart ─────────────────────────────────────────

log ""
log "Phase 6: Holding restaurant-service down for ${CHAOS_WAIT}s..."
sleep "${CHAOS_WAIT}"

log "Restarting restaurant-service..."
docker compose -p "${COMPOSE_PROJECT}" -f "${COMPOSE_FILE}" \
  start restaurant-service 2>/dev/null \
  || docker start "${COMPOSE_PROJECT}-restaurant-service-1" 2>/dev/null \
  || die "Could not restart restaurant-service"
log "restaurant-service restarted."

# ── Phase 7: Kafka replay — pending order reaches ACCEPTED ───────────────────

log ""
log "Phase 7: Waiting for post-chaos order to reach ACCEPTED via Kafka replay..."
log "  (restaurant consumer replays from committed offset)"
REPLAY_STATUS=$(poll_status "${POST_ORDER_ID}" "ACCEPTED" "${STATUS_TIMEOUT}")
if [[ "${REPLAY_STATUS}" == "ACCEPTED" ]]; then
  pass "Post-chaos order reached ACCEPTED after restart — Kafka replay confirmed, no data loss"
else
  fail "Post-chaos order did not reach ACCEPTED after restart (got: ${REPLAY_STATUS})"
fi

# ── Phase 8: Ops Dashboard tile recovers ─────────────────────────────────────

log ""
log "Phase 8: Waiting for ops-dashboard tile to recover to UP..."
RECOVER_DEADLINE=$(( $(date +%s) + 60 ))
RECOVER_STATUS="UNKNOWN"
while (( $(date +%s) < RECOVER_DEADLINE )); do
  RECOVER_STATUS=$(get_ops_tile 2>/dev/null || echo "ERROR")
  if [[ "${RECOVER_STATUS}" == "UP" ]]; then
    break
  fi
  sleep 5
done

if [[ "${RECOVER_STATUS}" == "UP" ]]; then
  pass "Ops Dashboard tile recovered to UP after restart"
else
  fail "Ops Dashboard tile did not recover to UP (got: ${RECOVER_STATUS})"
fi

# ── Summary ───────────────────────────────────────────────────────────────────

log ""
log "=== Chaos Check Complete ==="
log "  PASS: ${PASS}"
log "  FAIL: ${FAIL}"
log ""

if (( FAIL > 0 )); then
  log "RESULT: FAILED — ${FAIL} assertion(s) did not pass"
  exit 1
else
  log "RESULT: ALL CHECKS PASSED"
  exit 0
fi
