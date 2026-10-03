package gt.cunoc.sa.chaos;

import java.util.concurrent.atomic.AtomicReference;

import org.springframework.stereotype.Component;

/**
 * Modo de chaos vigente, en memoria. Un reinicio vuelve a NONE.
 *
 * Modo y demora se guardan juntos en un record inmutable detrás de un
 * AtomicReference: con dos campos sueltos, un request podría leer el modo nuevo
 * con la demora vieja mientras se cambia.
 */
@Component
public class ChaosState {

    public record Config(ChaosMode mode, long delayMs) {
    }

    private final AtomicReference<Config> current = new AtomicReference<>(new Config(ChaosMode.NONE, 0));

    public Config get() {
        return current.get();
    }

    public Config set(ChaosMode mode, long delayMs) {
        Config config = new Config(mode, Math.max(0, delayMs));
        current.set(config);
        return config;
    }
}
