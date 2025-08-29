package kr.hhplus.be.server.product.application;


import kr.hhplus.be.server.common.event.order.OrderRequestedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class ProductEventHandler {

    private final ProductCommandService productCommandService;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void on(OrderRequestedEvent event) {
        log.info("[PRODUCT] handling OrderRequestedEvent for order={}, items={}",
                event.orderId(), event.items().size());
        productCommandService.reserveStock(event.orderId(), event.items(), event.couponId());
    }
}
