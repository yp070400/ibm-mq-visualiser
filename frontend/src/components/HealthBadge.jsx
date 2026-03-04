/**
 * Color-coded badge for queue health or connection status.
 * No external library required — pure CSS classes defined in App.css.
 */
export function HealthBadge({ value }) {
  if (!value) return null
  const cls = `badge badge-${value.toLowerCase()}`
  return <span className={cls}>{value}</span>
}