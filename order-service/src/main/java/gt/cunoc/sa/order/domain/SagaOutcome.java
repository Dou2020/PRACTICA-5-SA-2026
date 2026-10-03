package gt.cunoc.sa.order.domain;

public enum SagaOutcome {
    OK,
    FAILED,
    /** El Circuit Breaker estaba abierto: la llamada ni siquiera se hizo. */
    FALLBACK
}
