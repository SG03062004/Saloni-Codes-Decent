import { useState, useEffect, useRef } from 'react';
import { api } from '../api';
import StatusStepper from './StatusStepper';

export default function OrderDetail({ order }) {
  const [fullStatus, setFullStatus] = useState(null);
  const [error,      setError]      = useState('');
  const intervalRef = useRef(null);

  const orderId = order?.orderId;

  useEffect(() => {
    if (!orderId) return;
    setFullStatus(null);
    setError('');

    const poll = async () => {
      try {
        const data = await api.getFullStatus(orderId);
        setFullStatus(data);
        setError('');
        // Stop polling once delivered
        const delivered =
          data.orderStatus === 'DELIVERED' ||
          data.delivery?.status === 'DELIVERED';
        if (delivered) clearInterval(intervalRef.current);
      } catch (err) {
        setError(err.message);
      }
    };

    poll();
    intervalRef.current = setInterval(poll, 2000);
    return () => clearInterval(intervalRef.current);
  }, [orderId]);

  if (!order) return <div className="card muted">Select or create an order.</div>;

  const orderStatus   = fullStatus?.orderStatus   ?? order.status ?? 'PENDING';
  const deliveryStatus = fullStatus?.delivery?.status;

  return (
    <div className="card">
      <h2>Order <span className="mono">{orderId.slice(0, 8)}…</span></h2>
      <p className="muted">Total: <strong>${((fullStatus?.totalCents ?? order.totalCents) / 100).toFixed(2)}</strong></p>

      <StatusStepper orderStatus={orderStatus} deliveryStatus={deliveryStatus} />

      {error && <div className="error">{error}</div>}

      <details className="raw-json">
        <summary>Raw full-status JSON</summary>
        <pre>{fullStatus ? JSON.stringify(fullStatus, null, 2) : 'Loading…'}</pre>
      </details>
    </div>
  );
}
