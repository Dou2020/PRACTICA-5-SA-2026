package gt.cunoc.sa.order.service;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

import org.springframework.stereotype.Component;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;

/**
 * Historial propio de los cambios de estado de los CBs, para el panel.
 *
 * Existe porque /actuator/circuitbreakerevents no alcanza: mezcla las
 * transiciones con un evento por cada llamada en un buffer acotado (una ráfaga
 * de órdenes las desplaza), y su timestamp trae un sufijo "[Etc/UTC]" que un
 * navegador no parsea. Acá se guardan solo las transiciones, con un Instant.
 */
@Component
public class CircuitBreakerTransitions {

    public record Transition(String name, String from, String to, Instant at) {
    }

    private static final int MAX = 50;

    private final Deque<Transition> transitions = new ArrayDeque<>();

    public CircuitBreakerTransitions(CircuitBreakerRegistry registry) {
        registry.getAllCircuitBreakers().forEach(this::subscribe);
        registry.getEventPublisher().onEntryAdded(e -> subscribe(e.getAddedEntry()));
    }

    private void subscribe(CircuitBreaker cb) {
        cb.getEventPublisher().onStateTransition(e -> add(new Transition(e.getCircuitBreakerName(),
                e.getStateTransition().getFromState().name(), e.getStateTransition().getToState().name(),
                e.getCreationTime().toInstant())));
    }

    private synchronized void add(Transition t) {
        transitions.addFirst(t);
        if (transitions.size() > MAX) {
            transitions.removeLast();
        }
    }

    /** La más reciente primero. */
    public synchronized List<Transition> recent() {
        return List.copyOf(transitions);
    }
}
