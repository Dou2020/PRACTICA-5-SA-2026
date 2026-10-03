package gt.cunoc.sa.inventory.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import gt.cunoc.sa.inventory.domain.Reservation;

public interface ReservationRepository extends JpaRepository<Reservation, Long> {

    Optional<Reservation> findByOrderId(Long orderId);
}
