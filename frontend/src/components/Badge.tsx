import type { ReactNode } from 'react'
import type { CbState, ChaosMode, OrderStatus } from '../api'

// Las clases van completas en estos mapas, nunca armadas con plantillas:
// Tailwind escanea texto plano y no generaría `bg-${color}-100`.

const ORDER: Record<OrderStatus, string> = {
  COMPLETED: 'bg-emerald-100 text-emerald-800 ring-emerald-600/20',
  CANCELLED: 'bg-slate-200 text-slate-700 ring-slate-500/20',
  COMPENSATION_FAILED: 'bg-rose-100 text-rose-800 ring-rose-600/30',
  PENDING: 'bg-sky-100 text-sky-800 ring-sky-600/20',
}

const CB: Record<CbState, string> = {
  CLOSED: 'bg-emerald-100 text-emerald-800 ring-emerald-600/20',
  OPEN: 'bg-rose-100 text-rose-800 ring-rose-600/30',
  HALF_OPEN: 'bg-amber-100 text-amber-800 ring-amber-600/30',
  DISABLED: 'bg-slate-200 text-slate-700 ring-slate-500/20',
  FORCED_OPEN: 'bg-rose-100 text-rose-800 ring-rose-600/30',
  METRICS_ONLY: 'bg-slate-200 text-slate-700 ring-slate-500/20',
}

const CHAOS: Record<ChaosMode, string> = {
  NONE: 'bg-emerald-100 text-emerald-800 ring-emerald-600/20',
  ERROR: 'bg-rose-100 text-rose-800 ring-rose-600/30',
  SLOW: 'bg-amber-100 text-amber-800 ring-amber-600/30',
}

const base = 'inline-flex items-center rounded-md px-2 py-0.5 text-xs font-semibold ring-1 ring-inset whitespace-nowrap'

export function OrderBadge({ status }: { status: OrderStatus }) {
  return <span className={`${base} ${ORDER[status]}`}>{status}</span>
}

export function CbBadge({ state }: { state: CbState }) {
  return <span className={`${base} ${CB[state]}`}>{state}</span>
}

export function ChaosBadge({ mode }: { mode: ChaosMode }) {
  return <span className={`${base} ${CHAOS[mode]}`}>{mode}</span>
}

/** Status HTTP de POST /orders: el color dice cómo terminó la saga. */
export function HttpBadge({ status }: { status: number }) {
  const tone =
    status >= 200 && status < 300
      ? 'bg-emerald-50 text-emerald-700 ring-emerald-600/20'
      : status >= 400 && status < 500
        ? 'bg-amber-50 text-amber-800 ring-amber-600/20'
        : 'bg-rose-50 text-rose-700 ring-rose-600/20'
  return <span className={`${base} font-mono ${tone}`}>{status === 0 ? 'red' : status}</span>
}

export function Panel({ title, aside, children }: { title: string; aside?: ReactNode; children: ReactNode }) {
  return (
    <section className="min-w-0 rounded-xl bg-white p-4 shadow-sm ring-1 ring-slate-200">
      <header className="mb-3 flex items-center justify-between gap-2">
        <h2 className="text-sm font-semibold uppercase tracking-wide text-slate-500">{title}</h2>
        {aside}
      </header>
      {children}
    </section>
  )
}

export function formatMs(ms: number) {
  return ms >= 1000 ? `${(ms / 1000).toFixed(2)} s` : `${ms} ms`
}

export function formatTime(iso: string) {
  return new Date(iso).toLocaleTimeString('es-GT', { hour12: false })
}
