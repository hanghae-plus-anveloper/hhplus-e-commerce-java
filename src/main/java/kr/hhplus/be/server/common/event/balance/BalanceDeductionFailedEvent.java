package kr.hhplus.be.server.common.event.balance;

import kr.hhplus.be.server.saga.domain.OrderSagaItem;

import java.util.List;

public record BalanceDeductionFailedEvent(
        Long orderId,
        List<OrderSagaItem> items,
        Long couponId,
        String reason
) {
}
