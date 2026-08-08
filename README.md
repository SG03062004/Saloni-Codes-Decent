# Food Delivery Platform

A microservices monorepo simulating a food delivery backend — order placement, restaurant acceptance, driver assignment, ETA computation, and live delivery tracking.

## Services

| Service            | Stack              | Port |
|--------------------|--------------------|------|
| restaurant-service | Java / Spring Boot | 8081 |
| order-service      | Java / Spring Boot | 8082 |
| delivery-service   | Java / Spring Boot | 8083 |
| eta-service        | Python / FastAPI   | 8084 |
| assignment-service | Python / FastAPI   | 8085 |
| ops-dashboard      | Node / Express     | 3000 |

## Quick Start

```bash
cd infra
docker compose up --build
```

All services, MySQL, Zookeeper, and Kafka start together. MySQL databases are created automatically from `infra/mysql/init/`.

## Repo Layout

```
food-delivery-platform/
├── services/
│   ├── restaurant-service/   Java/Spring Boot — menu & restaurant availability
│   ├── order-service/        Java/Spring Boot — order lifecycle
│   ├── delivery-service/     Java/Spring Boot — delivery state & driver location
│   ├── eta-service/          Python/FastAPI   — ETA computation
│   ├── assignment-service/   Python/FastAPI   — driver–order matching
│   └── ops-dashboard/        Node/Express     — internal ops view
├── infra/
│   ├── docker-compose.yml
│   ├── mysql/init/           SQL scripts run on first MySQL start
│   └── logging/
│       └── logback-json.xml  Shared JSON logging config for Java services
├── docs/
│   ├── architecture.md       Flow diagram & service responsibilities (source of truth)
│   ├── kafka-contracts/      Phase 1 Kafka event schemas
│   └── decisions.md          Architecture Decision Records (ADRs)
└── .github/workflows/        CI pipeline definitions
```

## Docs

- [Architecture & flow diagram](docs/architecture.md)
- [Kafka event contracts](docs/kafka-contracts/phase1-events.md)
- [Architecture decisions (ADRs)](docs/decisions.md)
