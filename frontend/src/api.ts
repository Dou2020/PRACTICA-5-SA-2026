// Tipos espejo de los DTOs de los servicios. Igual que entre microservicios, el
// contrato es el JSON: si cambia un record en Java, se cambia acá a mano.

export type OrderStatus = 'PENDING' | 'COMPLETED' | 'CANCELLED' | 'COMPENSATION_FAILED'
export type StepName = 'ORDER' | 'PAYMENT' | 'INVENTORY' | 'SHIPPING'
export type StepAction = 'EXECUTE' | 'COMPENSATE'
export type StepOutcome = 'OK' | 'FAILED' | 'FALLBACK'
export type CbState = 'CLOSED' | 'OPEN' | 'HALF_OPEN' | 'DISABLED' | 'FORCED_OPEN' | 'METRICS_ONLY'
export type ChaosMode = 'NONE' | 'ERROR' | 'SLOW'
export type Service = 'payment' | 'inventory' | 'shipping'

export interface SagaStep {
  step: StepName
  action: StepAction
  outcome: StepOutcome
  detail: string | null
  createdAt: string
}

export interface OrderSummary {
  id: number
  customer: string
  sku: string
  quantity: number
  amount: number
  status: OrderStatus
  failureCode: string | null
  failureReason: string | null
  createdAt: string
}

export interface Order extends OrderSummary {
  address: string
  paymentId: number | null
  reservationId: number | null
  shipmentId: number | null
  updatedAt: string
  steps: SagaStep[]
}

export interface CreateOrder {
  customer: string
  sku: string
  quantity: number
  amount: number
  address: string
}

export interface CircuitBreaker {
  name: string
  state: CbState
  failureRate: number
  slowCallRate: number
  bufferedCalls: number
  failedCalls: number
  slowCalls: number
  notPermittedCalls: number
}

export interface Transition {
  name: string
  from: CbState
  to: CbState
  at: string
}

export interface Product {
  sku: string
  name: string
  stock: number
}

export interface Chaos {
  mode: ChaosMode
  delayMs: number
}

export interface ApiError {
  error: string
  message: string
}

/** Un fallo de la llamada HTTP del panel (no de la saga). */
export class HttpError extends Error {
  readonly status: number

  constructor(status: number, message: string) {
    super(message)
    this.status = status
  }
}

async function request<T>(url: string, init?: RequestInit): Promise<T> {
  const res = await fetch(url, init)
  if (!res.ok) {
    const body = (await res.json().catch(() => null)) as ApiError | null
    throw new HttpError(res.status, body?.message ?? `${res.status} ${res.statusText}`)
  }
  return (await res.json()) as T
}

const json = (body: unknown): RequestInit => ({
  method: 'POST',
  headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify(body),
})

export const api = {
  orders: () => request<OrderSummary[]>('/api/orders'),
  order: (id: number) => request<Order>(`/api/orders/${id}`),
  products: () => request<Product[]>('/api/inventory/products'),
  circuitBreakers: () => request<CircuitBreaker[]>('/api/resilience/circuit-breakers'),
  transitions: () => request<Transition[]>('/api/resilience/circuit-breakers/transitions'),
  resetCircuitBreaker: (name: string) =>
    request<CircuitBreaker>(`/api/resilience/circuit-breakers/${name}/reset`, { method: 'POST' }),
  chaos: (service: Service) => request<Chaos>(`/api/chaos/${service}`),
  setChaos: (service: Service, mode: ChaosMode, delayMs: number) =>
    request<Chaos>(`/api/chaos/${service}`, json({ mode, delayMs })),
}

/** Resultado de POST /orders: la saga responde con la orden aunque el status no sea 2xx. */
export interface CreateResult {
  status: number
  ms: number
  order: Order | null
  error: string | null
}

/**
 * No usa request(): un 409/422/503/500 acá no es un error del panel, es cómo
 * terminó la saga, y el cuerpo sigue siendo la orden con su timeline. Solo un
 * 400 (validación) trae {error, message} en vez de una orden.
 */
export async function createOrder(body: CreateOrder): Promise<CreateResult> {
  const start = performance.now()
  try {
    const res = await fetch('/api/orders', json(body))
    const payload = (await res.json().catch(() => null)) as Order | ApiError | null
    const ms = Math.round(performance.now() - start)
    if (payload && 'steps' in payload) {
      return { status: res.status, ms, order: payload, error: null }
    }
    return { status: res.status, ms, order: null, error: payload?.message ?? `HTTP ${res.status}` }
  } catch (e) {
    const ms = Math.round(performance.now() - start)
    return { status: 0, ms, order: null, error: e instanceof Error ? e.message : String(e) }
  }
}
