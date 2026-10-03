package gt.cunoc.sa.web;

/**
 * Cuerpo de todo error de la API: {"error": "OUT_OF_STOCK", "message": "..."}.
 *
 * El orquestador decide con el status HTTP (409/422 = negocio, 5xx = técnico);
 * {@code error} es un código estable para que el frontend no parsee textos, y
 * {@code message} ya viene redactado para mostrarse tal cual en el timeline.
 */
public record ApiError(String error, String message) {
}
