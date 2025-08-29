package kr.hhplus.be.server.analytics.application;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import kr.hhplus.be.server.common.event.order.OrderCompletedEvent;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class TopProductEventHandler {

    private final TopProductService topProductService;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void on(OrderCompletedEvent event) {
        topProductService.recordOrdersAsync(event.orderId(), event.lines());
    }
}
