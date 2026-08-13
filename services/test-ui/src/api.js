// All URLs go through the Vite dev-server proxy (same origin → no CORS).
// In production, point these at a real reverse-proxy / API gateway instead.
const ORDER_URL    = '/api/orders';
const DELIVERY_URL = '/api/delivery';
const ASSIGN_URL   = '/api/assign';

async function request(url, options = {}) {
  let res;
  try {
    res = await fetch(url, {
      headers: { 'Content-Type': 'application/json', ...options.headers },
      ...options,
    });
  } catch (networkErr) {
    // fetch() itself threw — backend is unreachable
    throw new Error(
      `Network error — is the backend running? (${networkErr.message})`
    );
  }

  const text = await res.text();
  let body = null;
  try {
    body = text ? JSON.parse(text) : null;
  } catch (e) {
    body = { error: text || `HTTP ${res.status}` };
  }

  if (!res.ok) {
    const msg = typeof body?.error === 'string' ? body.error : (body?.message || `HTTP ${res.status}`);
    throw new Error(msg);
  }
  return body;
}


export const api = {
  createOrder: (payload) =>
    request(`${ORDER_URL}`, { method: 'POST', body: JSON.stringify(payload) }),

  getFullStatus: (orderId) =>
    request(`${ORDER_URL}/${orderId}/full-status`),

  // Direct order-service check (bypasses the full-status aggregator)
  getOrder: (orderId) =>
    request(`${ORDER_URL}/${orderId}`),

  // Direct delivery-service check (bypasses the order-service aggregator)
  getDeliveryByOrder: (orderId) =>
    request(`${DELIVERY_URL}/by-order/${orderId}`),

  registerDriver: (driverId, lat, lng) =>
    request(`${ASSIGN_URL}/drivers/${driverId}/location`, {
      method: 'PATCH',
      body: JSON.stringify({ lat: Number(lat), lng: Number(lng), is_available: true }),
    }),

  advanceDelivery: (orderId, status) =>
    request(`${DELIVERY_URL}/${orderId}/status`, {
      method: 'PATCH',
      body: JSON.stringify({ status }),
    }),

  // Test-harness only: seed a delivery row in ASSIGNED state directly,
  // bypassing the Kafka DriverAssignedEvent flow.
  seedDelivery: (orderId, driverId) =>
    request(`${DELIVERY_URL}/seed`, {
      method: 'POST',
      body: JSON.stringify({ orderId, driverId }),
    }),
};

