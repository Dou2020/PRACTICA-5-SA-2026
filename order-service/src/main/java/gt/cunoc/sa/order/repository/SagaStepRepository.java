package gt.cunoc.sa.order.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import gt.cunoc.sa.order.domain.SagaStep;

public interface SagaStepRepository extends JpaRepository<SagaStep, Long> {

    List<SagaStep> findByOrderIdOrderByIdAsc(Long orderId);
}
