package kr.hhplus.be.server.product.domain;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

public interface ProductRepository extends JpaRepository<Product, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Product p WHERE p.id = :id")
    Optional<Product> findByIdForUpdate(@Param("id") Long id);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE Product p
               SET p.stock = p.stock - :qty
             WHERE p.id = :id AND p.stock >= :qty
            """)
    int decreaseStockIfAvailable(@Param("id") Long id, @Param("qty") int qty);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE Product p
               SET p.stock = p.stock + :qty
             WHERE p.id = :id
            """)
    int increaseStock(@Param("id") Long id, @Param("qty") int qty);
}