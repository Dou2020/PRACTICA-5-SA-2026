package gt.cunoc.sa.shipping.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ScheduleRequest(@NotNull Long orderId, @NotBlank @Size(max = 255) String address) {
}
