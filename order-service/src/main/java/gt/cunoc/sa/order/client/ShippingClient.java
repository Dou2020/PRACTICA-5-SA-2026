package gt.cunoc.sa.order.client;

import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import gt.cunoc.sa.order.dto.RemoteDtos.ScheduleRequest;
import gt.cunoc.sa.order.dto.RemoteDtos.Shipment;
import gt.cunoc.sa.order.saga.BusinessException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;

/** Ver PaymentClient: misma forma, con shippingCB. */
@Component
public class ShippingClient {

    private static final String SERVICE = "shipping-service";
    private static final String LABEL = "envíos";

    private final RestClient http;
    private final RemoteErrors errors;

    public ShippingClient(RestClient.Builder builder, ClientHttpRequestFactory remoteRequestFactory,
                          RemoteErrors errors, @Value("${clients.shipping.url}") String url) {
        this.http = errors.restClient(builder, remoteRequestFactory, url, SERVICE);
        this.errors = errors;
    }

    @CircuitBreaker(name = "shippingCB", fallbackMethod = "scheduleFallback")
    @Retry(name = "shippingCB")
    public Shipment schedule(Long orderId, String address) {
        return errors.call(SERVICE, () -> http.post().uri("/shipping/schedule")
                .body(new ScheduleRequest(orderId, address))
                .retrieve().body(Shipment.class));
    }

    @CircuitBreaker(name = "shippingCB", fallbackMethod = "cancelFallback")
    @Retry(name = "shippingCB")
    public Shipment cancel(Long shipmentId) {
        return errors.call(SERVICE, () -> http.delete().uri("/shipping/{id}", shipmentId)
                .retrieve().body(Shipment.class));
    }

    /** Para compensar un envío incierto: el id no llegó, el orderId sí se conoce. */
    @CircuitBreaker(name = "shippingCB", fallbackMethod = "findFallback")
    @Retry(name = "shippingCB")
    public Optional<Shipment> findByOrder(Long orderId) {
        try {
            return Optional.ofNullable(errors.call(SERVICE, () -> http.get()
                    .uri(u -> u.path("/shipping").queryParam("orderId", orderId).build())
                    .retrieve().body(Shipment.class)));
        } catch (BusinessException e) {
            if (e.getStatus().value() == HttpStatus.NOT_FOUND.value()) {
                return Optional.empty();
            }
            throw e;
        }
    }

    private Shipment scheduleFallback(Long orderId, String address, Throwable t) {
        throw Fallbacks.translate(LABEL, "shipping.schedule", t);
    }

    private Shipment cancelFallback(Long shipmentId, Throwable t) {
        throw Fallbacks.translate(LABEL, "shipping.cancel", t);
    }

    private Optional<Shipment> findFallback(Long orderId, Throwable t) {
        throw Fallbacks.translate(LABEL, "shipping.findByOrder", t);
    }
}
