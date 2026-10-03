import { useCallback, useEffect, useRef, useState } from 'react'

/**
 * Llama a `load` cada `intervalMs`. No encima requests (si uno tarda más que el
 * intervalo, el siguiente espera) y se pausa con la pestaña oculta. Un error no
 * corta el polling: queda en `error` hasta la siguiente respuesta buena — así
 * un servicio caído se ve en el panel y vuelve solo cuando se levanta.
 */
export function usePolling<T>(load: () => Promise<T>, intervalMs: number, deps: unknown[] = []) {
  const [data, setData] = useState<T | null>(null)
  const [error, setError] = useState<Error | null>(null)
  const inFlight = useRef(false)
  const loadRef = useRef(load)
  loadRef.current = load

  const refresh = useCallback(async () => {
    if (inFlight.current) return
    inFlight.current = true
    try {
      setData(await loadRef.current())
      setError(null)
    } catch (e) {
      setError(e instanceof Error ? e : new Error(String(e)))
    } finally {
      inFlight.current = false
    }
  }, [])

  useEffect(() => {
    void refresh()
    const id = setInterval(() => {
      if (!document.hidden) void refresh()
    }, intervalMs)
    return () => clearInterval(id)
  }, [intervalMs, refresh, ...deps])

  return { data, error, refresh }
}
