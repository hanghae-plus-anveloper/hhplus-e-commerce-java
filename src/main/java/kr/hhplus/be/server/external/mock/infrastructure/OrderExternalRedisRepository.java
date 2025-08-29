package kr.hhplus.be.server.external.mock.infrastructure;

import java.time.LocalDateTime;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class OrderExternalRedisRepository {

    private final StringRedisTemplate redisTemplate;
    private static final String KEY_PREFIX = "EXTERNAL:ORDER:";

    public void recordSent(Long orderId, Long userId) {
        String key = KEY_PREFIX + orderId;
        String value = "sentAt=" + LocalDateTime.now() + ", userId=" + userId;
        redisTemplate.opsForList().rightPush(key, value);
    }

    public Long countRecords(Long orderId) {
        return redisTemplate.opsForList().size(KEY_PREFIX + orderId);
    }

    public void clear(Long orderId) {
        redisTemplate.delete(KEY_PREFIX + orderId);
    }
}
