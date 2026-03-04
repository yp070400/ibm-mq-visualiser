import { HealthBadge } from './HealthBadge.jsx'

/**
 * Left panel listing all queue managers.
 * Clicking a row selects it and loads its queue list.
 */
export function Sidebar({ managers, selectedName, onSelect, loading }) {
  if (loading && !managers?.length) {
    return <aside className="sidebar"><div className="sidebar-loading">Loading...</div></aside>
  }

  return (
    <aside className="sidebar">
      <div className="sidebar-header">Queue Managers</div>

      {managers?.map(qm => {
        const isSelected = qm.name === selectedName
        const hasAlert = qm.criticalQueues > 0 || qm.warningQueues > 0

        return (
          <button
            key={qm.name}
            className={`sidebar-item ${isSelected ? 'sidebar-item--selected' : ''}`}
            onClick={() => onSelect(qm.name)}
          >
            <div className="sidebar-item-name">
              {qm.name}
              {hasAlert && <span className="sidebar-alert-dot" title="Has alerts" />}
            </div>
            <div className="sidebar-item-meta">
              <HealthBadge value={qm.connectionStatus} />
              <span className="sidebar-queue-count">
                {qm.totalQueues ?? '—'} queues
              </span>
            </div>
            {qm.connectionStatus === 'CONNECTED' && (
              <div className="sidebar-item-summary">
                {qm.criticalQueues > 0 && (
                  <span className="sidebar-count sidebar-count--critical">
                    {qm.criticalQueues} critical
                  </span>
                )}
                {qm.warningQueues > 0 && (
                  <span className="sidebar-count sidebar-count--warning">
                    {qm.warningQueues} warning
                  </span>
                )}
                {!hasAlert && (
                  <span className="sidebar-count sidebar-count--ok">All OK</span>
                )}
              </div>
            )}
          </button>
        )
      })}
    </aside>
  )
}