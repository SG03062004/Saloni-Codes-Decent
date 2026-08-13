import { useState } from 'react';
import { api } from '../api';

const DEFAULT_ITEM = { menuItemId: 'item-burger', quantity: 1, unitPriceCents: 1000 };

export default function OrderForm({ onCreated }) {
  const [form, setForm] = useState({
    customerId:   'cust-001',
    restaurantId: 'rest-001',
    items:        [{ ...DEFAULT_ITEM }],
    street:       '1 Market St',
    city:         'San Francisco',
    lat:          '37.7749',
    lng:          '-122.4194',
  });
  const [loading, setLoading] = useState(false);
  const [error,   setError]   = useState('');

  const set = (k, v) => setForm(f => ({ ...f, [k]: v }));

  const setItem = (i, k, v) =>
    setForm(f => {
      const items = [...f.items];
      items[i] = { ...items[i], [k]: v };
      return { ...f, items };
    });

  const addItem    = () => setForm(f => ({ ...f, items: [...f.items, { ...DEFAULT_ITEM }] }));
  const removeItem = (i) => setForm(f => ({ ...f, items: f.items.filter((_, j) => j !== i) }));

  const submit = async (e) => {
    e.preventDefault();
    setError('');
    setLoading(true);
    try {
      const payload = {
        customerId:   form.customerId,
        restaurantId: form.restaurantId,
        items: form.items.map(it => ({
          menuItemId:     it.menuItemId,
          quantity:       Number(it.quantity),
          unitPriceCents: Number(it.unitPriceCents),
        })),
        deliveryAddress: {
          lat:    Number(form.lat),
          lng:    Number(form.lng),
          street: form.street,
          city:   form.city,
        },
      };
      const order = await api.createOrder(payload);
      onCreated(order);
    } catch (err) {
      setError(err.message);
    } finally {
      setLoading(false);
    }
  };

  return (
    <form id="create-order-form" className="card" onSubmit={submit}>
      <h2>Create Order</h2>

      <div className="field-row">
        <label htmlFor="order-customer-id">Customer ID
          <input
            id="order-customer-id"
            name="customerId"
            value={form.customerId}
            onChange={e => set('customerId', e.target.value)}
            autoComplete="off"
            required
          />
        </label>
        <label htmlFor="order-restaurant-id">Restaurant ID
          <input
            id="order-restaurant-id"
            name="restaurantId"
            value={form.restaurantId}
            onChange={e => set('restaurantId', e.target.value)}
            placeholder="paste restaurant UUID"
            autoComplete="off"
            required
          />
        </label>
      </div>

      <fieldset>
        <legend>Items</legend>
        {form.items.map((item, i) => (
          <div key={i} className="item-row">
            <label htmlFor={`item-${i}-id`}>Menu Item ID
              <input
                id={`item-${i}-id`}
                name={`item-${i}-menuItemId`}
                value={item.menuItemId}
                onChange={e => setItem(i, 'menuItemId', e.target.value)}
                placeholder="item-burger"
                autoComplete="off"
                required
              />
            </label>
            <label htmlFor={`item-${i}-qty`}>Qty
              <input
                id={`item-${i}-qty`}
                name={`item-${i}-quantity`}
                type="number"
                min="1"
                value={item.quantity}
                onChange={e => setItem(i, 'quantity', e.target.value)}
              />
            </label>
            <label htmlFor={`item-${i}-price`}>Price (cents)
              <input
                id={`item-${i}-price`}
                name={`item-${i}-unitPriceCents`}
                type="number"
                min="0"
                value={item.unitPriceCents}
                onChange={e => setItem(i, 'unitPriceCents', e.target.value)}
              />
            </label>
            {form.items.length > 1 &&
              <button type="button" className="btn-ghost" onClick={() => removeItem(i)}>✕</button>}
          </div>
        ))}
        <button type="button" className="btn-ghost" onClick={addItem}>+ Add item</button>
      </fieldset>

      <fieldset>
        <legend>Delivery Address</legend>
        <div className="field-row">
          <label htmlFor="order-street">Street
            <input
              id="order-street"
              name="street"
              value={form.street}
              onChange={e => set('street', e.target.value)}
              autoComplete="street-address"
              required
            />
          </label>
          <label htmlFor="order-city">City
            <input
              id="order-city"
              name="city"
              value={form.city}
              onChange={e => set('city', e.target.value)}
              autoComplete="address-level2"
              required
            />
          </label>
        </div>
        <div className="field-row">
          <label htmlFor="order-lat">Lat
            <input
              id="order-lat"
              name="lat"
              type="number"
              step="any"
              value={form.lat}
              onChange={e => set('lat', e.target.value)}
              autoComplete="off"
              required
            />
          </label>
          <label htmlFor="order-lng">Lng
            <input
              id="order-lng"
              name="lng"
              type="number"
              step="any"
              value={form.lng}
              onChange={e => set('lng', e.target.value)}
              autoComplete="off"
              required
            />
          </label>
        </div>
      </fieldset>

      {error && <div className="error">{error}</div>}
      <button id="create-order-submit" className="btn-primary" disabled={loading}>
        {loading ? 'Creating…' : 'Create Order'}
      </button>
    </form>
  );
}
