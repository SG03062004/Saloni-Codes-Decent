import { useState } from 'react';
import { api } from '../api.js';

const DELIVERY_STEPS = ['PICKED_UP', 'IN_TRANSIT', 'DELIVERED'];

export default function DriverSimulator({ activeOrder, fullStatus }) {
  const [driverId, setDriverId] = useState('driver-sim-001');
  const [lat,      setLat]      = useState('37.771');
  const [lng,      setLng]      = useState('-122.411');
  const [regMsg,   setRegMsg]   = useState('');
  const [regErr,   setRegErr]   = useState('');
  const [advMsg,   setAdvMsg]   = useState('');
  const [advErr,   setAdvErr]   = useState('');
  const [loading,  setLoading]  = useState('');

  const deliveryStatus = fullStatus?.delivery?.status;

  // ── Register driver ────────────────────────────────────────────────────────
  const registerDriver = async (e) => {
    e.preventDefault();
    setRegMsg(''); setRegErr('');
    try {
      await api.registerDriver(driverId, lat, lng);
      setRegMsg(`✓ Driver ${driverId} registered at (${lat}, ${lng})`);
    } catch (err) {
      setRegErr(err.message);
    }
  };

  // ── Advance delivery status (no guard — let backend decide) ────────────────
  const advance = async (status) => {
    if (!activeOrder) return;
    setAdvMsg(''); setAdvErr(''); setLoading(status);
    try {
      const res = await api.advanceDelivery(activeOrder.orderId, status);
      setAdvMsg(`✓ Delivery → ${res.status ?? status}`);
    } catch (err) {
      setAdvErr(err.message);
    } finally {
      setLoading('');
    }
  };

  return (
    <div className="card">
      <h2>Driver Simulator</h2>

      {/* ── Register driver ── */}
      <form id="register-driver-form" onSubmit={registerDriver}>
        <h3>Register Fake Driver</h3>
        <div className="field-row">
          <label htmlFor="driver-id">Driver ID
            <input
              id="driver-id"
              name="driverId"
              value={driverId}
              onChange={e => setDriverId(e.target.value)}
              autoComplete="off"
              required
            />
          </label>
          <label htmlFor="driver-lat">Lat
            <input
              id="driver-lat"
              name="driverLat"
              type="number"
              step="any"
              value={lat}
              onChange={e => setLat(e.target.value)}
              autoComplete="off"
            />
          </label>
          <label htmlFor="driver-lng">Lng
            <input
              id="driver-lng"
              name="driverLng"
              type="number"
              step="any"
              value={lng}
              onChange={e => setLng(e.target.value)}
              autoComplete="off"
            />
          </label>
        </div>
        {regMsg && <div className="success">{regMsg}</div>}
        {regErr && <div className="error"><strong>Error:</strong> {regErr}</div>}
        <button id="register-driver-submit" className="btn-primary">Register Driver</button>
      </form>

      <hr />

      {/* ── Advance delivery status ── */}
      <h3>Advance Delivery Status</h3>

      {!activeOrder ? (
        <p className="muted">Select an order first.</p>
      ) : (
        <>
          <p className="muted">
            Order: <span className="mono">{activeOrder.orderId.slice(0, 8)}…</span>
            {deliveryStatus && (
              <span> · Current: <strong>{deliveryStatus}</strong></span>
            )}
          </p>

          {/* Always show advance buttons — let the backend return the error */}
          <div className="btn-group">
            {DELIVERY_STEPS.map(s => (
              <button
                key={s}
                id={`advance-${s.toLowerCase()}`}
                className="btn-secondary"
                disabled={loading === s}
                onClick={() => advance(s)}
              >
                {loading === s ? '…' : s.replace(/_/g, ' ')}
              </button>
            ))}
          </div>

          {advMsg && <div className="success">{advMsg}</div>}
          {advErr && (
            <div className="error">
              <strong>Error:</strong> {advErr}
            </div>
          )}
        </>
      )}
    </div>
  );
}
