package gt.cunoc.sa.order.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import gt.cunoc.sa.order.domain.Order;

public interface OrderRepository extends JpaRepository<Order, Long> {

    List<Order> findAllByOrderByIdDesc();
}
