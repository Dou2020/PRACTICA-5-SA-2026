package gt.cunoc.sa.order.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import gt.cunoc.sa.order.saga.BusinessException;
import gt.cunoc.sa.order.saga.ServiceUnavailableException;
import gt.cunoc.sa.order.saga.TechnicalException;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;

/**
 * Lo que hacen todos los fallbackMethod de los clientes.
 *
 * Resilience4j invoca el fallback ante CUALQUIER excepción, incluidas las que
 * el CB ignora. Por eso lo primero es devolver la BusinessException tal cual:
 * sin esa línea, un "sin stock" llegaría al cliente como "servicio no
 * disponible", y la saga lo registraría como caída técnica.
 */
final class Fallbacks {

    private static final Logger log = LoggerFactory.getLogger(Fallbacks.class);

    private Fallbacks() {
    }

    static RuntimeException translate(String label, String operation, Throwable t) {
        if (t instanceof BusinessException business) {
            return business;
        }
        String message = "Servicio de " + label + " no disponible, intente más tarde";
        if (t instanceof CallNotPermittedException) {
            log.warn("FALLBACK {}: circuito abierto, no se llama", operation);
            return new ServiceUnavailableException(message, true, false, t);
        }
        boolean uncertain = !(t instanceof TechnicalException te) || te.isUncertain();
        log.warn("FALLBACK {}: {}", operation, t.getMessage());
        return new ServiceUnavailableException(message + " (" + t.getMessage() + ")", false, uncertain, t);
    }
}
