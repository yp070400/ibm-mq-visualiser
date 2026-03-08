import { useState } from 'react'
import { api } from '../api/mqApi.js'

/**
 * Modal to confirm and execute a queue purge.
 * Calls the API itself and passes the result (or error) to onConfirm.
 */
export function ConfirmPurgeModal({ qmName, queueName, count, onConfirm, onCancel }) {
  const [loading, setLoading] = useState(false)

  const handleConfirm = async () => {
    setLoading(true)
    try {
      const result = await api.purgeQueue(qmName, queueName)
      onConfirm(result, null)
    } catch (err) {
      onConfirm(null, err.message)
    }
  }

  return (
    <div className="modal-overlay" onClick={onCancel}>
      <div className="modal" onClick={e => e.stopPropagation()}>
        <h3 className="modal-title">Purge Queue</h3>
        <p className="modal-body">
          Remove{' '}
          {count > 0 ? <strong>{count} message{count !== 1 ? 's' : ''}</strong> : 'all messages'}{' '}
          from <strong className="mono">{queueName}</strong>?
        </p>
        <p className="modal-warning">This action cannot be undone.</p>
        <div className="modal-actions">
          <button className="btn-cancel" onClick={onCancel} disabled={loading}>
            Cancel
          </button>
          <button className="btn-danger" onClick={handleConfirm} disabled={loading}>
            {loading ? 'Purging…' : 'Purge All'}
          </button>
        </div>
      </div>
    </div>
  )
}
