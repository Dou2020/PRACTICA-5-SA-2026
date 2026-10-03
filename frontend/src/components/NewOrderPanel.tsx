import { useRef, useState } from 'react'
import { createOrder, type CreateOrder, type CreateResult, type Product } from '../api'
import { HttpBadge, OrderBadge, Panel, formatMs } from './Badge'

// Los mismos cuatro escenarios de "Pruebas con curl" del README.
const PRESETS: { label: string; hint: string; order: CreateOrder }[] = [
  {
    label: 'Flujo feliz',
    hint: 'los 3 pasos ✓',
    order: { customer: 'alice', sku: 'LAPTOP-01', quantity: 1, amount: 500, address: 'Xela zona 1' },
  },
  {
    label: 'Sin stock',
    hint: 'falla inventory → refund',
    order: { customer: 'alice', sku: 'AGOTADO-01', quantity: 1, amount: 100, address: 'Xela zona 1' },
  },
  {
    label: 'Pago rechazado',
    hint: 'monto > 1000',
    order: { customer: 'alice', sku: 'LAPTOP-01', quantity: 1, amount: 5000, address: 'Xela zona 1' },
  },
  {
    label: 'Envío falla',
    hint: 'release → refund',
    order: { customer: 'alice', sku: 'LAPTOP-01', quantity: 1, amount: 500, address: 'FAIL' },
  },
]

const BURST = 10

interface BurstRow {
  n: number
  result: CreateResult
}

const input = 'w-full rounded-md border border-slate-300 px-2 py-1.5 text-sm focus:border-sky-500 focus:outline-none'

export function NewOrderPanel({
  products,
  onCreated,
}: {
  products: Product[]
  onCreated: (result: CreateResult) => void
}) {
  const [form, setForm] = useState<CreateOrder>(PRESETS[0].order)
  const [busy, setBusy] = useState(false)
  const [bursting, setBursting] = useState(false)
  const [last, setLast] = useState<CreateResult | null>(null)
  const [burst, setBurst] = useState<BurstRow[]>([])
  const cancelBurst = useRef(false)

  const field = <K extends keyof CreateOrder>(key: K, value: CreateOrder[K]) => setForm((f) => ({ ...f, [key]: value }))

  const submit = async () => {
    setBusy(true)
    const result = await createOrder(form)
    setBurst([])
    setLast(result)
    onCreated(result)
    setBusy(false)
  }

  /**
   * Secuencial a propósito: en paralelo llegarían todas antes de que el CB
   * abra. Una tras otra se ve el salto — las primeras tardan (reintento + 503),
   * las que siguen a la apertura vuelven en milisegundos.
   */
  const runBurst = async () => {
    setBusy(true)
    setBursting(true)
    setBurst([])
    cancelBurst.current = false
    for (let n = 1; n <= BURST && !cancelBurst.current; n++) {
      const result = await createOrder(form)
      onCreated(result)
      setBurst((rows) => [...rows, { n, result }])
    }
    setBursting(false)
    setBusy(false)
  }

  return (
    <Panel title="Nueva orden">
      <div className="grid grid-cols-2 gap-2">
        {PRESETS.map((p) => (
          <button
            key={p.label}
            type="button"
            onClick={() => setForm(p.order)}
            className="rounded-lg border border-slate-200 px-2 py-1.5 text-left hover:border-sky-400 hover:bg-sky-50"
          >
            <div className="text-sm font-medium">{p.label}</div>
            <div className="text-xs text-slate-500">{p.hint}</div>
          </button>
        ))}
      </div>

      <form
        className="mt-4 grid grid-cols-2 gap-x-3 gap-y-2 text-sm"
        onSubmit={(e) => {
          e.preventDefault()
          void submit()
        }}
      >
        <label className="col-span-2">
          <span className="text-xs text-slate-500">Producto</span>
          <select className={input} value={form.sku} onChange={(e) => field('sku', e.target.value)}>
            {products.map((p) => (
              <option key={p.sku} value={p.sku}>
                {p.sku} — {p.name} (stock {p.stock})
              </option>
            ))}
            {!products.some((p) => p.sku === form.sku) && <option value={form.sku}>{form.sku}</option>}
          </select>
        </label>
        <label>
          <span className="text-xs text-slate-500">Cliente</span>
          <input className={input} value={form.customer} onChange={(e) => field('customer', e.target.value)} />
        </label>
        <label>
          <span className="text-xs text-slate-500">Cantidad</span>
          <input
            className={input}
            type="number"
            min={1}
            value={form.quantity}
            onChange={(e) => field('quantity', Number(e.target.value))}
          />
        </label>
        <label>
          <span className="text-xs text-slate-500">Monto (rechaza &gt; 1000)</span>
          <input
            className={input}
            type="number"
            min={1}
            step="0.01"
            value={form.amount}
            onChange={(e) => field('amount', Number(e.target.value))}
          />
        </label>
        <label>
          <span className="text-xs text-slate-500">Dirección (FAIL = sin cobertura)</span>
          <input className={input} value={form.address} onChange={(e) => field('address', e.target.value)} />
        </label>
        <div className="col-span-2 mt-1 flex gap-2">
          <button
            type="submit"
            disabled={busy}
            className="flex-1 rounded-md bg-sky-600 px-3 py-2 text-sm font-semibold text-white hover:bg-sky-700 disabled:opacity-50"
          >
            Crear orden
          </button>
          {bursting ? (
            <button
              type="button"
              onClick={() => (cancelBurst.current = true)}
              className="rounded-md border border-rose-300 px-3 py-2 text-sm font-semibold text-rose-700 hover:bg-rose-50"
            >
              Detener
            </button>
          ) : (
            <button
              type="button"
              disabled={busy}
              onClick={() => void runBurst()}
              className="rounded-md border border-slate-300 px-3 py-2 text-sm font-semibold text-slate-700 hover:bg-slate-50 disabled:opacity-50"
            >
              Ráfaga ×{BURST}
            </button>
          )}
        </div>
      </form>

      {last && burst.length === 0 && (
        <div className="mt-3 flex flex-wrap items-center gap-2 rounded-md bg-slate-50 px-3 py-2 text-sm">
          <HttpBadge status={last.status} />
          {last.order && <OrderBadge status={last.order.status} />}
          <span className="font-mono text-xs text-slate-500">{formatMs(last.ms)}</span>
          {last.error && <span className="text-xs text-rose-700">{last.error}</span>}
        </div>
      )}

      {burst.length > 0 && (
        <div className="mt-3">
          <h3 className="text-xs font-semibold text-slate-500">
            Ráfaga {burst.length}/{BURST}
          </h3>
          <ol className="mt-1 space-y-0.5 font-mono text-xs">
            {burst.map(({ n, result }) => (
              <li key={n} className="flex items-center gap-2">
                <span className="w-5 text-right text-slate-400">{n}</span>
                <HttpBadge status={result.status} />
                <span className="w-16 text-right">{formatMs(result.ms)}</span>
                {/* Barra proporcional a la latencia: el salto al abrir el circuito se ve sin leer números. */}
                <span className="h-2 rounded bg-slate-300" style={{ width: `${Math.max(2, Math.min(120, result.ms / 4))}px` }} />
                <span className="truncate text-slate-500">
                  {result.order?.steps.some((s) => s.outcome === 'FALLBACK') ? 'FALLBACK' : (result.order?.status ?? result.error)}
                </span>
              </li>
            ))}
          </ol>
        </div>
      )}
    </Panel>
  )
}
