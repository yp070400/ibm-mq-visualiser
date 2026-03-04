/**
 * Horizontal progress bar visualising queue depth as a percentage of max.
 * Colour transitions: green → amber → red matching health thresholds.
 */
export function DepthBar({ current, max, percent, health }) {
  const clamp = Math.min(Math.max(percent || 0, 0), 100)
  const barClass = `depth-bar-fill depth-bar-fill--${(health || 'normal').toLowerCase()}`

  return (
    <div className="depth-bar" title={`${current} / ${max} (${clamp.toFixed(1)}%)`}>
      <div className={barClass} style={{ width: `${clamp}%` }} />
    </div>
  )
}