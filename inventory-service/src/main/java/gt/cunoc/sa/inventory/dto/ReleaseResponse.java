package gt.cunoc.sa.inventory.dto;

/**
 * Resultado de la compensación. {@code reservation} es null cuando no había
 * nada que liberar; {@code detail} dice qué pasó en los tres casos posibles.
 */
public record ReleaseResponse(Long orderId, String detail, ReservationResponse reservation) {
}
