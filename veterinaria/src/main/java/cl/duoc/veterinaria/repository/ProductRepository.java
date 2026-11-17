package cl.duoc.veterinaria.repository;

import cl.duoc.veterinaria.model.Product;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProductRepository extends JpaRepository<Product, Long> {

    /** Catálogo visible: solo productos activos (no eliminados). */
    List<Product> findAllByActiveTrueOrderByIdAsc();

    Optional<Product> findByIdAndActiveTrue(Long id);

    /**
     * Igual que findByIdAndActiveTrue, pero bloquea la fila (SELECT ... FOR UPDATE)
     * hasta que termine la transacción: dos compras simultáneas no pueden vender el mismo stock.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Product p where p.id = :id and p.active = true")
    Optional<Product> findActiveForUpdate(@Param("id") Long id);
}
