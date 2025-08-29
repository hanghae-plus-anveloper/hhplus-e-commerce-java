package kr.hhplus.be.server.product.application;

import kr.hhplus.be.server.common.event.balance.BalanceDeductionFailedEvent;
import kr.hhplus.be.server.common.event.coupon.CouponUseFailedEvent;
import kr.hhplus.be.server.common.event.product.StockReserveFailedEvent;
import kr.hhplus.be.server.saga.domain.OrderSagaItem;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class ProductCompensationHandler {

    private final ProductCommandService productCommandService;

    // 재고 실패 로그만 기록(본인)
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void on(StockReserveFailedEvent event) {
        log.warn("[PRODUCT-COMP] order={} product reserve failed, no compensation needed (not reserved)",
                event.orderId());
    }

    // 쿠폰 사용 실패 시 보상
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void on(CouponUseFailedEvent event) {
        log.warn("[PRODUCT-COMP] order={} coupon failed → restoring reserved stock", event.orderId());
        restore(event.orderId(), event.items());
    }

    // 잔액 차감 실패 시 보상
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void on(BalanceDeductionFailedEvent event) {
        log.warn("[PRODUCT-COMP] order={} balance failed → restoring reserved stock", event.orderId());
        restore(event.orderId(), event.items());
    }


    private void restore(Long orderId, List<OrderSagaItem> items) {
        try {
            productCommandService.restoreStock(orderId, items);
            log.info("[PRODUCT-COMP] order={} product stock restored", orderId);
        } catch (Exception e) {
            log.error("[PRODUCT-COMP] order={} failed to restore stock, error={}", orderId, e.getMessage(), e);
        }
    }
}
