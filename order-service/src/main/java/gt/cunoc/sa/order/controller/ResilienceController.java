package gt.cunoc.sa.order.controller;

import java.util.Comparator;
import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import gt.cunoc.sa.order.service.CircuitBreakerTransitions;
import gt.cunoc.sa.order.service.CircuitBreakerTransitions.Transition;
import gt.cunoc.sa.web.NotFoundException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;

/**
 * Estado de los Circuit Breakers en un formato plano para el frontend. Lo mismo
 * está en /actuator/circuitbreakers, pero anidado y con nombres de Micrometer.
 */
@RestController
@RequestMapping("/resilience/circuit-breakers")
public class ResilienceController {

    public record CircuitBreakerStatus(String name, String state, float failureRate, float slowCallRate,
                                       int bufferedCalls, int failedCalls, int slowCalls,
                                       long notPermittedCalls) {

        static CircuitBreakerStatus of(CircuitBreaker cb) {
            CircuitBreaker.Metrics m = cb.getMetrics();
            // -1 = todavía no hay minimumNumberOfCalls llamadas para calcular la tasa.
            return new CircuitBreakerStatus(cb.getName(), cb.getState().name(), m.getFailureRate(),
                    m.getSlowCallRate(), m.getNumberOfBufferedCalls(), m.getNumberOfFailedCalls(),
                    m.getNumberOfSlowCalls(), m.getNumberOfNotPermittedCalls());
        }
    }

    private final CircuitBreakerRegistry registry;
    private final CircuitBreakerTransitions transitions;

    public ResilienceController(CircuitBreakerRegistry registry, CircuitBreakerTransitions transitions) {
        this.registry = registry;
        this.transitions = transitions;
    }

    @GetMapping
    public List<CircuitBreakerStatus> list() {
        return registry.getAllCircuitBreakers().stream()
                .sorted(Comparator.comparing(CircuitBreaker::getName))
                .map(CircuitBreakerStatus::of)
                .toList();
    }

    /**
     * Últimos cambios de estado, el más reciente primero. El panel saca de acá
     * el historial y el instante en que abrió cada CB (cuenta regresiva a
     * HALF_OPEN). Incluye los provocados por reset.
     */
    @GetMapping("/transitions")
    public List<Transition> transitions() {
        return transitions.recent();
    }

    /** Vuelve el CB a CLOSED y borra sus métricas, para repetir la demo sin esperar. */
    @PostMapping("/{name}/reset")
    public CircuitBreakerStatus reset(@PathVariable String name) {
        CircuitBreaker cb = registry.find(name)
                .orElseThrow(() -> new NotFoundException("No existe el circuit breaker " + name));
        cb.reset();
        return CircuitBreakerStatus.of(cb);
    }
}
