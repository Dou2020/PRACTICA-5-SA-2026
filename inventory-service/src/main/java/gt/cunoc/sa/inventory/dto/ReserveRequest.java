package gt.cunoc.sa.inventory.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record ReserveRequest(@NotNull Long orderId, @NotBlank String sku, @NotNull @Positive Integer quantity) {
}
