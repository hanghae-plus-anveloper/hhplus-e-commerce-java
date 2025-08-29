package kr.hhplus.be.server.saga.domain;

import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderSagaRepository extends JpaRepository<OrderSagaState, Long> {
}
