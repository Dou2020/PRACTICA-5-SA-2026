import { useState } from 'react'
import { api, HttpError, type ChaosMode, type Service } from '../api'
import { usePolling } from '../usePolling'
import { ChaosBadge, Panel } from './Badge'

const SERVICES: { id: Service; label: string; cb: string }[] = [
  { id: 'payment', label: 'payment-service', cb: 'paymentCB' },
  { id: 'inventory', label: 'inventory-service', cb: 'inventoryCB' },
  { id: 'shipping', label: 'shipping-service', cb: 'shippingCB' },
]

const MODES: { mode: ChaosMode; label: string; tone: string }[] = [
  { mode: 'NONE', label: 'Normal', tone: 'hover:bg-emerald-50 data-[on=true]:bg-emerald-600 data-[on=true]:text-white' },
  { mode: 'ERROR', label: '503', tone: 'hover:bg-rose-50 data-[on=true]:bg-rose-600 data-[on=true]:text-white' },
  { mode: 'SLOW', label: 'Lento', tone: 'hover:bg-amber-50 data-[on=true]:bg-amber-500 data-[on=true]:text-white' },
]

function ServiceChaos({ id, label, cb, onError }: { id: Service; label: string; cb: string; onError: (m: string) => void }) {
  const chaos = usePolling(() => api.chaos(id), 2000)
  const [delayMs, setDelayMs] = useState(3000)
  // Sin respuesta (o el 502 de nginx) = el contenedor está parado. Es la otra
  // forma de provocar el fallo técnico: docker compose stop <servicio>.
  const down = chaos.error !== null && (!(chaos.error instanceof HttpError) || chaos.error.status >= 500)

  const set = async (mode: ChaosMode) => {
    try {
      await api.setChaos(id, mode, delayMs)
      await chaos.refresh()
    } catch (e) {
      onError(`No se pudo cambiar el chaos de ${label}: ${(e as Error).message}`)
    }
  }

  return (
    <div className="rounded-lg p-3 ring-1 ring-slate-200">
      <div className="flex items-center justify-between gap-2">
        <div className="min-w-0">
          <div className="truncate font-mono text-sm font-semibold">{label}</div>
          <div className="text-xs text-slate-500">lo protege {cb}</div>
        </div>
        {down ? (
          <span className="rounded-md bg-slate-800 px-2 py-0.5 text-xs font-semibold text-white">servicio caído</span>
        ) : (
          chaos.data && <ChaosBadge mode={chaos.data.mode} />
        )}
      </div>
      <div className="mt-3 flex flex-wrap items-center gap-2">
        <div className="inline-flex overflow-hidden rounded-md border border-slate-300 text-xs font-medium">
          {MODES.map((m) => (
            <button
              key={m.mode}
              type="button"
              disabled={down}
              data-on={chaos.data?.mode === m.mode && !down}
              onClick={() => void set(m.mode)}
              className={`border-r border-slate-300 px-3 py-1 last:border-r-0 disabled:opacity-40 ${m.tone}`}
            >
              {m.label}
            </button>
          ))}
        </div>
        <label className="flex items-center gap-1 text-xs text-slate-500">
          demora
          <input
            type="number"
            min={0}
            step={500}
            value={delayMs}
            onChange={(e) => setDelayMs(Number(e.target.value))}
            className="w-20 rounded border border-slate-300 px-1.5 py-0.5 font-mono text-xs"
          />
          ms
        </label>
      </div>
    </div>
  )
}

export function ChaosPanel({ onError }: { onError: (msg: string) => void }) {
  return (
    <Panel title="Chaos · fallos técnicos">
      <p className="mb-3 text-xs text-slate-500">
        Un filtro en cada servicio responde 503 o duerme antes del controller. Es lo que el Circuit Breaker sí cuenta; un
        “sin stock” no.
      </p>
      <div className="space-y-2">
        {SERVICES.map((s) => (
          <ServiceChaos key={s.id} {...s} onError={onError} />
        ))}
      </div>
    </Panel>
  )
}
