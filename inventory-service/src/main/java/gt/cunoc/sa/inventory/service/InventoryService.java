package gt.cunoc.sa.inventory.service;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import gt.cunoc.sa.inventory.domain.Reservation;
import gt.cunoc.sa.inventory.dto.ProductResponse;
import gt.cunoc.sa.inventory.dto.ReleaseRequest;
import gt.cunoc.sa.inventory.dto.ReleaseResponse;
import gt.cunoc.sa.inventory.dto.ReservationResponse;
import gt.cunoc.sa.inventory.dto.ReserveRequest;
import gt.cunoc.sa.inventory.dto.ReserveResult;
import gt.cunoc.sa.inventory.repository.ProductRepository;
import gt.cunoc.sa.inventory.repository.ReservationRepository;
import gt.cunoc.sa.web.BusinessException;

@Service
public class InventoryService {

    private static final Logger log = LoggerFactory.getLogger(InventoryService.class);

    private final ProductRepository products;
    private final ReservationRepository reservations;

    public InventoryService(ProductRepository products, ReservationRepository reservations) {
        this.products = products;
        this.reservations = reservations;
    }

    /**
     * Idempotente por orderId, por la misma razón que el cobro: un reintento
     * tras un timeout no debe descontar stock dos veces.
     *
     * El descuento y la reserva van en la misma transacción local: o quedan
     * los dos o ninguno. Esa es la atomicidad que SÍ se puede tener dentro de
     * un servicio; la que cruza servicios es la que resuelve la saga.
     */
    @Transactional
    public ReserveResult reserve(ReserveRequest request) {
        var existente = reservations.findByOrderId(request.orderId());
        if (existente.isPresent()) {
            log.info("Reserva ya existente para orden {} → se devuelve sin descontar de nuevo", request.orderId());
            return new ReserveResult(ReservationResponse.of(existente.get()), false);
        }
        if (!products.existsById(request.sku())) {
            throw new BusinessException(HttpStatus.UNPROCESSABLE_ENTITY, "UNKNOWN_PRODUCT",
                    "No existe el producto " + request.sku());
        }
        if (products.decrementStock(request.sku(), request.quantity()) == 0) {
            log.info("Sin stock para orden {}: {} x{}", request.orderId(), request.sku(), request.quantity());
            throw new BusinessException(HttpStatus.CONFLICT, "OUT_OF_STOCK",
                    "Sin stock suficiente de " + request.sku() + " para " + request.quantity() + " unidad(es)");
        }
        Reservation reservation = reservations.save(
                Reservation.reserve(request.orderId(), request.sku(), request.quantity()));
        log.info("Reserva {} para orden {}: {} x{}", reservation.getId(), reservation.getOrderId(),
                reservation.getSku(), reservation.getQuantity());
        return new ReserveResult(ReservationResponse.of(reservation), true);
    }

    /**
     * Compensación. Nunca falla por "no encontrado", y es a propósito: si el
     * reserve dio timeout del lado del orquestador, este no sabe si se ejecutó.
     * Tiene que poder liberar "por si acaso" y recibir un 200 en ambos casos;
     * con un 404 la compensación quedaría como fallida sin que haya nada mal.
     */
    @Transactional
    public ReleaseResponse release(ReleaseRequest request) {
        var encontrada = reservations.findByOrderId(request.orderId());
        if (encontrada.isEmpty()) {
            log.info("Release de orden {}: no hay reserva, nada que liberar", request.orderId());
            return new ReleaseResponse(request.orderId(), "Sin reserva para esta orden: nada que liberar", null);
        }
        Reservation reservation = encontrada.get();
        if (!reservation.release()) {
            log.info("Reserva {} ya estaba liberada → sin cambios", reservation.getId());
            return new ReleaseResponse(request.orderId(), "La reserva ya estaba liberada",
                    ReservationResponse.of(reservation));
        }
        products.incrementStock(reservation.getSku(), reservation.getQuantity());
        log.info("Reserva {} liberada: +{} {}", reservation.getId(), reservation.getQuantity(), reservation.getSku());
        return new ReleaseResponse(request.orderId(), "Reserva liberada, stock devuelto",
                ReservationResponse.of(reservation));
    }

    @Transactional(readOnly = true)
    public List<ProductResponse> products() {
        return products.findAllByOrderBySku().stream().map(ProductResponse::of).toList();
    }
}
