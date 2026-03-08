import { useState, useMemo } from 'react'
import { HealthBadge } from './HealthBadge.jsx'
import { DepthBar } from './DepthBar.jsx'

const COLUMNS = [
  { key: 'queueName',      label: 'Queue Name',   sortable: true  },
  { key: 'health',         label: 'Health',        sortable: true  },
  { key: 'currentDepth',   label: 'Depth',         sortable: true  },
  { key: 'depthPercent',   label: 'Fill %',        sortable: true  },
  { key: 'maxDepth',       label: 'Max Depth',     sortable: true  },
  { key: 'openInputCount', label: 'Open In',       sortable: true  },
  { key: 'openOutputCount',label: 'Open Out',      sortable: true  },
  { key: 'queueType',      label: 'Type',          sortable: false },
  { key: 'inhibitGet',     label: 'Inh Get',       sortable: false },
  { key: 'inhibitPut',     label: 'Inh Put',       sortable: false },
]

const HEALTH_ORDER = { CRITICAL: 0, WARNING: 1, UNKNOWN: 2, NORMAL: 3 }

/**
 * Sortable table showing all queues for a selected Queue Manager.
 * Default sort: health (CRITICAL first), then depth% descending — matches server default.
 *
 * Action callbacks (onMessages, onSend, onPurge) open modals/panels in App.jsx.
 */
export function QueueTable({ queues, loading, error, filter, qmName, onMessages, onSend, onPurge }) {
  const [sortKey, setSortKey]   = useState('health')
  const [sortDir, setSortDir]   = useState('asc')

  const handleSort = (key) => {
    if (sortKey === key) {
      setSortDir(d => d === 'asc' ? 'desc' : 'asc')
    } else {
      setSortKey(key)
      setSortDir('asc')
    }
  }

  const filtered = useMemo(() => {
    if (!queues) return []
    const q = (filter || '').toLowerCase()
    return q ? queues.filter(r => r.queueName.toLowerCase().includes(q)) : queues
  }, [queues, filter])

  const sorted = useMemo(() => {
    const copy = [...filtered]
    copy.sort((a, b) => {
      let av = a[sortKey], bv = b[sortKey]
      if (sortKey === 'health') {
        av = HEALTH_ORDER[av] ?? 99
        bv = HEALTH_ORDER[bv] ?? 99
      }
      if (av < bv) return sortDir === 'asc' ? -1 :  1
      if (av > bv) return sortDir === 'asc' ?  1 : -1
      return 0
    })
    return copy
  }, [filtered, sortKey, sortDir])

  if (loading) return <div className="table-placeholder">Collecting metrics...</div>
  if (error)   return <div className="table-error">Error: {error}</div>
  if (!queues) return <div className="table-placeholder">Select a queue manager</div>

  return (
    <div className="table-wrapper">
      <table className="queue-table">
        <thead>
          <tr>
            {COLUMNS.map(col => (
              <th
                key={col.key}
                className={col.sortable ? 'sortable' : ''}
                onClick={col.sortable ? () => handleSort(col.key) : undefined}
              >
                {col.label}
                {col.sortable && sortKey === col.key && (
                  <span className="sort-arrow">{sortDir === 'asc' ? ' ▲' : ' ▼'}</span>
                )}
              </th>
            ))}
            <th>Depth Bar</th>
            <th>Actions</th>
          </tr>
        </thead>
        <tbody>
          {sorted.length === 0 && (
            <tr><td colSpan={COLUMNS.length + 2} className="no-data">No queues found</td></tr>
          )}
          {sorted.map(q => (
            <tr key={q.queueName} className={`row-health-${q.health?.toLowerCase()}`}>
              <td className="queue-name">{q.queueName}</td>
              <td><HealthBadge value={q.health} /></td>
              <td className="num">{q.currentDepth.toLocaleString()}</td>
              <td className="num">{q.depthPercent?.toFixed(1)}%</td>
              <td className="num">{q.maxDepth.toLocaleString()}</td>
              <td className="num">{q.openInputCount}</td>
              <td className="num">{q.openOutputCount}</td>
              <td>{q.queueType}</td>
              <td className="center">{q.inhibitGet ? 'Y' : '—'}</td>
              <td className="center">{q.inhibitPut ? 'Y' : '—'}</td>
              <td className="depth-bar-cell">
                <DepthBar
                  current={q.currentDepth}
                  max={q.maxDepth}
                  percent={q.depthPercent}
                  health={q.health}
                />
              </td>
              <td className="action-cell">
                <button
                  className="action-btn"
                  title="Browse messages"
                  onClick={() => onMessages?.(q.queueName)}
                  disabled={q.inhibitGet}
                >
                  📨
                </button>
                <button
                  className="action-btn"
                  title="Send message"
                  onClick={() => onSend?.(q.queueName)}
                  disabled={q.inhibitPut}
                >
                  ✉️
                </button>
                <button
                  className="action-btn action-btn-danger"
                  title="Purge queue"
                  onClick={() => onPurge?.(q.queueName, q.currentDepth)}
                >
                  🗑️
                </button>
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  )
}
