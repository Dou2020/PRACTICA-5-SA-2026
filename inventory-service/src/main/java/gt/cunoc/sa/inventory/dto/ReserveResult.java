package gt.cunoc.sa.inventory.dto;

/** {@code created} distingue la reserva nueva (201) de la que ya existía para esa orden (200). */
public record ReserveResult(ReservationResponse reservation, boolean created) {
}
