package kr.hhplus.be.server.common.event.coupon;

import kr.hhplus.be.server.saga.domain.OrderSagaItem;

import java.util.List;

public record CouponUseFailedEvent(
        Long orderId,
        Long couponId,
        String reason,
        List<OrderSagaItem> items
) {
}
