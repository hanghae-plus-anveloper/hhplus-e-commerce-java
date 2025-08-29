package kr.hhplus.be.server.common.event.order;

import kr.hhplus.be.server.saga.domain.OrderSagaItem;

import java.util.List;

public record OrderRequestedEvent(
        Long orderId,
        Long userId,
        List<OrderSagaItem> items,
        Long couponId
) {
}
