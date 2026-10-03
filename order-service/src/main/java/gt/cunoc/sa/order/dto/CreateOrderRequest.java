package gt.cunoc.sa.order.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record CreateOrderRequest(@NotBlank @Size(max = 80) String customer,
                                 @NotBlank @Size(max = 40) String sku,
                                 @NotNull @Positive Integer quantity,
                                 @NotNull @Positive BigDecimal amount,
                                 @NotBlank @Size(max = 255) String address) {
}
