package kr.hhplus.be.server.order.application;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import kr.hhplus.be.server.common.event.order.OrderCompletedEvent;
import kr.hhplus.be.server.common.event.order.OrderFailedEvent;
import kr.hhplus.be.server.common.event.order.OrderRequestedEvent;
import kr.hhplus.be.server.order.domain.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderEventHandler {

    private final OrderRepository orderRepository;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void on(OrderRequestedEvent event) {
        orderRepository.findById(event.orderId()).ifPresent(order -> {
            order.markPending();
            orderRepository.save(order);
            log.info("[ORDER] order={} moved to PENDING", event.orderId());
        });
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void on(OrderCompletedEvent event) {
        orderRepository.findById(event.orderId()).ifPresent(order -> {
            order.markPaid();
            orderRepository.save(order);
            log.info("[ORDER] order={} moved to PAID", event.orderId());
        });
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void on(OrderFailedEvent event) {
        orderRepository.findById(event.orderId()).ifPresent(order -> {
            order.cancel();
            orderRepository.save(order);
            log.info("[ORDER] order={} moved to CANCELED", event.orderId());
        });
    }
}
