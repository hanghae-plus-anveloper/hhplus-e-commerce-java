package kr.hhplus.be.server.common.event.product;

public record StockReserveFailedEvent(
        Long orderId,
        Long couponId,
        String reason
) {
}
