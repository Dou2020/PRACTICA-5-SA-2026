package gt.cunoc.sa.shipping.dto;

/** {@code created} distingue el envío nuevo (201) del que ya existía para esa orden (200). */
public record ScheduleResult(ShipmentResponse shipment, boolean created) {
}
