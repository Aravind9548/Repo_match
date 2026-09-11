export function ScoreBar({ value, label }: { value: number; label: string }) {
  const tone = value >= 75 ? "bg-linear-to-r from-blue to-accent" : value >= 50 ? "bg-amber" : "bg-muted";
  return (
    <div
      role="meter"
      aria-label={label}
      aria-valuenow={value}
      aria-valuemin={0}
      aria-valuemax={100}
      className="h-1.5 w-full overflow-hidden rounded-full bg-sunken"
    >
      <div className={`h-full rounded-full ${tone}`} style={{ width: `${Math.max(0, Math.min(100, value))}%` }} />
    </div>
  );
}
