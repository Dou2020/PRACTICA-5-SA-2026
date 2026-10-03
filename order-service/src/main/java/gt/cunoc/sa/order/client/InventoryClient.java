package gt.cunoc.sa.order.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import gt.cunoc.sa.order.dto.RemoteDtos.ReleaseRequest;
import gt.cunoc.sa.order.dto.RemoteDtos.ReleaseResponse;
import gt.cunoc.sa.order.dto.RemoteDtos.Reservation;
import gt.cunoc.sa.order.dto.RemoteDtos.ReserveRequest;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;

/** Ver PaymentClient: misma forma, con inventoryCB. */
@Component
public class InventoryClient {

    private static final String SERVICE = "inventory-service";
    private static final String LABEL = "inventario";

    private final RestClient http;
    private final RemoteErrors errors;

    public InventoryClient(RestClient.Builder builder, ClientHttpRequestFactory remoteRequestFactory,
                           RemoteErrors errors, @Value("${clients.inventory.url}") String url) {
        this.http = errors.restClient(builder, remoteRequestFactory, url, SERVICE);
        this.errors = errors;
    }

    @CircuitBreaker(name = "inventoryCB", fallbackMethod = "reserveFallback")
    @Retry(name = "inventoryCB")
    public Reservation reserve(Long orderId, String sku, int quantity) {
        return errors.call(SERVICE, () -> http.post().uri("/inventory/reserve")
                .body(new ReserveRequest(orderId, sku, quantity))
                .retrieve().body(Reservation.class));
    }

    /**
     * Va por orderId, así que sirve igual para una reserva confirmada que para
     * una incierta: inventory responde 200 aunque no haya nada que liberar.
     */
    @CircuitBreaker(name = "inventoryCB", fallbackMethod = "releaseFallback")
    @Retry(name = "inventoryCB")
    public ReleaseResponse release(Long orderId) {
        return errors.call(SERVICE, () -> http.post().uri("/inventory/release")
                .body(new ReleaseRequest(orderId))
                .retrieve().body(ReleaseResponse.class));
    }

    private Reservation reserveFallback(Long orderId, String sku, int quantity, Throwable t) {
        throw Fallbacks.translate(LABEL, "inventory.reserve", t);
    }

    private ReleaseResponse releaseFallback(Long orderId, Throwable t) {
        throw Fallbacks.translate(LABEL, "inventory.release", t);
    }
}
