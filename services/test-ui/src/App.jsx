import { useState, useEffect, useRef } from 'react';
import { api } from './api.js';
import OrderForm from './components/OrderForm.jsx';
import OrderDetail from './components/OrderDetail.jsx';
import DriverSimulator from './components/DriverSimulator.jsx';
import './App.css';

// ── Top-level polling hook ──────────────────────────────────────────────────
// Keeps a live fullStatus for the active order so both OrderDetail AND
// DriverSimulator can read the same data without duplicating the poll.
function useFullStatus(orderId) {
  const [fullStatus, setFullStatus] = useState(null);
  const [pollError, setPollError] = useState('');
  const timerRef = useRef(null);

  useEffect(() => {
    if (!orderId) { setFullStatus(null); setPollError(''); return; }

    setFullStatus(null);
    setPollError('');

    const poll = async () => {
      try {
        const data = await api.getFullStatus(orderId);
        setFullStatus(data);
        setPollError('');
        // Stop once fully delivered
        if (data.orderStatus === 'DELIVERED' || data.delivery?.status === 'DELIVERED') {
          clearInterval(timerRef.current);
        }
      } catch (err) {
        setPollError(err.message);
      }
    };

    poll();
    timerRef.current = setInterval(poll, 2000);
    return () => clearInterval(timerRef.current);
  }, [orderId]);

  return { fullStatus, pollError };
}

// ── App ─────────────────────────────────────────────────────────────────────
function App() {
  const [orders, setOrders] = useState([]);
  const [activeOrder, setActiveOrder] = useState(null);

  const { fullStatus, pollError } = useFullStatus(activeOrder?.orderId);

  const handleOrderCreated = (newOrder) => {
    setOrders(prev => [newOrder, ...prev].slice(0, 10));
    setActiveOrder(newOrder);
  };

  return (
    <div className="app-container">
      <header className="app-header">
        <h1>Food Delivery Test Harness</h1>
        <p className="subtitle">Manual verification tool for microservices backend</p>
      </header>

      <main className="dashboard-grid">
        {/* Left Column */}
        <section className="column-left">
          <OrderForm onCreated={handleOrderCreated} />
          <DriverSimulator activeOrder={activeOrder} fullStatus={fullStatus} />
        </section>

        {/* Right Column */}
        <section className="column-right">
          <OrderDetail
            order={activeOrder}
            fullStatus={fullStatus}
            pollError={pollError}
          />

          {/* Session Orders List */}
          <div className="card session-orders">
            <h2>Session Orders (Last 10)</h2>
            {orders.length === 0 ? (
              <p className="muted text-center">No orders created in this session yet.</p>
            ) : (
              <ul className="orders-list">
                {orders.map((o) => {
                  const isActive = activeOrder?.orderId === o.orderId;
                  const price = ((o.totalCents || 0) / 100).toFixed(2);
                  return (
                    <li
                      key={o.orderId}
                      className={`order-item-btn ${isActive ? 'active' : ''}`}
                      onClick={() => setActiveOrder(o)}
                    >
                      <div className="order-item-header">
                        <span className="mono font-bold">{o.orderId.slice(0, 8)}…</span>
                        <span className={`status-badge ${o.status?.toLowerCase() || 'pending'}`}>
                          {o.status || 'PENDING'}
                        </span>
                      </div>
                      <div className="order-item-meta">
                        <span>Total: ${price}</span>
                        <span className="date-text">
                          {o.createdAt
                            ? (() => {
                              const d = new Date(o.createdAt);
                              return isNaN(d) ? '—' : d.toLocaleTimeString();
                            })()
                            : '—'}
                        </span>
                      </div>
                    </li>
                  );
                })}
              </ul>
            )}
          </div>
        </section>
      </main>
    </div>
  );
}

export default App;
