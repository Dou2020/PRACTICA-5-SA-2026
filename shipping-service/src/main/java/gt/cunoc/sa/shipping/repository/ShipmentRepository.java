package gt.cunoc.sa.shipping.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import gt.cunoc.sa.shipping.domain.Shipment;

public interface ShipmentRepository extends JpaRepository<Shipment, Long> {

    Optional<Shipment> findByOrderId(Long orderId);
}
