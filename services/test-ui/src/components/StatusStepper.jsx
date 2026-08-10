const STEPS = [
  { label: 'Created',    match: (o) => o === 'PENDING' },
  { label: 'Accepted',   match: (o) => o === 'ACCEPTED' },
  { label: 'Assigned',   match: (o) => o === 'DRIVER_ASSIGNED' },
  { label: 'Picked Up',  match: (_, d) => d === 'PICKED_UP' },
  { label: 'In Transit', match: (_, d) => d === 'IN_TRANSIT' },
  { label: 'Delivered',  match: (o, d) => o === 'DELIVERED' || d === 'DELIVERED' },
];

function activeIndex(orderStatus, deliveryStatus) {
  for (let i = STEPS.length - 1; i >= 0; i--) {
    if (STEPS[i].match(orderStatus, deliveryStatus)) return i;
  }
  return 0;
}

export default function StatusStepper({ orderStatus, deliveryStatus }) {
  const active = activeIndex(orderStatus, deliveryStatus);
  return (
    <div className="stepper">
      {STEPS.map((step, i) => {
        const done    = i < active;
        const current = i === active;
        return (
          <div key={step.label} className="stepper-item">
            <div className={`stepper-circle${done ? ' done' : ''}${current ? ' active' : ''}`}>
              {done ? '✓' : i + 1}
            </div>
            <div className={`stepper-label${current ? ' active' : ''}`}>{step.label}</div>
            {i < STEPS.length - 1 && <div className={`stepper-line${done ? ' done' : ''}`} />}
          </div>
        );
      })}
    </div>
  );
}
