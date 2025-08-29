package kr.hhplus.be.server.external.mock.application;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import kr.hhplus.be.server.common.event.order.OrderCompletedEvent;
import kr.hhplus.be.server.external.mock.infrastructure.OrderExternalRedisRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderExternalEventHandler {

    private final OrderExternalRedisRepository redisRepository;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void on(OrderCompletedEvent event) {
        log.info("[MOCK-EXTERNAL] 주문 완료 이벤트 전송: orderId={}, items={}",
                event.orderId(), event.lines().size());

        mockSendToExternalSystem(event);
    }

    private void mockSendToExternalSystem(OrderCompletedEvent event) {
        log.debug("[MOCK-EXTERNAL] 외부 MOCK 이벤트 송신 - orderId={}, userId={}",
                event.orderId(), event.userId());

        redisRepository.recordSent(event.orderId(), event.userId());
    }
}
