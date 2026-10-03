package gt.cunoc.sa.web;

/**
 * Mismo formato de error que los servicios hoja ({"error", "message"}), para
 * que el frontend lea igual a todos. Es una copia, no un import: no hay
 * librería compartida entre microservicios.
 */
public record ApiError(String error, String message) {
}
