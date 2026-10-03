package gt.cunoc.sa.order.dto;

import java.math.BigDecimal;
import java.time.Instant;

import gt.cunoc.sa.order.domain.Order;
import gt.cunoc.sa.order.domain.OrderStatus;

/** Fila del listado: sin timeline, para no traer todos los saga_steps de todas las órdenes. */
public record OrderSummary(Long id, String customer, String sku, Integer quantity, BigDecimal amount,
                           OrderStatus status, String failureCode, String failureReason, Instant createdAt) {

    public static OrderSummary of(Order o) {
        return new OrderSummary(o.getId(), o.getCustomer(), o.getSku(), o.getQuantity(), o.getAmount(),
                o.getStatus(), o.getFailureCode(), o.getFailureReason(), o.getCreatedAt());
    }
}
