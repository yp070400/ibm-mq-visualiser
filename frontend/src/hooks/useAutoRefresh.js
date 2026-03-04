import { useState, useEffect, useCallback, useRef } from 'react'

/**
 * Polls a fetch function on a fixed interval.
 *
 * @param {Function} fetchFn  - async function that returns data
 * @param {number}   interval - polling interval in milliseconds
 * @returns {{ data, loading, error, lastUpdated, refresh }}
 */
export function useAutoRefresh(fetchFn, interval = 30_000) {
  const [data,        setData]        = useState(null)
  const [loading,     setLoading]     = useState(true)
  const [error,       setError]       = useState(null)
  const [lastUpdated, setLastUpdated] = useState(null)

  // Keep a stable ref to fetchFn so the interval callback doesn't stale-close
  const fetchRef = useRef(fetchFn)
  useEffect(() => { fetchRef.current = fetchFn }, [fetchFn])

  const run = useCallback(async () => {
    try {
      const result = await fetchRef.current()
      setData(result)
      setError(null)
      setLastUpdated(new Date())
    } catch (e) {
      setError(e.message)
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    run()
    const id = setInterval(run, interval)
    return () => clearInterval(id)
  }, [run, interval])

  return { data, loading, error, lastUpdated, refresh: run }
}