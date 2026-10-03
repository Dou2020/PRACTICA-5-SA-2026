package gt.cunoc.sa.order.saga;

/**
 * Lo que lanza el fallback de los clientes cuando la llamada no salió bien por
 * razones técnicas: con el circuito abierto (ni se llamó) o tras agotar los
 * reintentos. Su mensaje es la "respuesta controlada" que pide el enunciado y
 * llega tal cual al cliente de POST /orders, con 503.
 *
 * No corta la saga por su cuenta: el orquestador lo trata como cualquier otro
 * fallo y compensa. El CB decide cuándo dejar de intentar; la saga decide qué
 * hacer con lo que quedó a medias.
 */
public class ServiceUnavailableException extends RuntimeException {

    private final boolean circuitOpen;
    private final boolean uncertain;

    public ServiceUnavailableException(String message, boolean circuitOpen, boolean uncertain, Throwable cause) {
        super(message, cause);
        this.circuitOpen = circuitOpen;
        this.uncertain = uncertain;
    }

    /** true = rechazada por el CB sin llamar; false = se llamó y falló tras los reintentos. */
    public boolean isCircuitOpen() {
        return circuitOpen;
    }

    public boolean isUncertain() {
        return uncertain;
    }
}
