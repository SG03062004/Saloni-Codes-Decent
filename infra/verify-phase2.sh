#!/usr/bin/env bash
# Phase 2 checkpoint — run from food-delivery-platform/infra/
# Usage: bash verify-phase2.sh
set -euo pipefail

KAFKA_CONTAINER="infra-kafka-1"
MYSQL_CONTAINER="infra-mysql-1"
TEST_TOPIC="phase2-smoke-test"

echo "=== 1. Container health ==="
docker compose ps --format "table {{.Name}}\t{{.Status}}"

echo ""
echo "=== 2. Kafka — create topic ==="
docker exec "$KAFKA_CONTAINER" \
  /opt/kafka/bin/kafka-topics.sh \
  --bootstrap-server localhost:9092 \
  --create --if-not-exists \
  --topic "$TEST_TOPIC" \
  --partitions 1 --replication-factor 1
echo "Topic '$TEST_TOPIC' ready."

echo ""
echo "=== 3. Kafka — produce then consume one message ==="
echo '{"test":"phase2"}' | docker exec -i "$KAFKA_CONTAINER" \
  /opt/kafka/bin/kafka-console-producer.sh \
  --bootstrap-server localhost:9092 \
  --topic "$TEST_TOPIC"

docker exec "$KAFKA_CONTAINER" \
  /opt/kafka/bin/kafka-console-consumer.sh \
  --bootstrap-server localhost:9092 \
  --topic "$TEST_TOPIC" \
  --from-beginning \
  --max-messages 1 \
  --timeout-ms 5000
echo "Kafka produce/consume OK."

echo ""
echo "=== 4. MySQL spatial — ST_Distance_Sphere ==="
docker exec "$MYSQL_CONTAINER" \
  mysql -uroot -proot --silent -e "
    SELECT ROUND(ST_Distance_Sphere(
      ST_GeomFromText('POINT(37.7793 -122.4193)', 4326),
      ST_GeomFromText('POINT(37.7955 -122.3937)', 4326)
    ), 1) AS distance_metres;
  "
echo "MySQL spatial OK."

echo ""
echo "=== 5. ops-dashboard /status ==="
curl -sf http://localhost:3000/status | python3 -m json.tool || \
  echo "(app services not up yet — UNREACHABLE is expected at this stage)"

echo ""
echo "=== Phase 2 checkpoint PASSED ==="
