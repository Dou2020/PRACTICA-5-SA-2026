package gt.cunoc.sa.order.dto;

import java.math.BigDecimal;

/**
 * Copia de los contratos de los servicios hoja, solo con los campos que el
 * orquestador usa. No se importan sus clases: cada microservicio se construye
 * solo, y el contrato entre ellos es el JSON, no un jar compartido. Jackson
 * ignora los campos de más (Spring Boot desactiva FAIL_ON_UNKNOWN_PROPERTIES).
 */
public final class RemoteDtos {

    private RemoteDtos() {
    }

    public record ChargeRequest(Long orderId, BigDecimal amount) {
    }

    public record Payment(Long id, Long orderId, String status) {
    }

    public record ReserveRequest(Long orderId, String sku, Integer quantity) {
    }

    public record ReleaseRequest(Long orderId) {
    }

    public record Reservation(Long id, Long orderId, String status) {
    }

    public record ReleaseResponse(Long orderId, String detail) {
    }

    public record ScheduleRequest(Long orderId, String address) {
    }

    public record Shipment(Long id, Long orderId, String status, String trackingCode) {
    }

    /** Cuerpo de error común: {"error": "OUT_OF_STOCK", "message": "..."}. */
    public record ApiError(String error, String message) {
    }
}
