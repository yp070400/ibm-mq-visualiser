const BASE = '/api'

async function request(path) {
  const res = await fetch(BASE + path)
  if (!res.ok) {
    const text = await res.text().catch(() => res.statusText)
    throw new Error(`HTTP ${res.status}: ${text}`)
  }
  return res.json()
}

async function post(path, body) {
  const res = await fetch(BASE + path, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  })
  if (!res.ok) {
    const text = await res.text().catch(() => res.statusText)
    throw new Error(`HTTP ${res.status}: ${text}`)
  }
  return res.json()
}

async function del(path) {
  const res = await fetch(BASE + path, { method: 'DELETE' })
  if (!res.ok) {
    const text = await res.text().catch(() => res.statusText)
    throw new Error(`HTTP ${res.status}: ${text}`)
  }
  // 204 No Content returns no body; 200 returns JSON
  const ct = res.headers.get('content-type') || ''
  return ct.includes('application/json') ? res.json() : null
}

const enc = encodeURIComponent

export const api = {
  /** Fetch all queue managers with summary stats. */
  getQueueManagers:  ()           => request('/queue-managers'),

  /** Fetch full stats (with queue list) for one queue manager. */
  getQueueManager:   (name)       => request(`/queue-managers/${enc(name)}`),

  /** Fetch the queue list for one queue manager (sorted by health server-side). */
  getQueues:         (qmName)     => request(`/queue-managers/${enc(qmName)}/queues`),

  /** Fetch a single queue's stats. */
  getQueue:          (qm, queue)  => request(`/queues/${enc(qm)}/${enc(queue)}`),

  /** Application health summary. */
  getHealth:         ()           => request('/health'),

  /** Browse messages (non-destructive) — returns up to `limit` MessageDto objects. */
  browseMessages:    (qm, q, limit = 50) =>
    request(`/queue-managers/${enc(qm)}/queues/${enc(q)}/messages?limit=${limit}`),

  /** Put a new message onto the queue — returns { msgId: "..." }. */
  postMessage:       (qm, q, body) =>
    post(`/queue-managers/${enc(qm)}/queues/${enc(q)}/messages`, body),

  /** Delete a single message by its hex message ID. */
  deleteMessage:     (qm, q, id)   =>
    del(`/queue-managers/${enc(qm)}/queues/${enc(q)}/messages/${enc(id)}`),

  /** Purge all messages (PCF CLEAR_Q) — returns PurgeResult JSON. */
  purgeQueue:        (qm, q)       =>
    del(`/queue-managers/${enc(qm)}/queues/${enc(q)}/messages`),
}
