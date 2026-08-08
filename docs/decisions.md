# Architecture Decision Records

---

## ADR-001 — MySQL Spatial instead of Redis Geo

**Date:** 2025-07  
**Status:** Accepted

**Context:**  
Driver location lookups (nearest available driver to an order) require geo-proximity queries. The common default is Redis with the `GEOSEARCH` command.

**Decision:**  
Use MySQL 8 spatial functions (`ST_Distance_Sphere`, `POINT` column type) instead of Redis.

**Reasons:**
- Eliminates a second stateful infrastructure component (Redis) during early development.
- MySQL spatial indexes (`SPATIAL INDEX`) are sufficient for the expected driver fleet size (<10 k active drivers per region).
- Keeps the operational footprint to one database technology; easier to run locally and in CI.

**Trade-offs:**  
If query latency becomes a bottleneck at scale, introduce Redis Geo as a read-through cache in front of MySQL without changing the write path.

---

## ADR-002 — Single MySQL instance, per-service databases

**Date:** 2025-07  
**Status:** Accepted

**Context:**  
True microservice isolation calls for a separate database server per service. That adds operational overhead before any business logic exists.

**Decision:**  
Run one MySQL 8 container with three logical databases (`restaurant_db`, `order_db`, `delivery_db`). Each service connects only to its own database via its own credentials (to be added in a later phase).

**Trade-offs:**  
Noisy-neighbour risk on a single instance. Migrate to per-service RDS instances when deploying to AWS.

---

## ADR-003 — ops-dashboard as Node/Express instead of Grafana

**Date:** 2025-07  
**Status:** Accepted

**Context:**  
Grafana is the standard observability UI but requires a metrics backend (Prometheus/InfluxDB) and adds two more containers.

**Decision:**  
Build a minimal Node/Express dashboard that consumes Kafka topics directly and exposes a simple HTTP API for the ops team. Grafana can be layered on later.

**Reasons:**
- Zero extra infrastructure dependencies.
- Full control over what business events are surfaced (not just metrics).
- Demonstrates event-driven UI patterns relevant to the portfolio.

---
