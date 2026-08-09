# Blue-Green Deployment — food-delivery-platform

## How it works

```
                    ┌─────────────────────────────────────┐
  clients           │  nginx proxy  (bg-proxy container)  │
  :8081-8086        │  active-upstreams.conf  ──symlink──► │──► blue stack (18xxx ports)
  :3000             │                                      │    OR
                    └─────────────────────────────────────┘──► green stack (19xxx ports)
                                        │
                    ┌───────────────────┴──────────────────┐
                    │  Shared infra  (infra compose project) │
                    │  mysql · kafka                         │
                    └────────────────────────────────────────┘
```

- **Shared infra** (MySQL, Kafka) runs once under the `infra` compose project — never restarted during a deploy.
- **Blue / Green stacks** are independent compose projects (`-p blue`, `-p green`). Only one is live at a time.
- **nginx proxy** is the single external entry point. Cutover = update the `conf.d/active-upstreams.conf` symlink + `nginx -s reload` (graceful, zero dropped connections).
- **Host ports** on the blue/green stacks (18xxx / 19xxx) are only used by `deploy.sh` for direct health-check validation before cutover. All production traffic goes through nginx on the standard ports.

## Port map

| Service | nginx (prod) | Blue (health-check) | Green (health-check) |
|---|---|---|---|
| restaurant-service | 8081 | 18081 | 19081 |
| order-service | 8082 | 18082 | 19082 |
| delivery-service | 8083 | 18083 | 19083 |
| eta-service | 8084 | 18084 | 19084 |
| assignment-service | 8085 | 18085 | 19085 |
| notification-service | 8086 | 18086 | 19086 |
| ops-dashboard | 3000 | 13000 | 19000 |

## First-time setup

```bash
cd infra

# 1. Start shared infra
docker compose -p infra -f docker-compose.yml up -d mysql kafka
# Wait for healthy:
docker compose -p infra -f docker-compose.yml ps

# 2. Start the initial blue stack
docker compose -p blue -f blue-green/docker-compose.blue.yml up -d --build

# 3. Start the nginx proxy (points at blue by default)
docker compose -p proxy -f blue-green/docker-compose.proxy.yml up -d
```

## Deploy a new version (blue → green)

```bash
cd infra/blue-green

# Build new images first (optional — deploy.sh also builds)
docker compose -p green -f docker-compose.green.yml build

# Run the deploy
./deploy.sh
# Equivalent: ./deploy.sh --target green
```

`deploy.sh` will:
1. Start the green stack (`docker compose -p green up -d --build`)
2. Poll every service's health endpoint until all return `200` (timeout: 120s)
3. Atomically swap `conf.d/active-upstreams.conf` → `upstreams-green.conf`
4. Send `nginx -s reload` (graceful — in-flight requests finish on old workers)
5. Tear down the blue stack

If **any health check fails**, the script rolls back automatically:
- Restores the symlink to the previous slot
- Reloads nginx
- Tears down the failed stack

## Manual rollback

```bash
./deploy.sh --rollback
```

Switches the proxy back to the previously active slot and tears down the current one.

## Deploy green → blue (next release)

```bash
./deploy.sh          # inactive slot is now blue, so it deploys there
```

The script always deploys to whichever slot is **not** currently live.

## Verify the active slot

```bash
readlink infra/blue-green/conf.d/active-upstreams.conf
# → ../upstreams-blue.conf  or  ../upstreams-green.conf
```

## Checkpoint demo

```bash
# 1. Hit the live order-service through the proxy
curl http://localhost:8082/actuator/health

# 2. Run a deploy
./deploy.sh

# 3. While deploy is running, keep hitting the endpoint — no 5xx
watch -n1 'curl -sf http://localhost:8082/actuator/health | jq .status'

# 4. Kill a service mid-deploy to trigger auto-rollback
docker stop green-order-service-1
# deploy.sh detects the health-check failure and rolls back automatically
```
