package kr.hhplus.be.server.balance.application;

import kr.hhplus.be.server.common.event.order.OrderCalculatedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class BalanceEventHandler {

    private final BalanceCommandService balanceCommandService;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void on(OrderCalculatedEvent event) {
        log.info("[BALANCE] order={} calculation received, total={}",
                event.orderId(), event.totalAmount());

        balanceCommandService.deductBalance(
                event.orderId(),
                event.userId(),
                event.totalAmount(),
                event.items(),
                event.couponId()
        );
    }
}
