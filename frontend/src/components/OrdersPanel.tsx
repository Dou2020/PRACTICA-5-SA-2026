import type { OrderSummary } from '../api'
import { HttpBadge, OrderBadge, Panel, formatMs, formatTime } from './Badge'

export interface Latency {
  status: number
  ms: number
}

export function OrdersPanel({
  orders,
  latencies,
  selected,
  onSelect,
  offline,
}: {
  orders: OrderSummary[]
  latencies: Record<number, Latency>
  selected: number | null
  onSelect: (id: number) => void
  offline: boolean
}) {
  return (
    <Panel
      title="Órdenes"
      aside={offline && <span className="text-xs text-rose-600">order-service no responde</span>}
    >
      {orders.length === 0 ? (
        <p className="text-sm text-slate-400">Todavía no hay órdenes. Elegí un preset y creá una.</p>
      ) : (
        <ul className="max-h-[32rem] divide-y divide-slate-100 overflow-y-auto">
          {orders.map((o) => {
            const lat = latencies[o.id]
            return (
              <li key={o.id}>
                <button
                  type="button"
                  onClick={() => onSelect(o.id)}
                  className={`w-full px-2 py-2 text-left hover:bg-slate-50 ${selected === o.id ? 'bg-sky-50 ring-1 ring-inset ring-sky-200' : ''}`}
                >
                  <div className="flex flex-wrap items-center gap-2">
                    <span className="font-mono text-sm font-semibold">#{o.id}</span>
                    <OrderBadge status={o.status} />
                    {lat && <HttpBadge status={lat.status} />}
                    {lat && <span className="font-mono text-xs text-slate-500">{formatMs(lat.ms)}</span>}
                    <span className="ml-auto text-xs text-slate-400">{formatTime(o.createdAt)}</span>
                  </div>
                  <div className="mt-0.5 flex flex-wrap gap-x-2 text-xs text-slate-500">
                    <span>
                      {o.customer} · {o.sku} ×{o.quantity} · {o.amount}
                    </span>
                    {o.failureCode && <span className="font-mono text-rose-700">{o.failureCode}</span>}
                  </div>
                </button>
              </li>
            )
          })}
        </ul>
      )}
    </Panel>
  )
}
