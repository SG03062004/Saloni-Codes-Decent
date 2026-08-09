/**
 * load-spatial.js
 *
 * k6 stress test — MySQL spatial query under concurrent load.
 *
 * This is the critical benchmark for the "Redis replacement" decision (ADR-001):
 * concurrent driver-location UPSERT writes vs. nearest-driver reads via
 * ST_Distance_Sphere on a SPATIAL INDEX.
 *
 * Run:
 *   k6 run tests/load/load-spatial.js
 *
 * Two scenario groups run in parallel:
 *   writers  — PATCH /drivers/{id}/location  (simulate driver GPS pings)
 *   readers  — POST /orders (triggers nearest-driver query in assignment-service)
 *
 * Key metric to watch: spatial_query_duration p95 — if this exceeds ~200ms
 * under 50 concurrent writers, the ADR trade-off needs revisiting.
 */

import http from "k6/http";
import { check, sleep } from "k6";
import { Trend, Rate } from "k6/metrics";
import { uuidv4 } from "https://jslib.k6.io/k6-utils/1.4.0/index.js";

const ASSIGNMENT_URL = __ENV.ASSIGNMENT_URL || "http://localhost:8085";
const ORDER_URL      = __ENV.ORDER_URL      || "http://localhost:8082";
const RESTAURANT_URL = __ENV.RESTAURANT_URL || "http://localhost:8081";

const locationWriteDuration = new Trend("driver_location_write_duration", true);
const orderCreateDuration   = new Trend("spatial_query_duration",         true);
const writeErrorRate        = new Rate("write_error_rate");
const readErrorRate         = new Rate("read_error_rate");

// San Francisco bounding box for random coordinates
const SF = { latMin: 37.70, latMax: 37.83, lngMin: -122.52, lngMax: -122.35 };
const randInRange = (min, max) => min + Math.random() * (max - min);
const randSFLat   = () => randInRange(SF.latMin, SF.latMax);
const randSFLng   = () => randInRange(SF.lngMin, SF.lngMax);

export const options = {
  scenarios: {
    // 50 VUs continuously writing driver locations (GPS ping simulation)
    driver_location_writers: {
      executor:        "constant-vus",
      vus:             50,
      duration:        "3m",
      exec:            "writeDriverLocation",
    },
    // 10 VUs creating orders (each triggers a spatial nearest-driver query)
    order_creators: {
      executor:        "constant-vus",
      vus:             10,
      duration:        "3m",
      exec:            "createOrder",
      startTime:       "10s",   // let drivers register first
    },
  },
  thresholds: {
    // Spatial write p95 must stay under 100ms
    driver_location_write_duration: ["p(95)<100"],
    // Nearest-driver query (embedded in order creation) p95 under 500ms
    spatial_query_duration:         ["p(95)<500"],
    write_error_rate:               ["rate<0.02"],
    read_error_rate:                ["rate<0.02"],
  },
};

// ── Setup: seed restaurant ────────────────────────────────────────────────────

export function setup() {
  const res = http.post(
    `${RESTAURANT_URL}/restaurants`,
    JSON.stringify({ name: "Spatial Load Bistro", avgPrepTimeMinutes: 5 }),
    { headers: { "Content-Type": "application/json" } }
  );
  const restaurantId = res.status === 201 ? res.json("id") : "fallback-id";
  return { restaurantId };
}

// ── Writer scenario ───────────────────────────────────────────────────────────

export function writeDriverLocation() {
  // Each VU has a stable driver ID so it UPSERTs the same row (realistic GPS ping)
  const driverId = `load-driver-${__VU}`;
  const res = http.patch(
    `${ASSIGNMENT_URL}/drivers/${driverId}/location`,
    JSON.stringify({
      lat:          randSFLat(),
      lng:          randSFLng(),
      is_available: true,
    }),
    { headers: { "Content-Type": "application/json" } }
  );
  locationWriteDuration.add(res.timings.duration);
  writeErrorRate.add(
    !check(res, { "write: 204": (r) => r.status === 204 })
  );
  sleep(0.2);   // 5 GPS pings/second per driver
}

// ── Reader scenario ───────────────────────────────────────────────────────────

export function createOrder(data) {
  const { restaurantId } = data;
  const res = http.post(
    `${ORDER_URL}/orders`,
    JSON.stringify({
      customerId:   `spatial-cust-${uuidv4()}`,
      restaurantId: restaurantId,
      items: [{ menuItemId: "item-test", quantity: 1, unitPriceCents: 1000 }],
      deliveryAddress: {
        lat:    randSFLat(),
        lng:    randSFLng(),
        street: "Test St",
        city:   "San Francisco",
      },
    }),
    { headers: { "Content-Type": "application/json" } }
  );
  orderCreateDuration.add(res.timings.duration);
  readErrorRate.add(
    !check(res, { "order: 202": (r) => r.status === 202 })
  );
  sleep(1);
}
