/**
 * load-happy-path.js
 *
 * k6 load test — happy-path order creation + status read.
 *
 * Run:
 *   k6 run tests/load/load-happy-path.js
 *   k6 run --env ORDER_URL=http://localhost:8082 tests/load/load-happy-path.js
 *
 * What it measures:
 *   - POST /orders throughput and latency
 *   - GET /orders/{id} read latency under concurrent load
 *   - GET /orders/{id}/full-status composition latency (calls restaurant + delivery)
 *   - Error rate across all three endpoints
 */

import http from "k6/http";
import { check, sleep } from "k6";
import { Rate, Trend } from "k6/metrics";
import { uuidv4 } from "https://jslib.k6.io/k6-utils/1.4.0/index.js";

// ── Config ────────────────────────────────────────────────────────────────────

const ORDER_URL      = __ENV.ORDER_URL      || "http://localhost:8082";
const RESTAURANT_URL = __ENV.RESTAURANT_URL || "http://localhost:8081";

// ── Custom metrics ────────────────────────────────────────────────────────────

const createLatency     = new Trend("order_create_duration",      true);
const readLatency       = new Trend("order_read_duration",        true);
const fullStatusLatency = new Trend("order_full_status_duration", true);
const errorRate         = new Rate("error_rate");

// ── Load profile ──────────────────────────────────────────────────────────────
// Ramp up to 20 VUs over 30s, hold for 2 min, ramp down.
// Adjust for your hardware — 20 VUs is enough to stress the MySQL spatial query.

export const options = {
  stages: [
    { duration: "30s", target: 5  },   // warm-up
    { duration: "60s", target: 20 },   // ramp to target load
    { duration: "120s", target: 20 },  // sustained load
    { duration: "30s", target: 0  },   // ramp down
  ],
  thresholds: {
    // p95 of order creation must be under 800ms
    order_create_duration:      ["p(95)<800"],
    // p95 of status reads must be under 300ms
    order_read_duration:        ["p(95)<300"],
    // p95 of full-status (composition) must be under 1500ms
    order_full_status_duration: ["p(95)<1500"],
    // Overall error rate must stay below 1%
    error_rate:                 ["rate<0.01"],
    http_req_failed:            ["rate<0.01"],
  },
};

// ── Seed data: one restaurant shared across all VUs ───────────────────────────
// Created once in setup() so we don't hammer the restaurant service with creates.

export function setup() {
  const res = http.post(
    `${RESTAURANT_URL}/restaurants`,
    JSON.stringify({ name: "Load Test Bistro", avgPrepTimeMinutes: 5 }),
    { headers: { "Content-Type": "application/json" } }
  );
  if (res.status !== 201) {
    console.error(`setup: failed to create restaurant — ${res.status} ${res.body}`);
    return { restaurantId: "fallback-restaurant-id" };
  }
  const restaurantId = res.json("id");
  console.log(`setup: restaurant created id=${restaurantId}`);
  return { restaurantId };
}

// ── VU scenario ───────────────────────────────────────────────────────────────

export default function (data) {
  const { restaurantId } = data;
  const headers = { "Content-Type": "application/json" };

  // ── 1. Create order ─────────────────────────────────────────────────────────
  const payload = JSON.stringify({
    customerId:   `cust-${uuidv4()}`,
    restaurantId: restaurantId,
    items: [
      { menuItemId: "item-burger", quantity: 1, unitPriceCents: 1200 },
    ],
    deliveryAddress: {
      lat: 37.7749 + (Math.random() - 0.5) * 0.01,   // slight jitter
      lng: -122.4194 + (Math.random() - 0.5) * 0.01,
      street: "1 Market St",
      city:   "San Francisco",
    },
  });

  const createRes = http.post(`${ORDER_URL}/orders`, payload, { headers });
  createLatency.add(createRes.timings.duration);

  const createOk = check(createRes, {
    "create: status 202":        (r) => r.status === 202,
    "create: has orderId":       (r) => r.json("orderId") !== undefined,
    "create: status is PENDING": (r) => r.json("status") === "PENDING",
  });
  errorRate.add(!createOk);

  if (!createOk) {
    sleep(1);
    return;
  }

  const orderId = createRes.json("orderId");

  // ── 2. Read order status ────────────────────────────────────────────────────
  const readRes = http.get(`${ORDER_URL}/orders/${orderId}`);
  readLatency.add(readRes.timings.duration);

  const readOk = check(readRes, {
    "read: status 200":    (r) => r.status === 200,
    "read: orderId match": (r) => r.json("orderId") === orderId,
  });
  errorRate.add(!readOk);

  // ── 3. Full-status composition ──────────────────────────────────────────────
  const fullRes = http.get(`${ORDER_URL}/orders/${orderId}/full-status`);
  fullStatusLatency.add(fullRes.timings.duration);

  const fullOk = check(fullRes, {
    "full-status: 200 or 404": (r) => r.status === 200 || r.status === 404,
    "full-status: has orderId": (r) =>
      r.status === 200 ? r.json("orderId") === orderId : true,
  });
  errorRate.add(!fullOk);

  sleep(0.5);
}
