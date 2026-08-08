const express = require('express');
const axios   = require('axios');

const app  = express();
const PORT = process.env.PORT || 3000;

app.use(express.json());

// Parse "name=url,name=url" from SERVICE_URLS env var.
// Set in docker-compose; falls back to empty so the service still starts locally.
function parseServiceUrls() {
  const raw = process.env.SERVICE_URLS || '';
  return raw
    .split(',')
    .map(s => s.trim())
    .filter(Boolean)
    .map(entry => {
      const eq = entry.indexOf('=');
      return { name: entry.slice(0, eq), url: entry.slice(eq + 1) };
    });
}

// GET /health — liveness probe for ops-dashboard itself
app.get('/health', (_req, res) => res.json({ status: 'UP' }));

// GET /status — fan out to every service /health endpoint.
// Returns immediately with whatever each service responds (or UNREACHABLE).
// Phase 10 will add a polling loop that persists snapshots to ops_db.
app.get('/status', async (_req, res) => {
  const services = parseServiceUrls();

  const results = await Promise.allSettled(
    services.map(({ name, url }) =>
      axios.get(url, { timeout: 3000 })
        .then(r => ({
          name,
          url,
          status: r.data?.status === 'UP' || r.status === 200 ? 'UP' : 'DOWN',
          checkedAt: new Date().toISOString(),
        }))
        .catch(() => ({
          name,
          url,
          status: 'UNREACHABLE',
          checkedAt: new Date().toISOString(),
        }))
    )
  );

  res.json({ services: results.map(r => r.value) });
});

app.listen(PORT, () => console.log(`ops-dashboard listening on port ${PORT}`));
