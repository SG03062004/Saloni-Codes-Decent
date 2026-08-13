import { useState } from 'react';
import StatusStepper from './StatusStepper.jsx';
import { api } from '../api.js';

export default function OrderDetail({ order, fullStatus, pollError }) {
  const [diagResult, setDiagResult] = useState(null);
  const [diagLoading, setDiagLoading] = useState('');

  if (!order) {
    return <div className="card muted">Select or create an order.</div>;
  }

  const orderId        = order.orderId;
  const orderStatus    = fullStatus?.orderStatus   ?? order.status ?? 'PENDING';
  const deliveryStatus = fullStatus?.delivery?.status;
  const totalCents     = fullStatus?.totalCents    ?? order.totalCents ?? 0;

  const checkOrderDirect = async () => {
    setDiagLoading('order');
    try {
      const data = await api.getOrder(orderId);
      setDiagResult({ source: 'Order Service (GET /orders/' + orderId.slice(0, 8) + '...)', data });
    } catch (err) {
      setDiagResult({ source: 'Order Service (GET /orders/' + orderId.slice(0, 8) + '...)', error: err.message });
    } finally {
      setDiagLoading('');
    }
  };

  const checkDeliveryDirect = async () => {
    setDiagLoading('delivery');
    try {
      const data = await api.getDeliveryByOrder(orderId);
      setDiagResult({ source: 'Delivery Service (GET /deliveries/by-order/' + orderId.slice(0, 8) + '...)', data });
    } catch (err) {
      setDiagResult({ source: 'Delivery Service (GET /deliveries/by-order/' + orderId.slice(0, 8) + '...)', error: err.message });
    } finally {
      setDiagLoading('');
    }
  };

  return (
    <div className="card">
      <h2>Order <span className="mono">{orderId.slice(0, 8)}…</span></h2>
      <p className="muted">
        Total: <strong>${(totalCents / 100).toFixed(2)}</strong>
        {fullStatus?.delivery?.etaMinutes != null && (
          <span> · ETA <strong>{fullStatus.delivery.etaMinutes} min</strong></span>
        )}
      </p>

      <StatusStepper orderStatus={orderStatus} deliveryStatus={deliveryStatus} />

      {pollError && <div className="error">{pollError}</div>}

      <div className="diagnostics-section">
        <h3>Direct Service Diagnostics</h3>
        <div className="btn-group">
          <button
            className="btn-secondary"
            disabled={diagLoading === 'order'}
            onClick={checkOrderDirect}
          >
            {diagLoading === 'order' ? 'Checking...' : 'Check Order Service Direct'}
          </button>
          <button
            className="btn-secondary"
            disabled={diagLoading === 'delivery'}
            onClick={checkDeliveryDirect}
          >
            {diagLoading === 'delivery' ? 'Checking...' : 'Check Delivery Service Direct'}
          </button>
        </div>

        {diagResult && (
          <div className="diag-output">
            <strong>{diagResult.source}:</strong>
            {diagResult.error ? (
              <div className="error" style={{ marginTop: '6px' }}>{diagResult.error}</div>
            ) : (
              <pre>{JSON.stringify(diagResult.data, null, 2)}</pre>
            )}
          </div>
        )}
      </div>

      <details className="raw-json">
        <summary>Raw full-status JSON</summary>
        <pre>{fullStatus ? JSON.stringify(fullStatus, null, 2) : 'Loading…'}</pre>
      </details>
    </div>
  );
}

