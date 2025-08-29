package kr.hhplus.be.server.balance.application;

import kr.hhplus.be.server.common.event.balance.BalanceDeductionFailedEvent;
import kr.hhplus.be.server.common.event.order.OrderFailedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class BalanceCompensationHandler {

    private final BalanceCommandService balanceCommandService;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void on(BalanceDeductionFailedEvent event) {
        log.warn("[BALANCE-COMP] order={} balance deduction failed, reason={}",
                event.orderId(), event.reason());
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void on(OrderFailedEvent event) {
        log.warn("[BALANCE-COMP] order={} failed, start restoring balance", event.orderId());
        balanceCommandService.restoreBalance(event.userId(), event.totalAmount());
    }
}
