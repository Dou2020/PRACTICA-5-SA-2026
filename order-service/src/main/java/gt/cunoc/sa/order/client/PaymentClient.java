package gt.cunoc.sa.order.client;

import java.math.BigDecimal;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import gt.cunoc.sa.order.dto.RemoteDtos.ChargeRequest;
import gt.cunoc.sa.order.dto.RemoteDtos.Payment;
import gt.cunoc.sa.order.saga.BusinessException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;

/**
 * Todas las llamadas a payment pasan por paymentCB, también las de
 * compensación: con el circuito abierto, un refund falla al instante en vez de
 * colgar 2 × 2s, y la saga lo registra como compensación fallida.
 *
 * El fallbackMethod va solo en @CircuitBreaker (el aspecto de afuera). Si
 * estuviera también en @Retry, el reintento vería la respuesta del fallback en
 * lugar de la excepción y nunca reintentaría.
 */
@Component
public class PaymentClient {

    private static final String SERVICE = "payment-service";
    private static final String LABEL = "pagos";

    private final RestClient http;
    private final RemoteErrors errors;

    public PaymentClient(RestClient.Builder builder, ClientHttpRequestFactory remoteRequestFactory,
                         RemoteErrors errors, @Value("${clients.payment.url}") String url) {
        this.http = errors.restClient(builder, remoteRequestFactory, url, SERVICE);
        this.errors = errors;
    }

    @CircuitBreaker(name = "paymentCB", fallbackMethod = "chargeFallback")
    @Retry(name = "paymentCB")
    public Payment charge(Long orderId, BigDecimal amount) {
        return errors.call(SERVICE, () -> http.post().uri("/payments")
                .body(new ChargeRequest(orderId, amount))
                .retrieve().body(Payment.class));
    }

    @CircuitBreaker(name = "paymentCB", fallbackMethod = "refundFallback")
    @Retry(name = "paymentCB")
    public Payment refund(Long paymentId) {
        return errors.call(SERVICE, () -> http.post().uri("/payments/{id}/refund", paymentId)
                .retrieve().body(Payment.class));
    }

    /** Para compensar un cobro incierto: el id no llegó, el orderId sí se conoce. */
    @CircuitBreaker(name = "paymentCB", fallbackMethod = "findFallback")
    @Retry(name = "paymentCB")
    public Optional<Payment> findByOrder(Long orderId) {
        try {
            return Optional.ofNullable(errors.call(SERVICE, () -> http.get()
                    .uri(u -> u.path("/payments").queryParam("orderId", orderId).build())
                    .retrieve().body(Payment.class)));
        } catch (BusinessException e) {
            if (e.getStatus().value() == HttpStatus.NOT_FOUND.value()) {
                return Optional.empty();
            }
            throw e;
        }
    }

    private Payment chargeFallback(Long orderId, BigDecimal amount, Throwable t) {
        throw Fallbacks.translate(LABEL, "payment.charge", t);
    }

    private Payment refundFallback(Long paymentId, Throwable t) {
        throw Fallbacks.translate(LABEL, "payment.refund", t);
    }

    private Optional<Payment> findFallback(Long orderId, Throwable t) {
        throw Fallbacks.translate(LABEL, "payment.findByOrder", t);
    }
}
