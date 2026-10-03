package gt.cunoc.sa.inventory.dto;

import jakarta.validation.constraints.NotNull;

/**
 * La compensación se pide por orderId, no por id de reserva: si el reserve dio
 * timeout, el orquestador nunca supo el id, pero sí sabe su orden.
 */
public record ReleaseRequest(@NotNull Long orderId) {
}
