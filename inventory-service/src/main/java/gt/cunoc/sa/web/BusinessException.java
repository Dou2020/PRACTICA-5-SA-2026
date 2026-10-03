package gt.cunoc.sa.web;

import org.springframework.http.HttpStatus;

/**
 * Un "no" del negocio: el servicio está sano y respondió, pero la operación no
 * procede (sin stock, pago rechazado, dirección inválida).
 *
 * Se responde siempre con 4xx (409/422). Esa frontera es la tesis de la
 * práctica: del lado del orquestador un 4xx dispara compensaciones de la saga
 * pero NO cuenta como fallo para el Circuit Breaker; un 5xx sí. Si un error de
 * negocio saliera como 500, diez pedidos de un producto agotado apagarían el
 * inventario para todos.
 */
public class BusinessException extends RuntimeException {

    private final HttpStatus status;
    private final String code;

    public BusinessException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getCode() {
        return code;
    }
}
