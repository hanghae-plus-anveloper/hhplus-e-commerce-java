package kr.hhplus.be.server.kafka.message;

import java.time.Instant;

public record CouponPendedMessage(Long userId, Long policyId, Instant requestedAt) {
}
