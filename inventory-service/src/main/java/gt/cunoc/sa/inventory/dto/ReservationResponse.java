package gt.cunoc.sa.inventory.dto;

import java.time.Instant;

import gt.cunoc.sa.inventory.domain.Reservation;
import gt.cunoc.sa.inventory.domain.ReservationStatus;

public record ReservationResponse(Long id, Long orderId, String sku, Integer quantity,
                                  ReservationStatus status, Instant createdAt, Instant updatedAt) {

    public static ReservationResponse of(Reservation r) {
        return new ReservationResponse(r.getId(), r.getOrderId(), r.getSku(), r.getQuantity(),
                r.getStatus(), r.getCreatedAt(), r.getUpdatedAt());
    }
}
