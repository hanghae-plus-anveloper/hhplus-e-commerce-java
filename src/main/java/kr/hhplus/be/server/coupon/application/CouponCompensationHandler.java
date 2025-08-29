package kr.hhplus.be.server.coupon.application;

import kr.hhplus.be.server.common.event.balance.BalanceDeductionFailedEvent;
import kr.hhplus.be.server.common.event.product.StockReserveFailedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class CouponCompensationHandler {

    private final CouponCommandService couponCommandService;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void on(BalanceDeductionFailedEvent event) {
        log.warn("[COUPON-COMP] order={} balance 실패 → 쿠폰 원복 실행", event.orderId());
        couponCommandService.restoreCoupon(event.couponId());
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void on(StockReserveFailedEvent event) {
        log.warn("[COUPON-COMP] order={} product 실패 → 쿠폰 원복 실행", event.orderId());
        couponCommandService.restoreCoupon(event.couponId());
    }
}
