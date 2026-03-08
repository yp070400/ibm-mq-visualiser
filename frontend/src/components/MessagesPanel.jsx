import { useState, useEffect, useCallback } from 'react'
import { api } from '../api/mqApi.js'

/**
 * Right-side slide-in drawer showing messages for a queue.
 * Supports browse, per-message delete, expand/collapse body, and purge trigger.
 */
export function MessagesPanel({ qmName, queueName, onClose, onToast, onPurge }) {
  const [messages, setMessages] = useState([])
  const [loading, setLoading]   = useState(true)
  const [expandedId, setExpandedId] = useState(null)
  const [deleting, setDeleting] = useState(null)

  const fetchMessages = useCallback(async () => {
    setLoading(true)
    try {
      const msgs = await api.browseMessages(qmName, queueName)
      setMessages(msgs)
    } catch (err) {
      onToast({ type: 'error', message: `Failed to load messages: ${err.message}` })
    } finally {
      setLoading(false)
    }
  }, [qmName, queueName, onToast])

  useEffect(() => { fetchMessages() }, [fetchMessages])

  const handleDelete = async (msgId) => {
    setDeleting(msgId)
    try {
      await api.deleteMessage(qmName, queueName, msgId)
      setMessages(msgs => msgs.filter(m => m.msgId !== msgId))
      onToast({ type: 'success', message: 'Message deleted' })
    } catch (err) {
      onToast({ type: 'error', message: `Delete failed: ${err.message}` })
    } finally {
      setDeleting(null)
    }
  }

  const toggleExpand = (msgId) => {
    setExpandedId(prev => prev === msgId ? null : msgId)
  }

  return (
    <>
      <div className="messages-panel-overlay" onClick={onClose} />
      <div className="messages-panel">
        <div className="panel-header">
          <div className="panel-title-row">
            <span className="panel-queue-name mono">{queueName}</span>
            <span className="panel-msg-count">{messages.length} message{messages.length !== 1 ? 's' : ''}</span>
          </div>
          <div className="panel-header-actions">
            <button className="btn-sm" onClick={fetchMessages} disabled={loading}>
              ↻ Refresh
            </button>
            <button
              className="btn-sm btn-sm-danger"
              onClick={() => onPurge(qmName, queueName, messages.length)}
              disabled={loading || messages.length === 0}
            >
              Purge All
            </button>
            <button className="panel-close-btn" onClick={onClose} aria-label="Close">×</button>
          </div>
        </div>

        <div className="panel-body">
          {loading ? (
            <div className="panel-state">Loading…</div>
          ) : messages.length === 0 ? (
            <div className="panel-state">No messages</div>
          ) : (
            messages.map(msg => (
              <div key={msg.msgId} className="msg-row">
                <div className="msg-header-row">
                  <div className="msg-meta">
                    <span className="msg-id mono" title={msg.msgId}>
                      {msg.msgId.slice(0, 16)}…
                    </span>
                    <span className="msg-time">{msg.putTime}</span>
                    <span className="msg-fmt">{msg.format}</span>
                    <span className="msg-len">{msg.length}B</span>
                  </div>
                  <div className="msg-controls">
                    <button
                      className="msg-btn msg-expand-btn"
                      onClick={() => toggleExpand(msg.msgId)}
                      title={expandedId === msg.msgId ? 'Collapse' : 'Expand'}
                    >
                      {expandedId === msg.msgId ? '▲' : '▼'}
                    </button>
                    <button
                      className="msg-btn msg-delete-btn"
                      onClick={() => handleDelete(msg.msgId)}
                      disabled={deleting === msg.msgId}
                      title="Delete message"
                    >
                      {deleting === msg.msgId ? '…' : '🗑'}
                    </button>
                  </div>
                </div>

                {expandedId !== msg.msgId && (
                  <div className="msg-body-preview">
                    {msg.body ? msg.body.slice(0, 120) + (msg.body.length > 120 ? '…' : '') : <em>empty</em>}
                  </div>
                )}
                {expandedId === msg.msgId && (
                  <div className="msg-body-full">
                    {msg.body || <em>empty</em>}
                  </div>
                )}
              </div>
            ))
          )}
        </div>
      </div>
    </>
  )
}
