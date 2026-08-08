# Architecture

## System Overview

```
Customer App
     │  POST /orders
     ▼
order-service (8082)
     │  publishes → order-created
     ▼
Kafka
     ├──▶ restaurant-service (8081)   consumes order-created  → publishes order-accepted
     ├──▶ assignment-service (8085)   consumes order-accepted → nearest-driver spatial query
     │                                                         → publishes driver-assigned
     └──▶ notification-service (TBD)  consumes order-created, order-delivered

assignment-service
     └──▶ publishes driver-assigned
               ├──▶ order-service     updates status → DRIVER_ASSIGNED
               └──▶ delivery-service  begins tracking

delivery-service (8083)
     │  consumes driver-assigned
     │  tracks live delivery state
     └──▶ publishes order-delivered
               ├──▶ order-service     updates status → DELIVERED
               └──▶ notification-service

ops-dashboard (3000)
     └──▶ polls /health + /metrics-lite on all services every N seconds
```

## Services

| Service             | Stack              | Port | DB              | Owns                            |
|---------------------|--------------------|------|-----------------|---------------------------------|
| restaurant-service  | Java / Spring Boot | 8081 | restaurant_db   | Menu, restaurant availability   |
| order-service       | Java / Spring Boot | 8082 | order_db        | Order lifecycle                 |
| delivery-service    | Java / Spring Boot | 8083 | delivery_db     | Delivery state, driver tracking |
| eta-service         | Python / FastAPI   | 8084 | —               | ETA computation                 |
| assignment-service  | Python / FastAPI   | 8085 | assignment_db   | Driver–order matching           |
| ops-dashboard       | Node / Express     | 3000 | ops_db          | Internal ops view               |

## Kafka Topics (Phase 1)

Full JSON Schema definitions in `docs/kafka-contracts/`.

| Topic          | Schema file                      | Producer            | Consumers                                    |
|----------------|----------------------------------|---------------------|----------------------------------------------|
| order-created  | order-created.schema.json        | order-service       | restaurant-service, assignment-service, notification-service |
| order-accepted | order-accepted.schema.json       | restaurant-service  | order-service, eta-service                   |
| driver-assigned| driver-assigned.schema.json      | assignment-service  | order-service, delivery-service              |
| order-delivered| order-delivered.schema.json      | delivery-service    | order-service, notification-service          |

## REST API Contracts (OpenAPI)

Full specs in `docs/openapi/`.

| Service            | Spec file                  | Key endpoints                                    |
|--------------------|----------------------------|--------------------------------------------------|
| order-service      | order-service.yaml         | POST /orders, GET /orders/{id}                   |
| restaurant-service | restaurant-service.yaml    | GET /restaurants/{id}, GET /restaurants/{id}/menu|
| delivery-service   | delivery-service.yaml      | GET /deliveries/{orderId}, PUT /drivers/{id}/location |
| ops-dashboard      | ops-dashboard.yaml         | GET /status, GET /metrics (aggregated)           |

All services expose `GET /health` and `GET /metrics-lite` with a consistent shape
defined in `docs/openapi/ops-dashboard.yaml#MetricsLiteResponse`.

## Database Ownership

| Service            | Database       | Key tables                                    |
|--------------------|----------------|-----------------------------------------------|
| order-service      | order_db       | orders, order_items                           |
| restaurant-service | restaurant_db  | restaurants, menu_items                       |
| delivery-service   | delivery_db    | drivers, deliveries                           |
| assignment-service | assignment_db  | driver_locations (POINT + SPATIAL INDEX)      |
| ops-dashboard      | ops_db         | service_metric_snapshots                      |

Schema init scripts run in filename order from `infra/mysql/init/`.

## Data Store Decisions

- MySQL 8 with spatial extensions for driver location queries (see `decisions.md` ADR-001).
- `driver_locations.location` is a `POINT SRID 4326` column with a `SPATIAL INDEX` —
  Phase 6 nearest-driver query uses `ST_Distance_Sphere`.
- No Redis — spatial queries handled in MySQL to reduce operational surface area at this stage.
- Single MySQL instance with per-service logical databases; migrate to per-service RDS instances on AWS.
