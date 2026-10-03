package gt.cunoc.sa.payment.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import gt.cunoc.sa.payment.domain.Payment;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    Optional<Payment> findByOrderId(Long orderId);
}
