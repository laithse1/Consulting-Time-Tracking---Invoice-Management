export function Stat({
  label,
  value,
  suffix,
  detail,
}: {
  label: string;
  value: string;
  suffix?: string;
  detail: string;
}) {
  return (
    <div className="stat">
      <span className="muted small">{label}</span>
      <strong>
        {value}
        <small>{suffix}</small>
      </strong>
      <span className="muted small">{detail}</span>
    </div>
  );
}
