package gt.cunoc.sa.order.domain;

public enum OrderStatus {
    PENDING,
    COMPLETED,
    CANCELLED,
    /**
     * Alguna compensación no se pudo ejecutar: el sistema quedó inconsistente y
     * requiere intervención manual. Es el límite real de la saga — da
     * consistencia eventual, y a veces "eventual" depende de una persona.
     */
    COMPENSATION_FAILED
}
