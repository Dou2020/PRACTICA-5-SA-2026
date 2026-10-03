package gt.cunoc.sa.payment.dto;

/**
 * {@code created} distingue el cobro nuevo (201) del que ya existía para esa
 * orden (200). El orquestador trata los dos igual: en ambos el pago está hecho.
 */
public record ChargeResult(PaymentResponse payment, boolean created) {
}
