package kr.hhplus.be.server.common.event.order;

import kr.hhplus.be.server.saga.domain.OrderSagaItem;

import java.util.List;

public record OrderCalculatedEvent(
        Long orderId,
        Long userId,
        int totalAmount,
        List<OrderSagaItem> items,
        Long couponId
) {
}
