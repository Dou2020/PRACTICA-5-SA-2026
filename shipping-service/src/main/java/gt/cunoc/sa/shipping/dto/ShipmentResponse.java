package gt.cunoc.sa.shipping.dto;

import java.time.Instant;

import gt.cunoc.sa.shipping.domain.Shipment;
import gt.cunoc.sa.shipping.domain.ShipmentStatus;

public record ShipmentResponse(Long id, Long orderId, String address, ShipmentStatus status,
                               String trackingCode, Instant createdAt, Instant updatedAt) {

    public static ShipmentResponse of(Shipment s) {
        return new ShipmentResponse(s.getId(), s.getOrderId(), s.getAddress(), s.getStatus(),
                s.getTrackingCode(), s.getCreatedAt(), s.getUpdatedAt());
    }
}
