package gt.cunoc.sa.payment.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record ChargeRequest(@NotNull Long orderId, @NotNull @Positive BigDecimal amount) {
}
