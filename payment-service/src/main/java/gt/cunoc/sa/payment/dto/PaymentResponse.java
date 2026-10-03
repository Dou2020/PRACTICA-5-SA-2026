package gt.cunoc.sa.payment.dto;

import java.math.BigDecimal;
import java.time.Instant;

import gt.cunoc.sa.payment.domain.Payment;
import gt.cunoc.sa.payment.domain.PaymentStatus;

public record PaymentResponse(Long id, Long orderId, BigDecimal amount, PaymentStatus status,
                              Instant createdAt, Instant updatedAt) {

    public static PaymentResponse of(Payment p) {
        return new PaymentResponse(p.getId(), p.getOrderId(), p.getAmount(), p.getStatus(),
                p.getCreatedAt(), p.getUpdatedAt());
    }
}
