import { useCallback, useState } from 'react'
import { api, type CreateResult } from './api'
import { ChaosPanel } from './components/ChaosPanel'
import { CircuitBreakersPanel } from './components/CircuitBreakersPanel'
import { NewOrderPanel } from './components/NewOrderPanel'
import { OrdersPanel, type Latency } from './components/OrdersPanel'
import { SagaTimeline } from './components/SagaTimeline'
import { usePolling } from './usePolling'

export default function App() {
  const orders = usePolling(api.orders, 2000)
  const products = usePolling(api.products, 5000)
  const refreshOrders = orders.refresh
  const refreshProducts = products.refresh
  const [selected, setSelected] = useState<number | null>(null)
  // Status y latencia solo existen del lado del cliente: el backend no guarda
  // cuánto tardó la respuesta. Por eso solo aparecen en órdenes de esta pestaña.
  const [latencies, setLatencies] = useState<Record<number, Latency>>({})
  const [toast, setToast] = useState<string | null>(null)

  const notify = useCallback((msg: string) => {
    setToast(msg)
    setTimeout(() => setToast((t) => (t === msg ? null : t)), 5000)
  }, [])

  const onCreated = useCallback(
    (result: CreateResult) => {
      const order = result.order
      if (order) {
        setLatencies((l) => ({ ...l, [order.id]: { status: result.status, ms: result.ms } }))
        setSelected(order.id)
      } else if (result.error) {
        notify(`La orden no se creó: ${result.error}`)
      }
      void refreshOrders()
      void refreshProducts()
    },
    [notify, refreshOrders, refreshProducts],
  )

  return (
    <div className="mx-auto max-w-[110rem] px-4 py-5 sm:px-6">
      <header className="mb-5">
        <h1 className="text-xl font-bold text-slate-900">Saga + Circuit Breaker</h1>
        <p className="text-sm text-slate-500">
          Order → Payment → Inventory → Shipping. La saga deshace lo que quedó a medias; el Circuit Breaker deja de llamar
          a quien está fallando.
        </p>
      </header>

      <div className="space-y-4">
        <CircuitBreakersPanel onError={notify} />
        <div className="grid gap-4 lg:grid-cols-[minmax(0,1fr)_minmax(0,1fr)_minmax(0,1.2fr)]">
          <div className="min-w-0 space-y-4">
            <NewOrderPanel products={products.data ?? []} onCreated={onCreated} />
            <ChaosPanel onError={notify} />
          </div>
          <OrdersPanel
            orders={orders.data ?? []}
            latencies={latencies}
            selected={selected}
            onSelect={setSelected}
            offline={orders.error !== null}
          />
          <SagaTimeline orderId={selected} />
        </div>
      </div>

      {toast && (
        <div
          role="status"
          className="fixed inset-x-4 bottom-4 mx-auto max-w-md rounded-lg bg-slate-900 px-4 py-3 text-sm text-white shadow-lg"
        >
          {toast}
        </div>
      )}
    </div>
  )
}
