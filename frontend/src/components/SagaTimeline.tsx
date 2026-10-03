import { api, type SagaStep } from '../api'
import { usePolling } from '../usePolling'
import { OrderBadge, Panel, formatMs } from './Badge'

const ICON: Record<SagaStep['outcome'], string> = { OK: '✓', FAILED: '✗', FALLBACK: '⚡' }

const TONE: Record<SagaStep['outcome'], string> = {
  OK: 'bg-emerald-100 text-emerald-700',
  FAILED: 'bg-rose-100 text-rose-700',
  FALLBACK: 'bg-amber-100 text-amber-800',
}

/**
 * El log de saga_steps tal cual lo guardó el orquestador. Las compensaciones
 * van indentadas y con ↺; el "+Xs" entre pasos es lo que explica una orden de
 * 8.7 s (dos timeouts con reintento) frente a una de 0.2 s (circuito abierto).
 */
export function SagaTimeline({ orderId }: { orderId: number | null }) {
  const order = usePolling(() => (orderId === null ? Promise.resolve(null) : api.order(orderId)), 2000, [orderId])
  const o = order.data

  if (orderId === null) {
    return (
      <Panel title="Timeline de la saga">
        <p className="text-sm text-slate-400">Elegí una orden para ver cada paso y cada compensación.</p>
      </Panel>
    )
  }

  return (
    <Panel title={`Timeline de la saga · orden #${orderId}`} aside={o && <OrderBadge status={o.status} />}>
      {!o || o.id !== orderId ? (
        <p className="text-sm text-slate-400">Cargando…</p>
      ) : (
        <>
          <div className="mb-3 text-xs text-slate-500">
            {o.customer} · {o.sku} ×{o.quantity} · {o.amount} · “{o.address}”
          </div>
          <ol className="space-y-1.5">
            {o.steps.map((s, i) => {
              const prev = i > 0 ? new Date(o.steps[i - 1].createdAt).getTime() : null
              const delta = prev === null ? null : new Date(s.createdAt).getTime() - prev
              const compensate = s.action === 'COMPENSATE'
              return (
                <li key={i} className={`flex gap-2 ${compensate ? 'pl-6' : ''}`}>
                  <span
                    className={`mt-0.5 flex size-5 shrink-0 items-center justify-center rounded-full text-xs font-bold ${TONE[s.outcome]}`}
                  >
                    {compensate && s.outcome === 'OK' ? '↺' : ICON[s.outcome]}
                  </span>
                  <div className="min-w-0 flex-1">
                    <div className="flex flex-wrap items-baseline gap-x-2">
                      <span className="font-mono text-xs font-semibold">{s.step}</span>
                      <span className="text-xs text-slate-500">
                        {compensate ? 'compensa' : 'ejecuta'} · {s.outcome}
                      </span>
                      {delta !== null && (
                        <span className={`ml-auto font-mono text-xs ${delta >= 1000 ? 'font-semibold text-amber-700' : 'text-slate-400'}`}>
                          +{formatMs(delta)}
                        </span>
                      )}
                    </div>
                    {s.detail && <p className="break-words text-sm text-slate-700">{s.detail}</p>}
                  </div>
                </li>
              )
            })}
          </ol>
          {o.status === 'COMPENSATION_FAILED' && (
            <p className="mt-3 rounded-md bg-rose-50 px-3 py-2 text-xs text-rose-800">
              Una compensación no pudo ejecutarse: el sistema quedó inconsistente y requiere intervención manual. Es el
              límite real de la saga — consistencia <em>eventual</em>.
            </p>
          )}
        </>
      )}
    </Panel>
  )
}
