# PRACTICA 5 — Saga Pattern + Circuit Breaker Pattern

POC de un flujo de compra en e-commerce con **4 microservicios** (Order → Payment
→ Inventory → Shipping) que demuestra los dos patrones de resiliencia que pide el
enunciado ([PRACTICA_5.md](PRACTICA_5.md)).

La tesis que organiza todo el diseño: **los dos patrones atacan fallos de
naturaleza distinta, y se complementan en vez de competir.**

| | Saga | Circuit Breaker |
|---|---|---|
| Tipo de fallo | **de negocio** — el servicio responde bien, pero dice "no" (sin stock, pago rechazado) | **técnico** — el servicio no responde, tarda o da 5xx |
| Qué garantiza | consistencia eventual: deshacer lo que ya se hizo | no seguir golpeando (ni esperando) a quien ya está fallando |
| Dónde vive su estado | persistido en `order_db` (`orders` + `saga_steps`) | en memoria del orquestador (Resilience4j) |
| Qué ve el usuario | orden `CANCELLED` con el motivo | respuesta inmediata "servicio no disponible, intente más tarde" |

De ahí salen dos decisiones que la demo hace visibles:

1. **Un fallo de negocio no abre el circuito.** Un 409 "sin stock" es una
   respuesta correcta de un servicio sano; si contara como fallo, diez clientes
   pidiendo un producto agotado apagarían el inventario para todos. Por eso esos
   errores van en `ignoreExceptions`.
2. **Cuando el circuito está abierto, el fallback alimenta a la saga**: el paso
   falla rápido (sin timeout) y el orquestador dispara las compensaciones igual
   que ante cualquier otro fallo. El CB decide *cuándo* dejar de intentar; la
   saga decide *qué hacer* con lo que quedó a medias.

---

## Arquitectura

**Saga por orquestación**: `order-service` es el orquestador y llama por REST, en
orden, a los otros tres. Se eligió orquestación sobre coreografía porque es la
que tiene llamadas síncronas entre servicios — exactamente lo que un Circuit
Breaker protege. Con coreografía por eventos el CB casi no tendría qué envolver.
La comparación entre ambos enfoques va en el informe
([docs/01-saga.md](docs/01-saga.md)).

```
                      ┌──────────────────────┐
                      │  frontend (React)    │  :3000
                      │  nginx → /api/*      │
                      └──────────┬───────────┘
                                 │ REST
                      ┌──────────▼───────────┐
                      │   order-service      │  :8081
                      │   ORQUESTADOR SAGA   │
                      │ ┌──────────────────┐ │
                      │ │ paymentCB        │ │
                      │ │ inventoryCB      │ │  Resilience4j
                      │ │ shippingCB       │ │  (CB + Retry + Timeout)
                      │ └──────────────────┘ │
                      └───┬───────┬───────┬──┘
                   REST   │       │       │   REST
          ┌───────────────▼┐ ┌────▼────────────┐ ┌▼───────────────┐
          │ payment-service│ │inventory-service│ │shipping-service│
          │     :8082      │ │     :8083       │ │     :8084      │
          └───────┬────────┘ └───────┬─────────┘ └───────┬────────┘
                  │                  │                   │
   ┌──────────────▼──────────────────▼───────────────────▼──────────┐
   │ PostgreSQL :5433 — un contenedor, una base por servicio         │
   │ order_db · payment_db · inventory_db · shipping_db              │
   └─────────────────────────────────────────────────────────────────┘
```

**Database per service**: cada servicio tiene su propia base y solo él la toca.
Están en un solo contenedor para no levantar cuatro postgres, pero ningún
servicio conoce las credenciales ni el esquema de otro — por eso no hay
transacción ACID posible entre ellos, y por eso hace falta la saga.

| Servicio | Puerto | Base | Rol |
|---|---|---|---|
| `frontend` | 3000 | — | Panel de demo: órdenes, timeline de saga, estado de CBs, chaos |
| `order-service` | 8081 | `order_db` | Crea la orden y orquesta la saga |
| `payment-service` | 8082 | `payment_db` | Cobra y reembolsa |
| `inventory-service` | 8083 | `inventory_db` | Reserva y libera stock |
| `shipping-service` | 8084 | `shipping_db` | Programa y cancela envíos |
| `postgres` | 5433 (host) → 5432 | las 4 | El 5432 del host suele estar tomado por un postgres local |

---

## Stack

| Capa | Tecnología |
|---|---|
| Backend | Java 21 · Spring Boot 3.5 · Maven · Spring Web (`RestClient`) · Spring Data JPA · Flyway · Actuator |
| Resiliencia | Resilience4j 2.x (`resilience4j-spring-boot3` + `spring-boot-starter-aop`) |
| Base de datos | PostgreSQL 16 |
| Frontend | React 19 · Vite 8 · TypeScript · Tailwind CSS v4 |
| Infraestructura | Docker Compose · nginx (sirve el frontend y hace reverse-proxy a `/api`) |

Requisitos para desarrollar fuera de Docker: JDK 21, Maven 3.9+, Node 22.
Para solo correr la demo: Docker.

## Levantar

```bash
cd PRACTICA_5
docker compose up --build        # todo: postgres + 4 servicios + frontend
```

| URL | Qué es |
|---|---|
| http://localhost:3000 | Panel de la demo |
| http://localhost:8081/actuator/circuitbreakers | Estado crudo de los 3 CBs |
| http://localhost:8081/actuator/circuitbreakerevents | Historial de transiciones |

Correr un servicio fuera de Docker (con postgres del compose arriba): se conecta
a `localhost:5433` por default.

```bash
docker compose up -d postgres
cd payment-service && mvn spring-boot:run
```

El panel en modo desarrollo (con el backend del compose arriba): `vite.config.ts`
tiene el mismo mapeo de `/api` que `nginx.conf`, contra los puertos del host.

```bash
cd frontend && npm install && npm run dev      # http://localhost:3000
```

Volver al estado inicial (stock sembrado, sin órdenes):

```bash
docker compose down -v && docker compose up --build
```

---

## Endpoints

### order-service (:8081) — orquestador

| Método | Ruta | Qué hace |
|---|---|---|
| `POST` | `/orders` | `{customer, sku, quantity, amount, address}` → crea la orden (`PENDING`) y ejecuta la saga completa. El cuerpo es **siempre** la orden con su timeline; el status dice cómo terminó (tabla abajo) |
| `GET` | `/orders` | Lista de órdenes |
| `GET` | `/orders/{id}` | Orden + sus `saga_steps` (el timeline) |
| `DELETE` | `/orders/{id}` | **Compensación** del paso Order (la misma que usa la saga): cancela la orden. Idempotente; **409** `ORDER_COMPLETED` si ya se completó — cancelarla dejaría cobro, reserva y envío vigentes |
| `GET` | `/resilience/circuit-breakers` | Estado y métricas de cada CB (lo consume el frontend) |
| `POST` | `/resilience/circuit-breakers/{name}/reset` | Vuelve un CB a `CLOSED` para repetir la demo |
| `GET` | `/resilience/circuit-breakers/transitions` | Últimos 50 cambios de estado de los CBs (el más reciente primero). Propio y no `/actuator/circuitbreakerevents`: ese mezcla las transiciones con un evento por llamada en un buffer acotado, y su timestamp (`…Z[Etc/UTC]`) no lo parsea un navegador |

Status de `POST /orders`:

| Cómo terminó la saga | Status |
|---|---|
| `COMPLETED` | **201** |
| `CANCELLED` por un "no" de negocio | el **409/422** que devolvió el servicio que dijo que no |
| `CANCELLED` por falla técnica o circuito abierto | **503** con "Servicio de … no disponible, intente más tarde" |
| `COMPENSATION_FAILED` | **500** |

### Contrato común de los servicios hoja

- **Las acciones también son idempotentes, por `orderId`** (cada tabla tiene
  `UNIQUE(order_id)`). La primera llamada responde **201**; una repetida para la
  misma orden devuelve el recurso existente con **200** y no cobra, descuenta ni
  programa otra vez. Lo exige la Fase 4: el `@Retry` reintenta cuando no llegó
  respuesta, y "no llegó respuesta" no significa "no se ejecutó".
- **Errores** con cuerpo `{"error": "<CODIGO>", "message": "<texto>"}`. Negocio
  → 409/422; recurso inexistente → 404 `NOT_FOUND`; body inválido → 400
  `VALIDATION`; chaos → 503 `CHAOS`. El orquestador decide por el status:
  4xx = la saga compensa, 5xx/timeout = además cuenta para el Circuit Breaker.

### payment-service (:8082)

| Método | Ruta | Qué hace |
|---|---|---|
| `POST` | `/payments` | `{orderId, amount}` → 201 `CHARGED`. **422** `PAYMENT_REJECTED` si `amount > 1000` (regla de negocio de la demo) |
| `POST` | `/payments/{id}/refund` | **Compensación**: 200 `REFUNDED`. Idempotente; 404 si el pago no existe |
| `GET` | `/payments/{id}` | Consulta (para verificar) |
| `GET` | `/payments?orderId=X` | Búsqueda por orden, 404 si no hay. La usa la saga para compensar un cobro **incierto** (ver [Saga](#el-paso-incierto)) |

### inventory-service (:8083)

| Método | Ruta | Qué hace |
|---|---|---|
| `POST` | `/inventory/reserve` | `{orderId, sku, quantity}` → 201 `RESERVED`. **409** `OUT_OF_STOCK` si no alcanza; **422** `UNKNOWN_PRODUCT` si el sku no existe |
| `POST` | `/inventory/release` | **Compensación**: `{orderId}` → 200 y devuelve el stock. Idempotente, y **200 aunque no haya reserva** (ver abajo) |
| `GET` | `/inventory/products` | Catálogo con stock (para el formulario del frontend) |

Catálogo sembrado: `LAPTOP-01` (50), `MOUSE-01` (100), `TECLADO-01` (30),
`AGOTADO-01` (**0**, para el escenario "sin stock").

- **El descuento es un solo `UPDATE ... WHERE stock >= :qty`**, no leer-comparar-
  guardar. Verificado: 40 reservas concurrentes sobre stock 30 → exactamente 30
  × 201 y 10 × 409, stock final 0.
- **`release` sin reserva responde 200** ("nada que liberar"), no 404. Si el
  reserve dio timeout del lado del orquestador, este no sabe si se ejecutó y
  tiene que poder compensar "por si acaso". Por eso también se pide por
  `orderId` y no por id de reserva: el id nunca le llegó.

### shipping-service (:8084)

| Método | Ruta | Qué hace |
|---|---|---|
| `POST` | `/shipping/schedule` | `{orderId, address}` → 201 `SCHEDULED` con tracking `SHP-<id>`. **422** `ADDRESS_UNDELIVERABLE` si la dirección contiene `FAIL` (sin distinguir mayúsculas; fuerza la compensación de 3 pasos) |
| `DELETE` | `/shipping/{id}` | **Compensación**: 200 `CANCELLED` con cuerpo. Idempotente; 404 si no existe |
| `GET` | `/shipping?orderId=X` | Búsqueda por orden, 404 si no hay. Para compensar un envío **incierto** |

### Chaos — en payment, inventory y shipping

| Método | Ruta | Qué hace |
|---|---|---|
| `GET` | `/chaos` | Modo actual |
| `POST` | `/chaos` | `{"mode":"NONE"\|"ERROR"\|"SLOW","delayMs":3000}` |

Un filtro HTTP responde **503** (`ERROR`) o duerme `delayMs` (`SLOW`, default
3000) antes de llegar al controller. Es un fallo **técnico** a propósito: es lo
que el CB sí debe contar. Permite abrir un circuito en vivo sin tumbar
contenedores (aunque `docker compose stop payment-service` también funciona y se
muestra en el video). Cada request afectado deja un `WARN CHAOS ...` en el log
del servicio.

`/chaos` y `/actuator` quedan **fuera** del filtro: si no, con `ERROR` activo no
habría cómo apagarlo, y el healthcheck de Docker marcaría el contenedor como
caído — el fallo lo tiene que detectar el Circuit Breaker, no Docker.

Los paquetes `chaos/` y `web/` (manejo de errores) son **byte-idénticos** en los
tres servicios hoja, sin librería compartida — cada microservicio se construye
solo. Se comprueba:

```bash
B=src/main/java/gt/cunoc/sa
for s in inventory shipping; do for p in chaos web; do
  diff -r payment-service/$B/$p $s-service/$B/$p
done; done
# sin salida
```

---

## Saga

### Flujo feliz

```
POST /orders
  1. payment    POST /payments            → CHARGED
  2. inventory  POST /inventory/reserve   → RESERVED
  3. shipping   POST /shipping/schedule   → SCHEDULED
  → orden COMPLETED
```

### Compensaciones según dónde falla

Si el paso *n* falla, el orquestador compensa **en orden inverso** los pasos que
ya se completaron y cancela la orden.

| Falla en | Causa en la demo | Compensaciones ejecutadas | Estado final |
|---|---|---|---|
| Payment | `amount > 1000` | — (nada que deshacer) → cancelar orden | `CANCELLED` |
| Inventory | producto sin stock | refund → cancelar orden | `CANCELLED` |
| Shipping | dirección con `FAIL` | release → refund → cancelar orden | `CANCELLED` |
| Cualquiera | CB abierto (fallback) o 5xx tras reintentos | las mismas que arriba según el paso | `CANCELLED` |
| Cualquiera | timeout (resultado incierto) | las de arriba **más la del propio paso**, buscándolo por `orderId` | `CANCELLED`, o `COMPENSATION_FAILED` si el servicio sigue sin responder |

Cada ejecución y cada compensación queda registrada en `saga_steps`
(`step`, `action` = `EXECUTE`\|`COMPENSATE`, `outcome` = `OK`\|`FAILED`\|`FALLBACK`,
`detail`, `timestamp`). Ese log es el timeline que muestra el frontend.

Las órdenes, sus ids remotos (`payment_id`, `reservation_id`, `shipment_id`) y
el log viven en `order_db`. El orquestador **no** es `@Transactional`: no hay
transacción que cubra cuatro bases (es el problema que la saga resuelve), y cada
cambio local se confirma apenas ocurre, sin dejar una transacción abierta
durante una llamada HTTP.

Tres reglas que hacen que la saga sea confiable:

- **Las compensaciones son idempotentes.** Un refund de un pago ya reembolsado o
  un release de una reserva ya liberada devuelve 200 sin hacer nada. Así el
  orquestador puede reintentarlas sin miedo a devolver dinero dos veces.
- **Una compensación que falla no se esconde.** Se reintenta; si igual falla, la
  orden queda en `COMPENSATION_FAILED`. Es el límite real del patrón: la saga da
  consistencia *eventual*, y a veces "eventual" requiere intervención manual.
  Las demás compensaciones se ejecutan igual: devolver el dinero no depende de
  haber liberado el stock.
- **Un timeout no dice si el paso se ejecutó** (ver abajo).

### El paso incierto

Lo que el orquestador sabe de un paso que falló depende de *cómo* falló
(`client/RemoteErrors.java`):

| Qué pasó | ¿Se ejecutó? | ¿Cuenta para el CB? | Se compensa ese paso |
|---|---|---|---|
| 4xx | no — dijo que no | **no** | no |
| 5xx (incluye chaos `ERROR`) | no — respondió, y su transacción hizo rollback | sí | no |
| conexión rechazada / host caído / connect timeout | no — el request no salió | sí | no |
| CB abierto | no — ni se llamó | — | no |
| **timeout de respuesta** | **no se sabe** | sí | **sí, por `orderId`** |

El último caso no es teórico: chaos `SLOW` duerme y *después* ejecuta. El
orquestador corta a los 2s y compensa, pero el cobro o el envío se completa
segundos después — con un id que nunca le llegó. Por eso inventory libera por
`orderId`, y payment y shipping exponen `GET ?orderId=` para que la saga
encuentre lo que pudo haberse ejecutado. Compensar algo que no se ejecutó es
inofensivo (todas son idempotentes); no compensar algo que sí se ejecutó deja
un cobro huérfano.

Queda una carrera: si la ejecución tardía llega *después* de la búsqueda, se
escapa. Y si el servicio sigue lento, la búsqueda también da timeout y la orden
termina `COMPENSATION_FAILED` — que es exactamente lo que pasó.

---

## Circuit Breaker

Tres CBs en `order-service`, uno por dependencia: `paymentCB`, `inventoryCB`,
`shippingCB` (el enunciado pide al menos dos). Todos heredan la misma config:

| Parámetro | Exigido | Configurado | Por qué |
|---|---|---|---|
| `failureRateThreshold` | 50% | 50 | — |
| `waitDurationInOpenState` | 10s | 10s | — |
| `slidingWindowSize` | 10 | 10 (`COUNT_BASED`) | — |
| `permittedNumberOfCallsInHalfOpenState` | 3 | 3 | — |
| `minimumNumberOfCalls` | — | 5 | sin él, 1 fallo en 1 llamada ya es 100% y abre |
| `slowCallRateThreshold` / `slowCallDurationThreshold` | — | 50% / 2s | para que el modo chaos `SLOW` también abra |
| `automaticTransitionFromOpenToHalfOpenEnabled` | — | true | pasa a `HALF_OPEN` a los 10s aunque no llegue tráfico — se ve en el panel |
| `ignoreExceptions` | — | `BusinessException` | un 409/422 no cuenta como fallo |

```yaml
resilience4j:
  circuitbreaker:
    circuitBreakerAspectOrder: 1        # CB afuera
    configs:
      default:
        slidingWindowType: COUNT_BASED
        slidingWindowSize: 10
        minimumNumberOfCalls: 5
        failureRateThreshold: 50
        slowCallRateThreshold: 50
        slowCallDurationThreshold: 2s
        waitDurationInOpenState: 10s
        permittedNumberOfCallsInHalfOpenState: 3
        automaticTransitionFromOpenToHalfOpenEnabled: true
        registerHealthIndicator: false    # ver abajo
        ignoreExceptions:
          - gt.cunoc.sa.order.saga.BusinessException
    instances:
      paymentCB:   { baseConfig: default }
      inventoryCB: { baseConfig: default }
      shippingCB:  { baseConfig: default }
  retry:
    retryAspectOrder: 2                 # Retry adentro
    configs:
      default:
        maxAttempts: 2
        waitDuration: 300ms
        ignoreExceptions:
          - gt.cunoc.sa.order.saga.BusinessException
```

**El orden de los aspectos importa.** En Spring Boot 3, por default `@Retry`
puede quedar *afuera* de `@CircuitBreaker`: cada reintento se registra como un
fallo aparte y el circuito abre el doble de rápido de lo configurado. Con
`circuitBreakerAspectOrder=1` / `retryAspectOrder=2` el CB envuelve a los
reintentos y una operación lógica cuenta **una** vez. Verificado: con payment en
`ERROR`, cada orden deja **2** líneas `CHAOS` en payment y suma **1** a
`failedCalls`.

**`registerHealthIndicator: false`, a propósito.** Con `true`, un CB abierto pone
`/actuator/health` del orquestador en DOWN, y el healthcheck del compose lo
marcaría caído porque cayó *una dependencia* — la cascada que el CB existe para
cortar, y la misma razón por la que el `ChaosFilter` excluye `/actuator`. El
estado de los CBs se ve en `/actuator/circuitbreakers` y `/resilience/...`.

**Todas** las llamadas pasan por el CB de su servicio, también las de
compensación: con el circuito abierto, un refund falla al instante en vez de
colgar 2 × 2s.

### Estados

```
              fallos ≥ 50% en las últimas 10 llamadas
   ┌────────┐ ──────────────────────────────────────▶ ┌────────┐
   │ CLOSED │                                         │  OPEN  │ rechaza sin llamar
   └────────┘                                         └────────┘ → fallback inmediato
        ▲                                               ▲    │
        │ las 3 llamadas      ≥ 50% de las 3 fallan     │    │ pasan 10s
        │ de prueba salen bien                          │    ▼
        │                 ┌───────────┐ ────────────────┘    │
        └──────────────── │ HALF_OPEN │ ◀────────────────────┘
                          └───────────┘  deja pasar 3 de prueba
```

### Fallback y patrones complementarios

| Patrón | Cómo está aplicado |
|---|---|
| **Fallback** | `fallbackMethod` (solo en `@CircuitBreaker`, el aspecto de afuera) lanza `ServiceUnavailableException("Servicio de pagos no disponible, intente más tarde")`. La saga lo registra como `FALLBACK` si el circuito estaba abierto o `FAILED` si se agotaron los reintentos, y compensa; la API responde **503** con ese mensaje. Resilience4j invoca el fallback **también para las excepciones ignoradas**, así que lo primero que hace es relanzar la `BusinessException` tal cual — si no, un "sin stock" saldría como "servicio no disponible" |
| **Retry** | 2 intentos, 300 ms entre ellos, solo ante fallos técnicos |
| **Timeout** | `RestClient` sobre el `HttpClient` del JDK: conexión 1s, respuesta 2s. Se eligió ese cliente porque distingue **por tipo** el timeout de conexión (no salió) del de respuesta (incierto) |
| **Bulkhead** | No implementado; se analiza en el informe |
| **Encolar para reintentar** (opcional del enunciado) | No implementado en esta versión; se discute como extensión en el informe |

---

## Recorrido de la demo

Todo se ve desde http://localhost:3000. En este orden aparece el argumento completo:

1. **Flujo feliz** — preset *Flujo feliz*. Timeline: 3 pasos ✓, orden `COMPLETED`,
   el stock del producto baja.
2. **Sin stock** — preset *Sin stock*. Timeline: payment ✓ → inventory ✗ →
   refund ↺ → orden `CANCELLED`. Mirá el panel de CBs: `inventoryCB` **sigue
   `CLOSED`**. Fue un "no" de negocio, no una caída.
3. **Pago rechazado** — preset *Pago rechazado*. Falla el primer paso: no hay
   nada que compensar, solo se cancela la orden.
4. **Envío falla** — preset *Envío falla*. La compensación completa:
   release ↺ → refund ↺ → cancelar.
5. **Caída de Payment** — Chaos → payment → **503**. Botón *Ráfaga ×10* (secuencial).
   Las primeras fallan por 503 (con reintento, ~0.36 s cada una); en la 5ª,
   `paymentCB` llega al 50% y pasa a **`OPEN`**, con la cuenta regresiva a
   `HALF_OPEN` en su tarjeta.
6. **Fallback** — las órdenes 6-10 de la ráfaga, o una nueva: responden en
   **~0.05 s** "Servicio de pagos
   no disponible, intente más tarde". En los logs de payment no aparece ninguna
   request: el CB ni siquiera llamó.
7. **Half-Open** — a los 10s el panel muestra **`HALF_OPEN`**. Chaos → payment →
   `NONE`, y creá 3 órdenes: las 3 pasan y el circuito vuelve a **`CLOSED`**.
   Repetí con chaos todavía en `ERROR`: el CB deja pasar las 3 de prueba, las 3
   fallan, y recién ahí vuelve a `OPEN` — en `HALF_OPEN` no decide con la primera.
8. **Llamadas lentas** — Chaos → shipping → **Lento** (3000 ms) y *Ráfaga ×10*. Es el caso que
   muestra a los dos patrones trabajando juntos:
   - Las 2 primeras órdenes tardan **~8.7s** y terminan **`COMPENSATION_FAILED`**:
     el schedule dio timeout (resultado incierto), release y refund salen bien,
     pero la búsqueda del envío *también* da timeout. En `shipping_db` esos envíos
     quedan `SCHEDULED`: se ejecutaron tarde, y la orden lo declara.
   - Cada una sumó 2 operaciones (schedule + búsqueda), y cada timeout cuenta como
     fallo **y** como llamada lenta: con 4 de 8, `shippingCB` abre por las dos tasas
     a la vez. Una dependencia lenta abre el circuito aunque nunca responda un error.
   - Desde ahí, cada orden termina **`CANCELLED` en ~0.08 s**: el CB rechaza sin
     llamar (no hay incertidumbre) y la saga compensa los **dos** pasos anteriores.
   - Lección: una compensación también necesita que el servicio esté vivo. El CB
     convierte órdenes lentas e inconsistentes en cancelaciones limpias e inmediatas.

---

## Pruebas con curl

```bash
# 1. Flujo feliz
curl -s -X POST localhost:8081/orders -H 'Content-Type: application/json' \
  -d '{"customer":"alice","sku":"LAPTOP-01","quantity":1,"amount":500,"address":"Xela zona 1"}'

# 2. Sin stock (producto sembrado con stock 0)
curl -s -X POST localhost:8081/orders -H 'Content-Type: application/json' \
  -d '{"customer":"alice","sku":"AGOTADO-01","quantity":1,"amount":100,"address":"Xela zona 1"}'

# 3. Pago rechazado
curl -s -X POST localhost:8081/orders -H 'Content-Type: application/json' \
  -d '{"customer":"alice","sku":"LAPTOP-01","quantity":1,"amount":5000,"address":"Xela zona 1"}'

# 4. Envío falla
curl -s -X POST localhost:8081/orders -H 'Content-Type: application/json' \
  -d '{"customer":"alice","sku":"LAPTOP-01","quantity":1,"amount":500,"address":"FAIL"}'

# 5. Abrir paymentCB
curl -s -X POST localhost:8082/chaos -H 'Content-Type: application/json' -d '{"mode":"ERROR"}'
for i in $(seq 1 10); do
  curl -s -o /dev/null -w '%{http_code} %{time_total}s\n' -X POST localhost:8081/orders \
    -H 'Content-Type: application/json' \
    -d '{"customer":"bob","sku":"LAPTOP-01","quantity":1,"amount":500,"address":"Xela"}'
done
curl -s localhost:8081/actuator/circuitbreakers | jq '.circuitBreakers.paymentCB.state'   # "OPEN"

# 6. Recuperar
sleep 10
curl -s -X POST localhost:8082/chaos -H 'Content-Type: application/json' -d '{"mode":"NONE"}'
# 3 órdenes más → state "CLOSED"

```

### Cada servicio aislado (requisito del enunciado)

```bash
J='Content-Type: application/json'

# payment: cobro → repetido (200, mismo id) → rechazo → refund → refund repetido
curl -s -X POST localhost:8082/payments -H "$J" -d '{"orderId":1,"amount":500}'     # 201
curl -s -X POST localhost:8082/payments -H "$J" -d '{"orderId":1,"amount":500}'     # 200
curl -s -X POST localhost:8082/payments -H "$J" -d '{"orderId":2,"amount":5000}'    # 422
curl -s -X POST localhost:8082/payments/1/refund                                    # 200 REFUNDED
curl -s -X POST localhost:8082/payments/1/refund                                    # 200 sin cambios

# inventory: reserva → sin stock → release → release sin reserva
curl -s -X POST localhost:8083/inventory/reserve -H "$J" -d '{"orderId":1,"sku":"LAPTOP-01","quantity":2}'   # 201
curl -s -X POST localhost:8083/inventory/reserve -H "$J" -d '{"orderId":2,"sku":"AGOTADO-01","quantity":1}'  # 409
curl -s -X POST localhost:8083/inventory/release -H "$J" -d '{"orderId":1}'         # 200 stock devuelto
curl -s -X POST localhost:8083/inventory/release -H "$J" -d '{"orderId":777}'       # 200 nada que liberar
curl -s localhost:8083/inventory/products

# shipping: programar → dirección sin cobertura → cancelar
curl -s -X POST localhost:8084/shipping/schedule -H "$J" -d '{"orderId":1,"address":"Xela zona 1"}'  # 201
curl -s -X POST localhost:8084/shipping/schedule -H "$J" -d '{"orderId":2,"address":"FAIL"}'         # 422
curl -s -X DELETE localhost:8084/shipping/1                                                          # 200 CANCELLED

# chaos
curl -s -X POST localhost:8082/chaos -H "$J" -d '{"mode":"ERROR"}'
curl -s -X POST localhost:8082/payments -H "$J" -d '{"orderId":9,"amount":10}'      # 503
curl -s localhost:8082/actuator/health                                              # sigue UP
curl -s -X POST localhost:8082/chaos -H "$J" -d '{"mode":"NONE"}'

# ver las tablas
docker compose exec postgres psql -U postgres -d inventory_db -c 'table products'
```

En la ráfaga del paso 5, fijate en `time_total`: las primeras tardan (reintento +
503), las últimas son casi instantáneas. Ese salto es el circuito abriéndose.

---

## Estructura

```
PRACTICA_5/
├── README.md
├── PRACTICA_5.md                 # enunciado
├── docker-compose.yml
├── db/init.sql                   # las 4 bases
├── docs/                         # informe técnico + guion del video
├── order-service/                # :8081 — orquestador + Resilience4j (Fase 3-4)
│   └── src/main/java/gt/cunoc/sa/
│       ├── web/             ApiError · ApiExceptionHandler · NotFound/ConflictException (propios, no los de los hojas)
│       └── order/
│           ├── controller/  OrderController · ResilienceController
│           ├── saga/        OrderSagaOrchestrator · SagaLog · BusinessException · TechnicalException · ServiceUnavailableException
│           ├── client/      PaymentClient · InventoryClient · ShippingClient · RemoteErrors · Fallbacks · ClientConfig
│           ├── service/     OrderService
│           ├── domain/      Order · SagaStep
│           ├── repository/
│           └── dto/         incluye RemoteDtos: copia de los contratos de los hojas, no un jar compartido
├── payment-service/              # :8082
├── inventory-service/            # :8083
├── shipping-service/             # :8084
│   ├── pom.xml · Dockerfile
│   └── src/main/
│       ├── java/gt/cunoc/sa/
│       │   ├── <Servicio>Application.java
│       │   ├── chaos/        ChaosMode · ChaosState · ChaosFilter · ChaosController   (idéntico ×3)
│       │   ├── web/          ApiError · BusinessException · NotFoundException · ApiExceptionHandler (idéntico ×3)
│       │   └── <servicio>/   controller/ · service/ · domain/ · repository/ · dto/
│       └── resources/        application.yml · db/migration/V1__init.sql
└── frontend/                     # :3000 — React + Vite, servido por nginx
    ├── nginx.conf · vite.config.ts   # el mismo mapeo de /api, en producción y en desarrollo
    └── src/
        ├── api.ts                # tipos espejo de los DTOs + fetch
        ├── usePolling.ts
        └── components/           # NewOrder · Orders · SagaTimeline · CircuitBreakers · Chaos
```

`docs/` va **dentro** de `PRACTICA_5/`, para que quede en el repositorio de la
entrega.
