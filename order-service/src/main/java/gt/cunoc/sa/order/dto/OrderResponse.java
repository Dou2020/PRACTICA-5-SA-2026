package gt.cunoc.sa.order.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import gt.cunoc.sa.order.domain.Order;
import gt.cunoc.sa.order.domain.OrderStatus;
import gt.cunoc.sa.order.domain.SagaStep;

/**
 * La orden con su timeline. Es también el cuerpo de POST /orders aunque la
 * saga se haya cancelado: el status HTTP dice cómo terminó y {@code steps} dice
 * por qué, así el frontend pinta el timeline sin pedirlo otra vez.
 */
public record OrderResponse(Long id, String customer, String sku, Integer quantity, BigDecimal amount,
                            String address, OrderStatus status, String failureCode, String failureReason,
                            Long paymentId, Long reservationId, Long shipmentId,
                            Instant createdAt, Instant updatedAt, List<SagaStepResponse> steps) {

    public static OrderResponse of(Order o, List<SagaStep> steps) {
        return new OrderResponse(o.getId(), o.getCustomer(), o.getSku(), o.getQuantity(), o.getAmount(),
                o.getAddress(), o.getStatus(), o.getFailureCode(), o.getFailureReason(),
                o.getPaymentId(), o.getReservationId(), o.getShipmentId(),
                o.getCreatedAt(), o.getUpdatedAt(), steps.stream().map(SagaStepResponse::of).toList());
    }
}
