import { useEffect, useState } from 'react'
import { api, type CircuitBreaker, type Transition } from '../api'
import { usePolling } from '../usePolling'
import { CbBadge, Panel, formatTime } from './Badge'

/** waitDurationInOpenState del application.yml de order-service. */
const WAIT_OPEN_MS = 10_000

/** En el orden del flujo de la saga, no alfabético. */
const ORDER = ['paymentCB', 'inventoryCB', 'shippingCB']

const LABEL: Record<string, string> = {
  paymentCB: 'payment-service',
  inventoryCB: 'inventory-service',
  shippingCB: 'shipping-service',
}

function rate(value: number) {
  // -1 = todavía no hay minimumNumberOfCalls (5) llamadas en la ventana.
  return value < 0 ? '< 5 llamadas' : `${value.toFixed(0)} %`
}

function Countdown({ openedAt }: { openedAt: string | undefined }) {
  const [now, setNow] = useState(() => Date.now())
  useEffect(() => {
    const id = setInterval(() => setNow(Date.now()), 250)
    return () => clearInterval(id)
  }, [])
  if (!openedAt) return null
  const left = Math.max(0, WAIT_OPEN_MS - (now - new Date(openedAt).getTime()))
  return (
    <div className="mt-2">
      <div className="flex justify-between text-xs text-rose-700">
        <span>Rechaza sin llamar · HALF_OPEN en</span>
        <span className="font-mono font-semibold">{(left / 1000).toFixed(1)} s</span>
      </div>
      <div className="mt-1 h-1.5 overflow-hidden rounded-full bg-rose-100">
        <div className="h-full bg-rose-500 transition-[width] duration-200" style={{ width: `${(left / WAIT_OPEN_MS) * 100}%` }} />
      </div>
    </div>
  )
}

function Card({ cb, openedAt, onReset }: { cb: CircuitBreaker; openedAt: string | undefined; onReset: () => void }) {
  const ring =
    cb.state === 'OPEN' ? 'ring-rose-300 bg-rose-50/40' : cb.state === 'HALF_OPEN' ? 'ring-amber-300 bg-amber-50/40' : 'ring-slate-200'
  return (
    <div className={`rounded-lg p-3 ring-1 ${ring}`}>
      <div className="flex items-center justify-between gap-2">
        <div className="min-w-0">
          <div className="font-mono text-sm font-semibold">{cb.name}</div>
          <div className="truncate text-xs text-slate-500">{LABEL[cb.name] ?? ''}</div>
        </div>
        <CbBadge state={cb.state} />
      </div>
      <dl className="mt-3 grid grid-cols-2 gap-x-3 gap-y-1 text-xs">
        <dt className="text-slate-500">Tasa de fallos</dt>
        <dd className="text-right font-mono">{rate(cb.failureRate)}</dd>
        <dt className="text-slate-500">Tasa lentas</dt>
        <dd className="text-right font-mono">{rate(cb.slowCallRate)}</dd>
        <dt className="text-slate-500">En ventana</dt>
        <dd className="text-right font-mono">{cb.bufferedCalls} / 10</dd>
        <dt className="text-slate-500">Fallidas · lentas</dt>
        <dd className="text-right font-mono">
          {cb.failedCalls} · {cb.slowCalls}
        </dd>
        <dt className="text-slate-500">Rechazadas sin llamar</dt>
        <dd className="text-right font-mono">{cb.notPermittedCalls}</dd>
      </dl>
      {cb.state === 'OPEN' && <Countdown openedAt={openedAt} />}
      {cb.state === 'HALF_OPEN' && (
        <p className="mt-2 text-xs text-amber-800">Deja pasar 3 llamadas de prueba y decide con las 3.</p>
      )}
      <button
        type="button"
        onClick={onReset}
        className="mt-3 w-full rounded-md border border-slate-300 px-2 py-1 text-xs font-medium text-slate-600 hover:bg-slate-50"
      >
        Reset → CLOSED
      </button>
    </div>
  )
}

export function CircuitBreakersPanel({ onError }: { onError: (msg: string) => void }) {
  const cbs = usePolling(api.circuitBreakers, 1000)
  const transitions = usePolling(api.transitions, 1000)

  const openedAt = (name: string) =>
    transitions.data?.find((t: Transition) => t.name === name && t.to === 'OPEN')?.at

  const reset = async (name: string) => {
    try {
      await api.resetCircuitBreaker(name)
      await Promise.all([cbs.refresh(), transitions.refresh()])
    } catch (e) {
      onError(`No se pudo resetear ${name}: ${(e as Error).message}`)
    }
  }

  return (
    <Panel
      title="Circuit Breakers · order-service"
      aside={cbs.error && <span className="text-xs text-rose-600">order-service no responde</span>}
    >
      <div className="grid gap-3 lg:grid-cols-[repeat(3,minmax(0,1fr))_minmax(0,1.1fr)]">
        {[...(cbs.data ?? [])].sort((a, b) => ORDER.indexOf(a.name) - ORDER.indexOf(b.name)).map((cb) => (
          <Card key={cb.name} cb={cb} openedAt={openedAt(cb.name)} onReset={() => void reset(cb.name)} />
        ))}
        <div className="min-w-0 rounded-lg bg-slate-50 p-3 ring-1 ring-slate-200">
          <h3 className="text-xs font-semibold text-slate-500">Transiciones recientes</h3>
          {transitions.data?.length ? (
            <ul className="mt-2 space-y-1 font-mono text-xs">
              {transitions.data.slice(0, 8).map((t) => (
                <li key={`${t.name}-${t.at}`} className="flex flex-wrap gap-x-2">
                  <span className="text-slate-400">{formatTime(t.at)}</span>
                  <span className="font-semibold">{t.name}</span>
                  <span>
                    {t.from} → {t.to}
                  </span>
                </li>
              ))}
            </ul>
          ) : (
            <p className="mt-2 text-xs text-slate-400">Sin cambios de estado todavía.</p>
          )}
        </div>
      </div>
    </Panel>
  )
}
