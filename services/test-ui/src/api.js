const ORDER_URL    = import.meta.env.VITE_ORDER_SERVICE_URL    || 'http://localhost:8082';
const DELIVERY_URL = import.meta.env.VITE_DELIVERY_SERVICE_URL || 'http://localhost:8083';
const ASSIGN_URL   = import.meta.env.VITE_ASSIGNMENT_SERVICE_URL || 'http://localhost:8085';

async function request(url, options = {}) {
  const res = await fetch(url, {
    headers: { 'Content-Type': 'application/json', ...options.headers },
    ...options,
  });
  const text = await res.text();
  const body = text ? JSON.parse(text) : null;
  if (!res.ok) {
    const msg = body?.error || body?.message || `HTTP ${res.status}`;
    throw new Error(msg);
  }
  return body;
}

export const api = {
  createOrder: (payload) =>
    request(`${ORDER_URL}/orders`, { method: 'POST', body: JSON.stringify(payload) }),

  getFullStatus: (orderId) =>
    request(`${ORDER_URL}/orders/${orderId}/full-status`),

  registerDriver: (driverId, lat, lng) =>
    request(`${ASSIGN_URL}/drivers/${driverId}/location`, {
      method: 'PATCH',
      body: JSON.stringify({ lat: Number(lat), lng: Number(lng), is_available: true }),
    }),

  advanceDelivery: (orderId, status) =>
    request(`${DELIVERY_URL}/deliveries/${orderId}/status`, {
      method: 'PATCH',
      body: JSON.stringify({ status }),
    }),
};
