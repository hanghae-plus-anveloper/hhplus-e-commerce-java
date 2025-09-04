package kr.hhplus.be.server.kafka.message;

import java.time.Instant;
import java.util.List;

public record OrderCompletedMessage(
        String orderId,
        String userId,
        List<Line> lines,
        Instant completedAt
) {
    public record Line(Long productId, int quantity) {}
}