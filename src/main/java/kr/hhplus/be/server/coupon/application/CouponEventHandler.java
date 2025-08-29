package kr.hhplus.be.server.coupon.application;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import kr.hhplus.be.server.common.event.order.OrderRequestedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class CouponEventHandler {

    private final CouponCommandService couponCommandService;
    private final ApplicationEventPublisher publisher;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void on(OrderRequestedEvent event) {
        if (event.couponId() == null) {
            couponCommandService.skipCoupon(event.orderId());
            log.info("[COUPON] order={} no coupon applied", event.orderId());
            return;
        }

        log.info("[COUPON] order={} coupon={} processed", event.orderId(), event.couponId());
        couponCommandService.useCoupon(event.couponId(), event.userId(), event.orderId(), event.items());
    }
}
