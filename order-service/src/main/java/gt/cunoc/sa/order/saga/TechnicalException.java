package gt.cunoc.sa.order.saga;

/**
 * El servicio no respondió bien: 5xx, timeout o sin conexión. Cuenta como fallo
 * para el Circuit Breaker y dispara reintentos.
 *
 * {@code uncertain} es lo que la saga necesita saber para compensar bien:
 * <ul>
 *   <li>false — se sabe que NO se ejecutó: respondió con error (su transacción
 *       hizo rollback) o la conexión ni se estableció.</li>
 *   <li>true — no se sabe: el request salió y la respuesta no llegó a tiempo.
 *       Con chaos SLOW el servicio duerme y después ejecuta igual, así que el
 *       cobro o el envío pueden aparecer segundos después de que el
 *       orquestador se rindió. Ese paso también se compensa, buscándolo por
 *       orderId.</li>
 * </ul>
 */
public class TechnicalException extends RuntimeException {

    private final boolean uncertain;

    public TechnicalException(String message, boolean uncertain, Throwable cause) {
        super(message, cause);
        this.uncertain = uncertain;
    }

    public boolean isUncertain() {
        return uncertain;
    }
}
