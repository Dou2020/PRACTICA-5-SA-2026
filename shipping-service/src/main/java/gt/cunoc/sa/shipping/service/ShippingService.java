package gt.cunoc.sa.shipping.service;

import java.util.Locale;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import gt.cunoc.sa.shipping.domain.Shipment;
import gt.cunoc.sa.shipping.dto.ScheduleRequest;
import gt.cunoc.sa.shipping.dto.ScheduleResult;
import gt.cunoc.sa.shipping.dto.ShipmentResponse;
import gt.cunoc.sa.shipping.repository.ShipmentRepository;
import gt.cunoc.sa.web.BusinessException;
import gt.cunoc.sa.web.NotFoundException;

@Service
public class ShippingService {

    private static final Logger log = LoggerFactory.getLogger(ShippingService.class);

    /**
     * Regla de negocio de la demo: una dirección que contiene FAIL no tiene
     * cobertura. Shipping es el último paso, así que fallar acá obliga a la
     * saga a compensar los dos anteriores (release + refund).
     */
    static final String SIN_COBERTURA = "FAIL";

    private final ShipmentRepository repository;

    public ShippingService(ShipmentRepository repository) {
        this.repository = repository;
    }

    /** Idempotente por orderId: un reintento no programa dos envíos. */
    @Transactional
    public ScheduleResult schedule(ScheduleRequest request) {
        var existente = repository.findByOrderId(request.orderId());
        if (existente.isPresent()) {
            log.info("Envío ya existente para orden {} → se devuelve sin programar otro", request.orderId());
            return new ScheduleResult(ShipmentResponse.of(existente.get()), false);
        }
        if (request.address().toUpperCase(Locale.ROOT).contains(SIN_COBERTURA)) {
            log.info("Envío rechazado para orden {}: dirección sin cobertura", request.orderId());
            throw new BusinessException(HttpStatus.UNPROCESSABLE_ENTITY, "ADDRESS_UNDELIVERABLE",
                    "No hay cobertura de envío para la dirección \"" + request.address() + "\"");
        }
        Shipment shipment = repository.save(Shipment.schedule(request.orderId(), request.address()));
        shipment.assignTracking();
        log.info("Envío {} programado para orden {}", shipment.getTrackingCode(), shipment.getOrderId());
        return new ScheduleResult(ShipmentResponse.of(shipment), true);
    }

    /** Compensación. Idempotente: cancelar un envío ya cancelado devuelve 200 sin cambios. */
    @Transactional
    public ShipmentResponse cancel(Long id) {
        Shipment shipment = repository.findById(id)
                .orElseThrow(() -> new NotFoundException("No existe el envío " + id));
        if (shipment.cancel()) {
            log.info("Envío {} cancelado (orden {})", shipment.getTrackingCode(), shipment.getOrderId());
        } else {
            log.info("Envío {} ya estaba cancelado → sin cambios", shipment.getTrackingCode());
        }
        return ShipmentResponse.of(shipment);
    }

    /**
     * Búsqueda por orden, para compensar un envío incierto: si el schedule dio
     * timeout del lado del orquestador, el envío pudo programarse tarde y el id
     * que pide el DELETE nunca le llegó.
     */
    @Transactional(readOnly = true)
    public ShipmentResponse findByOrder(Long orderId) {
        return repository.findByOrderId(orderId)
                .map(ShipmentResponse::of)
                .orElseThrow(() -> new NotFoundException("No hay envío para la orden " + orderId));
    }
}
