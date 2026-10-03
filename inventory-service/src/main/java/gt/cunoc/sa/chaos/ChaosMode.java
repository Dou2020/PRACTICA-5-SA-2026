package gt.cunoc.sa.chaos;

/**
 * NONE: normal. ERROR: responde 503 sin tocar el negocio. SLOW: duerme y sigue.
 *
 * Los dos modos simulan fallos TÉCNICOS, que son los que el Circuit Breaker
 * tiene que contar. Los fallos de negocio no se simulan acá: salen de los datos
 * (sin stock, monto alto, dirección con FAIL).
 */
public enum ChaosMode {
    NONE,
    ERROR,
    SLOW
}
