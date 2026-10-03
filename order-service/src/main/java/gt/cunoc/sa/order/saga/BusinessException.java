package gt.cunoc.sa.order.saga;

import org.springframework.http.HttpStatusCode;

/**
 * Un servicio hoja respondió 4xx: está sano, pero dijo que no (sin stock, pago
 * rechazado, dirección sin cobertura). La operación no se ejecutó.
 *
 * Es la frontera de la práctica: la saga compensa los pasos anteriores, pero el
 * Circuit Breaker NO lo cuenta como fallo (va en ignoreExceptions del CB y del
 * Retry). Si contara, diez pedidos de un producto agotado apagarían el
 * inventario para todos.
 *
 * No es la web.BusinessException de los servicios hoja: cada microservicio se
 * construye solo y no comparten clases. Esta lleva el status que vino del
 * remoto para devolvérselo igual al cliente de POST /orders.
 */
public class BusinessException extends RuntimeException {

    private final HttpStatusCode status;
    private final String code;

    public BusinessException(HttpStatusCode status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public HttpStatusCode getStatus() {
        return status;
    }

    public String getCode() {
        return code;
    }
}
