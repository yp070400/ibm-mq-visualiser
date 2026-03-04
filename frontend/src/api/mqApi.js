const BASE = '/api'

async function request(path) {
  const res = await fetch(BASE + path)
  if (!res.ok) {
    const text = await res.text().catch(() => res.statusText)
    throw new Error(`HTTP ${res.status}: ${text}`)
  }
  return res.json()
}

export const api = {
  /** Fetch all queue managers with summary stats. */
  getQueueManagers:  ()           => request('/queue-managers'),

  /** Fetch full stats (with queue list) for one queue manager. */
  getQueueManager:   (name)       => request(`/queue-managers/${encodeURIComponent(name)}`),

  /** Fetch the queue list for one queue manager (sorted by health server-side). */
  getQueues:         (qmName)     => request(`/queue-managers/${encodeURIComponent(qmName)}/queues`),

  /** Fetch a single queue's stats. */
  getQueue:          (qm, queue)  => request(`/queues/${encodeURIComponent(qm)}/${encodeURIComponent(queue)}`),

  /** Application health summary. */
  getHealth:         ()           => request('/health'),
}