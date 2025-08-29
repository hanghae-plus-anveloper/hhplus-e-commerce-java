package kr.hhplus.be.server.common.event.product;

public record StockReservedEvent(
        Long orderId,
        Integer subTotalAmount
) {
}
