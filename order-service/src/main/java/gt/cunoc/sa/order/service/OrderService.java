package gt.cunoc.sa.order.service;

import java.util.List;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import gt.cunoc.sa.order.domain.Order;
import gt.cunoc.sa.order.domain.OrderStatus;
import gt.cunoc.sa.order.dto.CreateOrderRequest;
import gt.cunoc.sa.order.dto.OrderResponse;
import gt.cunoc.sa.order.dto.OrderSummary;
import gt.cunoc.sa.order.repository.OrderRepository;
import gt.cunoc.sa.order.repository.SagaStepRepository;
import gt.cunoc.sa.web.ConflictException;
import gt.cunoc.sa.web.NotFoundException;

/**
 * La base local del orquestador. Cada método es su propia transacción corta:
 * el orquestador los llama entre una llamada HTTP y la siguiente, y ninguna
 * transacción de BD queda abierta mientras se espera a otro servicio.
 */
@Service
public class OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderService.class);

    private final OrderRepository orders;
    private final SagaStepRepository steps;

    public OrderService(OrderRepository orders, SagaStepRepository steps) {
        this.orders = orders;
        this.steps = steps;
    }

    @Transactional
    public Order create(CreateOrderRequest r) {
        Order order = orders.save(Order.create(r.customer(), r.sku(), r.quantity(), r.amount(), r.address()));
        log.info("Orden {} creada (PENDING): {} x{} para {}", order.getId(), r.sku(), r.quantity(), r.customer());
        return order;
    }

    /** Aplica un cambio a la orden y lo guarda; devuelve la versión guardada. */
    @Transactional
    public Order update(Long id, Consumer<Order> change) {
        Order order = load(id);
        change.accept(order);
        return order;
    }

    /**
     * Compensación del paso Order: la usa la saga y la expone DELETE
     * /orders/{id}. Idempotente sobre una orden ya cancelada.
     *
     * Una orden COMPLETED no se cancela acá: sus pasos remotos siguen
     * confirmados, y marcarla CANCELLED dejaría cobro, reserva y envío vigentes
     * de una orden "cancelada". Revertir una compra terminada es otro caso de
     * uso (devolución), no la compensación de una saga en curso.
     */
    @Transactional
    public Order cancel(Long id, String code, String reason) {
        Order order = load(id);
        if (order.getStatus() == OrderStatus.COMPLETED) {
            throw new ConflictException("ORDER_COMPLETED",
                    "La orden " + id + " ya se completó: cancelarla dejaría el cobro, la reserva y el envío vigentes");
        }
        if (order.cancel(code, reason)) {
            log.info("Orden {} cancelada: {}", id, order.getFailureReason());
        } else {
            log.info("Orden {} ya estaba cancelada → sin cambios", id);
        }
        return order;
    }

    /** Id del cobro confirmado; null si el paso no llegó a confirmarse. */
    @Transactional(readOnly = true)
    public Long paymentId(Long id) {
        return load(id).getPaymentId();
    }

    /** Id del envío confirmado; null si el paso no llegó a confirmarse. */
    @Transactional(readOnly = true)
    public Long shipmentId(Long id) {
        return load(id).getShipmentId();
    }

    @Transactional(readOnly = true)
    public OrderResponse get(Long id) {
        return OrderResponse.of(load(id), steps.findByOrderIdOrderByIdAsc(id));
    }

    @Transactional(readOnly = true)
    public List<OrderSummary> list() {
        return orders.findAllByOrderByIdDesc().stream().map(OrderSummary::of).toList();
    }

    private Order load(Long id) {
        return orders.findById(id).orElseThrow(() -> new NotFoundException("No existe la orden " + id));
    }
}
