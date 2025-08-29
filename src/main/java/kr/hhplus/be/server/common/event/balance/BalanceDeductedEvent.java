package kr.hhplus.be.server.common.event.balance;

public record BalanceDeductedEvent(
        Long orderId,
        Integer totalAmount
) {
}
