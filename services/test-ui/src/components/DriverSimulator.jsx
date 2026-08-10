import { useState } from 'react';
import { api } from '../api';

const DELIVERY_STEPS = ['PICKED_UP', 'IN_TRANSIT', 'DELIVERED'];

export default function DriverSimulator({ activeOrder }) {
  const [driverId, setDriverId] = useState('driver-sim-001');
  const [lat,      setLat]      = useState('37.771');
  const [lng,      setLng]      = useState('-122.411');
  const [regMsg,   setRegMsg]   = useState('');
  const [regErr,   setRegErr]   = useState('');

  const [advMsg,   setAdvMsg]   = useState('');
  const [advErr,   setAdvErr]   = useState('');
  const [loading,  setLoading]  = useState('');

  const registerDriver = async (e) => {
    e.preventDefault();
    setRegMsg(''); setRegErr('');
    try {
      await api.registerDriver(driverId, lat, lng);
      setRegMsg(`Driver ${driverId} registered at (${lat}, ${lng})`);
    } catch (err) {
      setRegErr(err.message);
    }
  };

  const advance = async (status) => {
    if (!activeOrder) return;
    setAdvMsg(''); setAdvErr(''); setLoading(status);
    try {
      const res = await api.advanceDelivery(activeOrder.orderId, status);
      setAdvMsg(`Delivery → ${res.status ?? status}`);
    } catch (err) {
      setAdvErr(err.message);
    } finally {
      setLoading('');
    }
  };

  return (
    <div className="card">
      <h2>Driver Simulator</h2>

      {/* Register driver */}
      <form onSubmit={registerDriver}>
        <h3>Register Fake Driver</h3>
        <div className="field-row">
          <label>Driver ID
            <input value={driverId} onChange={e => setDriverId(e.target.value)} required />
          </label>
          <label>Lat
            <input type="number" step="any" value={lat} onChange={e => setLat(e.target.value)} />
          </label>
          <label>Lng
            <input type="number" step="any" value={lng} onChange={e => setLng(e.target.value)} />
          </label>
        </div>
        {regMsg && <div className="success">{regMsg}</div>}
        {regErr && <div className="error">{regErr}</div>}
        <button className="btn-primary">Register Driver</button>
      </form>

      <hr />

      {/* Advance delivery */}
      <h3>Advance Delivery Status</h3>
      {!activeOrder
        ? <p className="muted">Select an order first.</p>
        : <>
            <p className="muted">
              Order: <span className="mono">{activeOrder.orderId.slice(0, 8)}…</span>
            </p>
            <div className="btn-group">
              {DELIVERY_STEPS.map(s => (
                <button
                  key={s}
                  className="btn-secondary"
                  disabled={loading === s}
                  onClick={() => advance(s)}
                >
                  {loading === s ? '…' : s.replace('_', ' ')}
                </button>
              ))}
            </div>
            {advMsg && <div className="success">{advMsg}</div>}
            {advErr && <div className="error">{advErr}</div>}
          </>
      }
    </div>
  );
}
