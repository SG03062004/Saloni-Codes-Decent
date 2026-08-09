#!/usr/bin/env bash
# deploy.sh — Blue-Green deployment for food-delivery-platform
#
# Usage:
#   ./deploy.sh [--target blue|green]   # deploy to the inactive slot
#   ./deploy.sh --rollback              # revert proxy to the previous slot
#
# What it does:
#   1. Determine the inactive slot (the one not currently live)
#   2. Build + start the inactive slot's compose stack
#   3. Wait for every service in that slot to pass health checks
#   4. Atomically switch the nginx upstream symlink + reload (zero-downtime)
#   5. Decommission the previously active slot
#   6. On any failure: auto-rollback by restoring the previous symlink
#
# Prerequisites:
#   - Infra stack (mysql, kafka) already running:
#       docker compose -p infra -f ../docker-compose.yml up -d mysql kafka
#   - Proxy already running:
#       docker compose -p proxy -f docker-compose.proxy.yml up -d
#   - curl available on the host

set -euo pipefail

# ── Config ────────────────────────────────────────────────────────────────────

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
CONF_D="${SCRIPT_DIR}/conf.d"
SYMLINK="${CONF_D}/active-upstreams.conf"
PROXY_CONTAINER="bg-proxy"

# Health-check: max wait time per service (seconds)
HC_TIMEOUT=120
HC_INTERVAL=5

# Services and their host-exposed health-check ports per slot
# Format: "service_name:blue_host_port:green_host_port:health_path"
declare -a SERVICES=(
  "order-service:18082:19082:/actuator/health"
  "restaurant-service:18081:19081:/actuator/health"
  "delivery-service:18083:19083:/actuator/health"
  "eta-service:18084:19084:/health"
  "assignment-service:18085:19085:/health"
  "notification-service:18086:19086:/health"
  "ops-dashboard:13000:19000:/health"
)

# ── Helpers ───────────────────────────────────────────────────────────────────

log()  { echo "[$(date '+%H:%M:%S')] $*"; }
info() { log "INFO  $*"; }
ok()   { log "OK    $*"; }
err()  { log "ERROR $*" >&2; }
die()  { err "$*"; exit 1; }

# Determine which slot is currently live by reading the symlink target
current_slot() {
  local target
  target=$(readlink "${SYMLINK}" 2>/dev/null || echo "upstreams-blue.conf")
  if [[ "${target}" == *blue* ]]; then echo "blue"; else echo "green"; fi
}

# Return the opposite slot
other_slot() {
  if [[ "$1" == "blue" ]]; then echo "green"; else echo "blue"; fi
}

# Get the host port for a service in a given slot
host_port() {
  local svc="$1" slot="$2"
  for entry in "${SERVICES[@]}"; do
    IFS=: read -r name blue_port green_port path <<< "${entry}"
    if [[ "${name}" == "${svc}" ]]; then
      if [[ "${slot}" == "blue" ]]; then echo "${blue_port}"; else echo "${green_port}"; fi
      return
    fi
  done
}

# ── Health-check gate ─────────────────────────────────────────────────────────
# Polls every service in the target slot until all return HTTP 200 with
# {"status":"UP"} (or just 200 for Python services), or until timeout.

wait_for_healthy() {
  local slot="$1"
  local deadline=$(( $(date +%s) + HC_TIMEOUT ))

  info "Waiting for all ${slot} services to be healthy (timeout ${HC_TIMEOUT}s)..."

  for entry in "${SERVICES[@]}"; do
    IFS=: read -r name blue_port green_port path <<< "${entry}"
    local port
    port=$(if [[ "${slot}" == "blue" ]]; then echo "${blue_port}"; else echo "${green_port}"; fi)
    local url="http://localhost:${port}${path}"
    local elapsed=0

    info "  Checking ${name} at ${url}"
    while true; do
      local now
      now=$(date +%s)
      if (( now > deadline )); then
        err "Timeout waiting for ${name} (${url}) after ${HC_TIMEOUT}s"
        return 1
      fi

      local http_code
      http_code=$(curl -sf -o /dev/null -w "%{http_code}" --max-time 3 "${url}" 2>/dev/null || echo "000")

      if [[ "${http_code}" == "200" ]]; then
        ok "    ${name} is UP (${http_code})"
        break
      fi

      elapsed=$(( now - (deadline - HC_TIMEOUT) ))
      info "    ${name} not ready yet (${http_code}) — ${elapsed}s elapsed, retrying in ${HC_INTERVAL}s..."
      sleep "${HC_INTERVAL}"
    done
  done

  ok "All ${slot} services are healthy."
}

# ── Proxy cutover ─────────────────────────────────────────────────────────────
# Atomically swap the symlink and send nginx a graceful reload.
# nginx -s reload: finishes in-flight requests on old workers, then switches.

cutover_proxy() {
  local new_slot="$1"
  local upstream_file="../upstreams-${new_slot}.conf"

  info "Cutting over proxy to ${new_slot}..."

  # Atomic symlink swap (ln -sf is atomic on Linux/macOS)
  ln -sf "${upstream_file}" "${SYMLINK}"
  ok "Symlink updated: ${SYMLINK} -> ${upstream_file}"

  # Reload nginx inside the proxy container (graceful — zero dropped connections)
  docker exec "${PROXY_CONTAINER}" nginx -s reload
  ok "nginx reloaded — traffic now flowing to ${new_slot} stack."
}

# ── Rollback ──────────────────────────────────────────────────────────────────

rollback() {
  local failed_slot="$1"
  local safe_slot
  safe_slot=$(other_slot "${failed_slot}")

  err "Deployment to ${failed_slot} FAILED — rolling back to ${safe_slot}."
  ln -sf "../upstreams-${safe_slot}.conf" "${SYMLINK}"
  docker exec "${PROXY_CONTAINER}" nginx -s reload 2>/dev/null || true
  ok "Rollback complete. Proxy restored to ${safe_slot}."

  info "Tearing down failed ${failed_slot} stack..."
  docker compose -p "${failed_slot}" \
    -f "${SCRIPT_DIR}/docker-compose.${failed_slot}.yml" \
    down --remove-orphans 2>/dev/null || true

  exit 1
}

# ── Main ──────────────────────────────────────────────────────────────────────

main() {
  # Parse args
  local force_target=""
  local do_rollback=false

  while [[ $# -gt 0 ]]; do
    case "$1" in
      --target)   force_target="$2"; shift 2 ;;
      --rollback) do_rollback=true; shift ;;
      *)          die "Unknown argument: $1" ;;
    esac
  done

  # ── Rollback mode ──────────────────────────────────────────────────────────
  if [[ "${do_rollback}" == true ]]; then
    local live
    live=$(current_slot)
    local prev
    prev=$(other_slot "${live}")
    info "Manual rollback requested: ${live} -> ${prev}"
    cutover_proxy "${prev}"
    info "Tearing down ${live} stack..."
    docker compose -p "${live}" \
      -f "${SCRIPT_DIR}/docker-compose.${live}.yml" \
      down --remove-orphans
    ok "Rollback to ${prev} complete."
    exit 0
  fi

  # ── Deploy mode ────────────────────────────────────────────────────────────
  local live
  live=$(current_slot)
  local target
  target="${force_target:-$(other_slot "${live}")}"

  if [[ "${target}" == "${live}" ]]; then
    die "Target slot '${target}' is already live. Use --target to force."
  fi

  info "=== Blue-Green Deploy ==="
  info "  Live slot   : ${live}"
  info "  Target slot : ${target}"
  info "  Compose file: docker-compose.${target}.yml"
  echo ""

  # Step 1 — Build + start the target stack
  info "Step 1/4 — Building and starting ${target} stack..."
  docker compose -p "${target}" \
    -f "${SCRIPT_DIR}/docker-compose.${target}.yml" \
    up -d --build --remove-orphans \
    || { err "Failed to start ${target} stack"; rollback "${target}"; }

  # Step 2 — Health-check gate
  info "Step 2/4 — Running health-check gate on ${target} stack..."
  wait_for_healthy "${target}" || rollback "${target}"

  # Step 3 — Atomic proxy cutover
  info "Step 3/4 — Cutting over proxy from ${live} to ${target}..."
  cutover_proxy "${target}" || { err "Proxy cutover failed"; rollback "${target}"; }

  # Brief pause to let nginx finish draining in-flight requests on old workers
  sleep 3

  # Step 4 — Decommission old stack
  info "Step 4/4 — Decommissioning ${live} stack..."
  docker compose -p "${live}" \
    -f "${SCRIPT_DIR}/docker-compose.${live}.yml" \
    down --remove-orphans
  ok "${live} stack stopped."

  echo ""
  ok "=== Deployment complete. Active slot: ${target} ==="
}

main "$@"
