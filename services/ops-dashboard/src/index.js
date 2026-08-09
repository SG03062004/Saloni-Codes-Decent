'use strict';

const express = require('express');
const axios   = require('axios');
const mysql   = require('mysql2/promise');
const path    = require('path');

const app  = express();
const PORT = process.env.PORT || 3000;
const POLL_INTERVAL_MS = parseInt(process.env.POLL_INTERVAL_MS || '10000', 10);

// ── DB pool ──────────────────────────────────────────────────────────────────

let pool = null;

async function getPool() {
  if (pool) return pool;
  pool = mysql.createPool({
    host:               process.env.DB_HOST     || 'mysql',
    port:               parseInt(process.env.DB_PORT || '3306', 10),
    user:               process.env.DB_USER     || 'root',
    password:           process.env.DB_PASS     || 'root',
    database:           process.env.DB_NAME     || 'ops_db',
    waitForConnections: true,
    connectionLimit:    5,
  });
  return pool;
}

// ── Service config ───────────────────────────────────────────────────────────
// SERVICE_URLS  = "name=healthUrl,name=healthUrl,..."
// METRICS_URLS  = "name=metricsUrl,name=metricsUrl,..."  (optional; Java services use /actuator/metrics)

function parseUrlMap(envVar) {
  return (process.env[envVar] || '')
    .split(',')
    .map(s => s.trim())
    .filter(Boolean)
    .reduce((acc, entry) => {
      const eq = entry.indexOf('=');
      acc[entry.slice(0, eq)] = entry.slice(eq + 1);
      return acc;
    }, {});
}

// ── Metrics extraction ───────────────────────────────────────────────────────
// Python services expose /metrics-lite with { requests_total, errors_total, avg_latency_ms }
// Java services expose /actuator/metrics — we pull http.server.requests count + mean

async function fetchMetrics(metricsUrl) {
  if (!metricsUrl) return { request_count: 0, error_count: 0, avg_latency_ms: 0 };
  try {
    // Try /metrics-lite first (Python services)
    if (metricsUrl.includes('metrics-lite')) {
      const { data } = await axios.get(metricsUrl, { timeout: 3000 });
      return {
        request_count:  data.requests_total  || 0,
        error_count:    data.errors_total    || 0,
        avg_latency_ms: data.avg_latency_ms  || 0,
      };
    }
    // Java: /actuator/metrics/http.server.requests
    const { data } = await axios.get(metricsUrl, { timeout: 3000 });
    const count = data.measurements?.find(m => m.statistic === 'COUNT')?.value || 0;
    const total = data.measurements?.find(m => m.statistic === 'TOTAL_TIME')?.value || 0;
    const avg   = count > 0 ? (total / count) * 1000 : 0; // seconds → ms
    return { request_count: Math.round(count), error_count: 0, avg_latency_ms: Math.round(avg * 100) / 100 };
  } catch {
    return { request_count: 0, error_count: 0, avg_latency_ms: 0 };
  }
}

// ── Polling loop ─────────────────────────────────────────────────────────────

async function poll() {
  const healthUrls  = parseUrlMap('SERVICE_URLS');
  const metricsUrls = parseUrlMap('METRICS_URLS');

  const results = await Promise.allSettled(
    Object.entries(healthUrls).map(async ([name, url]) => {
      let status = 'DOWN';
      try {
        const { data, status: httpStatus } = await axios.get(url, { timeout: 3000 });
        status = (data?.status === 'UP' || httpStatus === 200) ? 'UP' : 'DOWN';
      } catch { /* stays DOWN */ }

      const metrics = await fetchMetrics(metricsUrls[name]);
      return { name, status, ...metrics };
    })
  );

  const db = await getPool();
  for (const r of results) {
    if (r.status !== 'fulfilled') continue;
    const { name, status, request_count, error_count, avg_latency_ms } = r.value;
    await db.execute(
      `INSERT INTO service_metric_snapshots
         (service_name, status, request_count, error_count, avg_latency_ms)
       VALUES (?, ?, ?, ?, ?)`,
      [name, status, request_count, error_count, avg_latency_ms]
    );
  }
}

function startPolling() {
  // Retry until DB is ready, then start the interval
  const tryPoll = () =>
    poll().catch(err => console.error('[poll error]', err.message));

  tryPoll();
  setInterval(tryPoll, POLL_INTERVAL_MS);
}

// ── API routes ───────────────────────────────────────────────────────────────

app.get('/health', (_req, res) => res.json({ status: 'UP' }));

// Latest snapshot per service
app.get('/api/status', async (_req, res) => {
  try {
    const db = await getPool();
    const [rows] = await db.execute(`
      SELECT s.*
      FROM service_metric_snapshots s
      INNER JOIN (
        SELECT service_name, MAX(captured_at) AS latest
        FROM service_metric_snapshots
        GROUP BY service_name
      ) t ON s.service_name = t.service_name AND s.captured_at = t.latest
    `);
    res.json(rows);
  } catch (err) {
    res.status(503).json({ error: err.message });
  }
});

// Last-hour trend per service (for charts)
app.get('/api/snapshots', async (req, res) => {
  try {
    const db = await getPool();
    const service = req.query.service;
    const [rows] = service
      ? await db.execute(
          `SELECT service_name, status, request_count, error_count, avg_latency_ms, captured_at
           FROM service_metric_snapshots
           WHERE service_name = ? AND captured_at >= NOW() - INTERVAL 1 HOUR
           ORDER BY captured_at ASC`,
          [service]
        )
      : await db.execute(
          `SELECT service_name, status, request_count, error_count, avg_latency_ms, captured_at
           FROM service_metric_snapshots
           WHERE captured_at >= NOW() - INTERVAL 1 HOUR
           ORDER BY captured_at ASC`
        );
    res.json(rows);
  } catch (err) {
    res.status(503).json({ error: err.message });
  }
});

// Order funnel — queries order_db directly
app.get('/api/funnel', async (_req, res) => {
  try {
    const db = mysql.createPool({
      host:     process.env.DB_HOST || 'mysql',
      port:     parseInt(process.env.DB_PORT || '3306', 10),
      user:     process.env.DB_USER || 'root',
      password: process.env.DB_PASS || 'root',
      database: 'order_db',
    });
    const [rows] = await db.execute(
      `SELECT status, COUNT(*) AS count FROM orders GROUP BY status`
    );
    await db.end();
    // Return in funnel order
    const order = ['PENDING', 'ACCEPTED', 'DRIVER_ASSIGNED', 'DELIVERED', 'REJECTED'];
    const map   = Object.fromEntries(rows.map(r => [r.status, Number(r.count)]));
    res.json(order.map(s => ({ status: s, count: map[s] || 0 })));
  } catch (err) {
    res.status(503).json({ error: err.message });
  }
});

// Serve dashboard HTML
app.get('/', (_req, res) =>
  res.sendFile(path.join(__dirname, 'dashboard.html'))
);

// ── Boot ─────────────────────────────────────────────────────────────────────

app.listen(PORT, () => {
  console.log(JSON.stringify({ level: 'info', service: 'ops-dashboard', msg: `listening on ${PORT}` }));
  // Delay first poll slightly to let DB container finish init
  setTimeout(startPolling, 5000);
});
