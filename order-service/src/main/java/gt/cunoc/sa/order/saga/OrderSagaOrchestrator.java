package gt.cunoc.sa.order.saga;

import java.util.ArrayDeque;
import java.util.Deque;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Service;

import gt.cunoc.sa.order.client.InventoryClient;
import gt.cunoc.sa.order.client.PaymentClient;
import gt.cunoc.sa.order.client.ShippingClient;
import gt.cunoc.sa.order.domain.Order;
import gt.cunoc.sa.order.domain.SagaAction;
import gt.cunoc.sa.order.domain.SagaOutcome;
import gt.cunoc.sa.order.domain.SagaStepName;
import gt.cunoc.sa.order.dto.CreateOrderRequest;
import gt.cunoc.sa.order.dto.RemoteDtos.Payment;
import gt.cunoc.sa.order.dto.RemoteDtos.Reservation;
import gt.cunoc.sa.order.dto.RemoteDtos.Shipment;
import gt.cunoc.sa.order.service.OrderService;

/**
 * Saga por orquestación: este servicio decide el orden de los pasos y, si uno
 * falla, deshace en orden inverso los que ya se confirmaron.
 *
 * NO es @Transactional, y es a propósito: no existe una transacción que cubra
 * payment_db, inventory_db y shipping_db — ese es el problema que la saga
 * resuelve. Cada cambio local (la orden, cada fila del timeline) se confirma
 * apenas ocurre, y ninguna transacción de BD queda abierta durante una llamada
 * HTTP que puede tardar segundos.
 */
@Service
public class OrderSagaOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(OrderSagaOrchestrator.class);

    private final OrderService orders;
    private final SagaLog sagaLog;
    private final PaymentClient payment;
    private final InventoryClient inventory;
    private final ShippingClient shipping;

    public OrderSagaOrchestrator(OrderService orders, SagaLog sagaLog, PaymentClient payment,
                                 InventoryClient inventory, ShippingClient shipping) {
        this.orders = orders;
        this.sagaLog = sagaLog;
        this.payment = payment;
        this.inventory = inventory;
        this.shipping = shipping;
    }

    public SagaResult run(CreateOrderRequest request) {
        Order order = orders.create(request);
        Long id = order.getId();
        sagaLog.record(id, SagaStepName.ORDER, SagaAction.EXECUTE, SagaOutcome.OK, "Orden creada (PENDING)");

        // Pila de pasos a compensar: se apila al confirmar cada uno, se desapila
        // en orden inverso.
        Deque<SagaStepName> toCompensate = new ArrayDeque<>();
        SagaStepName current = SagaStepName.PAYMENT;
        try {
            Payment p = payment.charge(id, request.amount());
            orders.update(id, o -> o.paid(p.id()));
            sagaLog.record(id, current, SagaAction.EXECUTE, SagaOutcome.OK,
                    "Cobro " + p.id() + " por " + request.amount() + " (" + p.status() + ")");
            toCompensate.push(current);

            current = SagaStepName.INVENTORY;
            Reservation r = inventory.reserve(id, request.sku(), request.quantity());
            orders.update(id, o -> o.reserved(r.id()));
            sagaLog.record(id, current, SagaAction.EXECUTE, SagaOutcome.OK,
                    "Reserva " + r.id() + ": " + request.sku() + " x" + request.quantity());
            toCompensate.push(current);

            current = SagaStepName.SHIPPING;
            Shipment s = shipping.schedule(id, request.address());
            orders.update(id, o -> o.shipped(s.id()));
            sagaLog.record(id, current, SagaAction.EXECUTE, SagaOutcome.OK,
                    "Envío programado, tracking " + s.trackingCode());

            order = orders.update(id, Order::complete);
            sagaLog.record(id, SagaStepName.ORDER, SagaAction.EXECUTE, SagaOutcome.OK, "Orden COMPLETED");
            return new SagaResult(order, HttpStatus.CREATED);
        } catch (RuntimeException e) {
            Failure f = Failure.of(current, e);
            sagaLog.record(id, current, SagaAction.EXECUTE, f.outcome(),
                    f.uncertain() ? f.reason() + " — puede haberse ejecutado: se compensa por orderId" : f.reason());
            orders.update(id, o -> o.failed(f.code(), f.reason()));
            if (f.uncertain()) {
                toCompensate.push(current);
            }
            return compensate(id, toCompensate, f);
        }
    }

    /**
     * Deshace en orden inverso. Una compensación que falla no detiene a las
     * demás: devolver el dinero no depende de haber liberado el stock. Pero
     * tampoco se esconde: si alguna falló, la orden queda COMPENSATION_FAILED.
     */
    private SagaResult compensate(Long id, Deque<SagaStepName> toCompensate, Failure failure) {
        boolean allCompensated = true;
        while (!toCompensate.isEmpty()) {
            SagaStepName step = toCompensate.pop();
            try {
                sagaLog.record(id, step, SagaAction.COMPENSATE, SagaOutcome.OK, compensateStep(id, step));
            } catch (RuntimeException e) {
                allCompensated = false;
                Failure f = Failure.of(step, e);
                sagaLog.record(id, step, SagaAction.COMPENSATE, f.outcome(), f.reason());
            }
        }

        if (!allCompensated) {
            Order order = orders.update(id, Order::compensationFailed);
            sagaLog.record(id, SagaStepName.ORDER, SagaAction.COMPENSATE, SagaOutcome.FAILED,
                    "Compensación incompleta: requiere intervención manual");
            log.error("Orden {} quedó COMPENSATION_FAILED", id);
            return new SagaResult(order, HttpStatus.INTERNAL_SERVER_ERROR);
        }
        Order order = orders.cancel(id, failure.code(), failure.reason());
        sagaLog.record(id, SagaStepName.ORDER, SagaAction.COMPENSATE, SagaOutcome.OK,
                "Orden CANCELLED: " + failure.reason());
        return new SagaResult(order, failure.status());
    }

    /**
     * Las compensaciones por id usan el que guardó la orden al confirmar el
     * paso. Si no hay id, el paso quedó incierto (timeout): se busca por
     * orderId, porque pudo ejecutarse después de que el orquestador se rindió.
     * Todas son idempotentes en el servicio hoja.
     */
    private String compensateStep(Long id, SagaStepName step) {
        return switch (step) {
            case PAYMENT -> compensatePayment(id);
            case INVENTORY -> inventory.release(id).detail();
            case SHIPPING -> compensateShipping(id);
            case ORDER -> throw new IllegalStateException("El paso ORDER se compensa al final, no en la pila");
        };
    }

    private String compensatePayment(Long orderId) {
        Long paymentId = orders.paymentId(orderId);
        if (paymentId == null) {
            var found = payment.findByOrder(orderId);
            if (found.isEmpty()) {
                return "No se había cobrado: nada que reembolsar";
            }
            paymentId = found.get().id();
        }
        Payment refunded = payment.refund(paymentId);
        return "Pago " + refunded.id() + " " + refunded.status();
    }

    private String compensateShipping(Long orderId) {
        Long shipmentId = orders.shipmentId(orderId);
        if (shipmentId == null) {
            var found = shipping.findByOrder(orderId);
            if (found.isEmpty()) {
                return "No se había programado: nada que cancelar";
            }
            shipmentId = found.get().id();
        }
        Shipment cancelled = shipping.cancel(shipmentId);
        return "Envío " + cancelled.trackingCode() + " " + cancelled.status();
    }

    /** Cómo falló un paso, en los términos que le importan a la saga. */
    private record Failure(SagaOutcome outcome, String code, String reason, HttpStatusCode status,
                           boolean uncertain) {

        static Failure of(SagaStepName step, RuntimeException e) {
            if (e instanceof BusinessException b) {
                return new Failure(SagaOutcome.FAILED, b.getCode(), b.getMessage(), b.getStatus(), false);
            }
            String unavailable = step + "_UNAVAILABLE";
            if (e instanceof ServiceUnavailableException s) {
                return new Failure(s.isCircuitOpen() ? SagaOutcome.FALLBACK : SagaOutcome.FAILED, unavailable,
                        s.getMessage(), HttpStatus.SERVICE_UNAVAILABLE, s.isUncertain());
            }
            if (e instanceof TechnicalException t) {
                return new Failure(SagaOutcome.FAILED, unavailable, t.getMessage(),
                        HttpStatus.SERVICE_UNAVAILABLE, t.isUncertain());
            }
            // Un error inesperado: no se sabe en qué punto cortó, así que se
            // asume lo peor (incierto) y se compensa también este paso.
            log.error("Error inesperado en el paso {}", step, e);
            return new Failure(SagaOutcome.FAILED, "INTERNAL", "Error inesperado: " + e.getMessage(),
                    HttpStatus.INTERNAL_SERVER_ERROR, true);
        }
    }
}
