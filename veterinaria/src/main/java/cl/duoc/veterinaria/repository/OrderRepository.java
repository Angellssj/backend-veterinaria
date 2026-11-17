package cl.duoc.veterinaria.repository;

import cl.duoc.veterinaria.model.Order;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderRepository extends JpaRepository<Order, Long> {

    List<Order> findAllByOrderByCreatedAtDesc();

    List<Order> findAllByUser_IdOrderByCreatedAtDesc(Long userId);
}
