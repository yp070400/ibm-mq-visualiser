import { useState, useCallback } from 'react'
import { api } from './api/mqApi.js'
import { useAutoRefresh } from './hooks/useAutoRefresh.js'
import { Sidebar } from './components/Sidebar.jsx'
import { QueueTable } from './components/QueueTable.jsx'
import { Toast } from './components/Toast.jsx'
import { MessagesPanel } from './components/MessagesPanel.jsx'
import { SendMessageModal } from './components/SendMessageModal.jsx'
import { ConfirmPurgeModal } from './components/ConfirmPurgeModal.jsx'

const REFRESH_INTERVAL_MS = 30_000  // match backend polling interval

export default function App() {
  const [selectedQM,  setSelectedQM]  = useState(null)
  const [queueFilter, setQueueFilter] = useState('')

  // ── Modal / panel state ──────────────────────────────────────────────────────
  const [messagesPanel, setMessagesPanel] = useState(null) // { qmName, queueName }
  const [sendModal,     setSendModal]     = useState(null) // { qmName, queueName }
  const [purgeModal,    setPurgeModal]    = useState(null) // { qmName, queueName, count }
  const [toasts,        setToasts]        = useState([])

  // ── Toast helpers ────────────────────────────────────────────────────────────
  const addToast = useCallback((toast) => {
    const id = Date.now() + Math.random()
    setToasts(ts => [...ts, { ...toast, id }])
  }, [])

  const removeToast = useCallback((id) => {
    setToasts(ts => ts.filter(t => t.id !== id))
  }, [])

  // ── Queue manager list (auto-refreshes) ─────────────────────────────────────
  const {
    data: managers,
    loading: managersLoading,
    error: managersError,
    lastUpdated,
    refresh,
  } = useAutoRefresh(api.getQueueManagers, REFRESH_INTERVAL_MS)

  // ── Queue list for selected QM ───────────────────────────────────────────────
  const fetchQueues = useCallback(
    () => (selectedQM ? api.getQueues(selectedQM) : Promise.resolve(null)),
    [selectedQM]
  )
  const {
    data: queues,
    loading: queuesLoading,
    error: queuesError,
  } = useAutoRefresh(fetchQueues, REFRESH_INTERVAL_MS)

  // ── Selected QM summary ──────────────────────────────────────────────────────
  const selectedManager = managers?.find(m => m.name === selectedQM)

  const handleSelectQM = (name) => {
    setSelectedQM(name)
    setQueueFilter('')
    // Close any open panels for the previous QM
    setMessagesPanel(null)
    setSendModal(null)
    setPurgeModal(null)
  }

  // ── Action handlers (open modals / panels) ────────────────────────────────────
  const handleMessages = useCallback((queueName) => {
    setMessagesPanel({ qmName: selectedQM, queueName })
  }, [selectedQM])

  const handleSend = useCallback((queueName) => {
    setSendModal({ qmName: selectedQM, queueName })
  }, [selectedQM])

  const handlePurge = useCallback((queueName, count) => {
    setPurgeModal({ qmName: selectedQM, queueName, count })
  }, [selectedQM])

  // Called from MessagesPanel's "Purge All" button
  const handlePurgeFromPanel = useCallback((qmName, queueName, count) => {
    setPurgeModal({ qmName, queueName, count })
  }, [])

  // ── Purge completion ─────────────────────────────────────────────────────────
  const handlePurgeComplete = useCallback((result, error) => {
    setPurgeModal(null)
    setMessagesPanel(null) // queue is now empty — close the messages panel if open
    if (result) {
      addToast({
        type: 'success',
        message: `Purged ${result.messagesDeleted} message${result.messagesDeleted !== 1 ? 's' : ''} from ${result.queueName}`,
      })
    } else if (error) {
      addToast({ type: 'error', message: `Purge failed: ${error}` })
    }
  }, [addToast])

  // ── Send completion ──────────────────────────────────────────────────────────
  const handleMessageSent = useCallback((msgId) => {
    setSendModal(null)
    addToast({ type: 'success', message: `Message sent (ID: ${msgId.slice(0, 8)}…)` })
  }, [addToast])

  return (
    <div className="app-layout">
      {/* ── Top bar ── */}
      <header className="topbar">
        <span className="topbar-title">IBM MQ Monitor</span>
        <div className="topbar-right">
          {lastUpdated && (
            <span className="topbar-timestamp">
              Last refresh: {lastUpdated.toLocaleTimeString()}
            </span>
          )}
          <button className="btn-refresh" onClick={refresh} title="Refresh now">
            ↻ Refresh
          </button>
        </div>
      </header>

      <div className="app-body">
        {/* ── Sidebar ── */}
        <Sidebar
          managers={managers}
          selectedName={selectedQM}
          onSelect={handleSelectQM}
          loading={managersLoading}
        />

        {/* ── Main content ── */}
        <main className="main-content">
          {managersError && (
            <div className="error-banner">
              Cannot reach backend: {managersError}
            </div>
          )}

          {/* Summary strip for selected QM */}
          {selectedManager && (
            <div className="qm-header">
              <div className="qm-header-name">{selectedManager.name}</div>
              <div className="qm-header-meta">
                <span>{selectedManager.host}:{selectedManager.port}</span>
                <span>{selectedManager.totalQueues ?? 0} total queues</span>
                {selectedManager.criticalQueues > 0 && (
                  <span className="pill pill--critical">{selectedManager.criticalQueues} critical</span>
                )}
                {selectedManager.warningQueues > 0 && (
                  <span className="pill pill--warning">{selectedManager.warningQueues} warning</span>
                )}
                {selectedManager.collectionDurationMs && (
                  <span className="pill pill--info">
                    collected in {selectedManager.collectionDurationMs}ms
                  </span>
                )}
              </div>

              {/* Queue filter */}
              <input
                className="queue-filter"
                type="search"
                placeholder="Filter queues…"
                value={queueFilter}
                onChange={e => setQueueFilter(e.target.value)}
              />
            </div>
          )}

          {/* Queue table */}
          {!selectedQM ? (
            <div className="select-prompt">
              {managers?.length
                ? 'Select a queue manager from the sidebar'
                : managersLoading
                  ? 'Connecting to backend…'
                  : 'No queue managers configured'}
            </div>
          ) : (
            <QueueTable
              queues={queues}
              loading={queuesLoading}
              error={queuesError}
              filter={queueFilter}
              qmName={selectedQM}
              onMessages={handleMessages}
              onSend={handleSend}
              onPurge={handlePurge}
            />
          )}
        </main>
      </div>

      {/* ── Overlays / Modals ── */}
      {messagesPanel && (
        <MessagesPanel
          qmName={messagesPanel.qmName}
          queueName={messagesPanel.queueName}
          onClose={() => setMessagesPanel(null)}
          onToast={addToast}
          onPurge={handlePurgeFromPanel}
        />
      )}

      {sendModal && (
        <SendMessageModal
          qmName={sendModal.qmName}
          queueName={sendModal.queueName}
          onClose={() => setSendModal(null)}
          onSent={handleMessageSent}
        />
      )}

      {purgeModal && (
        <ConfirmPurgeModal
          qmName={purgeModal.qmName}
          queueName={purgeModal.queueName}
          count={purgeModal.count}
          onConfirm={handlePurgeComplete}
          onCancel={() => setPurgeModal(null)}
        />
      )}

      <Toast toasts={toasts} onRemove={removeToast} />
    </div>
  )
}
