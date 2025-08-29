package kr.hhplus.be.server.common.event.order;

import java.util.List;

public record OrderCompletedEvent(Long orderId, Long userId, List<OrderLineSummary> lines) {
}
