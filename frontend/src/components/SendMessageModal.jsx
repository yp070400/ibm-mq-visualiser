import { useState, useRef, useEffect } from 'react'
import { api } from '../api/mqApi.js'

/**
 * Modal dialog for composing and sending a new MQ message.
 */
export function SendMessageModal({ qmName, queueName, onClose, onSent }) {
  const [body, setBody]       = useState('')
  const [format, setFormat]   = useState('MQSTR')
  const [correlId, setCorrelId] = useState('')
  const [loading, setLoading] = useState(false)
  const [error, setError]     = useState(null)
  const textareaRef = useRef(null)

  useEffect(() => { textareaRef.current?.focus() }, [])

  const handleSend = async () => {
    setLoading(true)
    setError(null)
    try {
      const result = await api.postMessage(qmName, queueName, {
        body,
        format,
        correlationId: correlId.trim() || undefined,
      })
      onSent(result.msgId)
    } catch (err) {
      setError(err.message)
      setLoading(false)
    }
  }

  const handleKeyDown = (e) => {
    if (e.key === 'Escape') onClose()
  }

  return (
    <div className="modal-overlay" onClick={onClose} onKeyDown={handleKeyDown}>
      <div className="modal modal-send" onClick={e => e.stopPropagation()}>
        <h3 className="modal-title">Send Message</h3>
        <p className="modal-subtitle mono">{qmName} / {queueName}</p>

        {error && <div className="modal-error">{error}</div>}

        <label className="modal-label">Message Body</label>
        <textarea
          ref={textareaRef}
          className="modal-textarea"
          rows={7}
          value={body}
          onChange={e => setBody(e.target.value)}
          placeholder="Enter message content…"
          disabled={loading}
        />

        <div className="modal-row">
          <label className="modal-label">Format</label>
          <select
            className="modal-select"
            value={format}
            onChange={e => setFormat(e.target.value)}
            disabled={loading}
          >
            <option value="MQSTR">MQSTR</option>
            <option value="MQHRF2">MQHRF2</option>
            <option value="MQRFH2">MQRFH2</option>
            <option value="MQNONE">MQNONE (none)</option>
          </select>
        </div>

        <div className="modal-row">
          <label className="modal-label">Correlation ID (hex, optional)</label>
          <input
            className="modal-input"
            value={correlId}
            onChange={e => setCorrelId(e.target.value)}
            placeholder="e.g. 414D51..."
            disabled={loading}
          />
        </div>

        <div className="modal-actions">
          <button className="btn-cancel" onClick={onClose} disabled={loading}>
            Cancel
          </button>
          <button
            className="btn-primary"
            onClick={handleSend}
            disabled={loading || !body.trim()}
          >
            {loading ? 'Sending…' : 'Send'}
          </button>
        </div>
      </div>
    </div>
  )
}
