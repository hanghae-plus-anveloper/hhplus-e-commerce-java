package kr.hhplus.be.server.saga.application;

import kr.hhplus.be.server.common.event.balance.BalanceDeductedEvent;
import kr.hhplus.be.server.common.event.balance.BalanceDeductionFailedEvent;
import kr.hhplus.be.server.common.event.coupon.CouponUseFailedEvent;
import kr.hhplus.be.server.common.event.coupon.CouponUsedEvent;
import kr.hhplus.be.server.common.event.order.OrderCalculatedEvent;
import kr.hhplus.be.server.common.event.order.OrderDraftedEvent;
import kr.hhplus.be.server.common.event.order.OrderRequestedEvent;
import kr.hhplus.be.server.common.event.product.StockReserveFailedEvent;
import kr.hhplus.be.server.common.event.product.StockReservedEvent;
import kr.hhplus.be.server.saga.domain.OrderSagaEventStatus;
import kr.hhplus.be.server.saga.domain.OrderSagaRepository;
import kr.hhplus.be.server.saga.domain.OrderSagaState;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderSagaHandler {

    private final ApplicationEventPublisher publisher;
    private final OrderSagaRepository sagaRepository;

    // 주문 조안 생성 시 감지 후 저장
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void on(OrderDraftedEvent event) {
        // 초안 저장 시 초기 Saga 상태 저장
        OrderSagaState sagaState = OrderSagaState.builder()
                .orderId(event.orderId())
                .userId(event.userId())
                .items(event.items())
                .couponId(event.couponId())
                .productReserved(OrderSagaEventStatus.PENDING)
                .couponApplied(OrderSagaEventStatus.PENDING)
                .balanceCharged(OrderSagaEventStatus.PENDING)
                .build();

        sagaRepository.save(sagaState);
        log.info("[SAGA] Drafted order={}, user={} saga initialized", event.orderId(), event.userId());

        // Saga 저장소 생성 후 각 도메인별 주문 요청 이벤트 발행
        publisher.publishEvent(
                new OrderRequestedEvent(event.orderId(), event.userId(), event.items(), event.couponId())
        );
    }

    private void tryTriggerOrderCalculated(OrderSagaState saga) {
        if (saga.isReadyForCalculation()) {
            int total = saga.getSubTotalAmount() - saga.getDiscountAmount();
            publisher.publishEvent(new OrderCalculatedEvent(
                    saga.getOrderId(),
                    saga.getUserId(),
                    total,
                    saga.getItems(),
                    saga.getCouponId()
            ));
            log.info("[SAGA] order={} calculation prepared → total={}", saga.getOrderId(), total);
        }
    }

    // 재고 차감 상태 업데이트
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void on(StockReservedEvent event) {
        sagaRepository.findById(event.orderId()).ifPresent(saga -> {
            saga.markProductReservedSuccess(event.subTotalAmount());
            sagaRepository.save(saga);
            log.info("[SAGA] order={} product reserved success, subtotal={}", event.orderId(), event.subTotalAmount());
            tryTriggerOrderCalculated(saga);
        });
    }

    // 쿠폰 사용 상태 업데이트
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void on(CouponUsedEvent event) {
        sagaRepository.findById(event.orderId()).ifPresent(saga -> {
            saga.markCouponAppliedSuccess(event.discountAmount());
            sagaRepository.save(saga);
            log.info("[SAGA] order={} coupon used success, discount={}", event.orderId(), event.discountAmount());
            tryTriggerOrderCalculated(saga);
        });
    }

    // 잔액 차감 완료 상태 업데이트
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void on(BalanceDeductedEvent event) {
        sagaRepository.findById(event.orderId()).ifPresent(saga -> {
            saga.markBalanceChargedSuccess(event.totalAmount());
            sagaRepository.save(saga);
            log.info("[SAGA] order={} balance deducted success, total={}", event.orderId(), event.totalAmount());
        });
    }

    // 상품 재고 차감 실패 상태 업데이트
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void on(StockReserveFailedEvent event) {
        sagaRepository.findById(event.orderId()).ifPresent(saga -> {
            saga.markFailedDomain("PRODUCT");
            sagaRepository.save(saga);
            log.warn("[SAGA] order={} product reserve failed, reason={}", event.orderId(), event.reason());
        });
    }

    // 쿠폰 사용 실패 상태 업데이트
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void on(CouponUseFailedEvent event) {
        sagaRepository.findById(event.orderId()).ifPresent(saga -> {
            saga.markFailedDomain("COUPON");
            sagaRepository.save(saga);
            log.warn("[SAGA] order={} coupon use failed, reason={}", event.orderId(), event.reason());
        });
    }

    // 잔액 차감 실패 상태 업데이트
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void on(BalanceDeductionFailedEvent event) {
        sagaRepository.findById(event.orderId()).ifPresent(saga -> {
            saga.markFailedDomain("BALANCE");
            sagaRepository.save(saga);
            log.warn("[SAGA] order={} balance deduction failed, reason={}", event.orderId(), event.reason());
        });
    }
}
