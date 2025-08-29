package kr.hhplus.be.server.analytics.infrastructure;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Repository;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Set;

@Repository
@RequiredArgsConstructor
public class TopProductRedisRepository {

    private final StringRedisTemplate redisTemplate;
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.BASIC_ISO_DATE;

    private static final int TTL_DAYS = 4;
    private static final String PRODUCT_RANKING_PREFIX = "RANKING:PRODUCT:";
    private static final String ISSUED_ORDER_SET = PRODUCT_RANKING_PREFIX + "ISSUED";

    private String getDailyKey(LocalDate date) {
        return PRODUCT_RANKING_PREFIX + date.format(FORMATTER);
    }

    public boolean isAlreadyIssued(Long orderId) {
        return Boolean.TRUE.equals(redisTemplate.opsForSet().isMember(ISSUED_ORDER_SET, orderId.toString()));
    }

    public void markIssued(Long orderId) {
        redisTemplate.opsForSet().add(ISSUED_ORDER_SET, orderId.toString());
        redisTemplate.expire(ISSUED_ORDER_SET, Duration.ofDays(TTL_DAYS));
    }

    public void recordOrders(List<TopProductRecord> items) {
        String key = getDailyKey(LocalDate.now());

        for (TopProductRecord item : items) {
            redisTemplate.opsForZSet().incrementScore(key, item.productId(), item.soldQty());
        }

        redisTemplate.expire(key, Duration.ofDays(TTL_DAYS));
    }

    public Set<ZSetOperations.TypedTuple<String>> getTop5InLast3Days() {
        LocalDate today = LocalDate.now();
        List<String> keys = List.of(
                getDailyKey(today),
                getDailyKey(today.minusDays(1)),
                getDailyKey(today.minusDays(2))
        );

        String unionKey =  PRODUCT_RANKING_PREFIX + "TOP5LAST3DAYS";

        // 합집합 새로 저장
        redisTemplate.opsForZSet().unionAndStore(keys.get(0), keys.subList(1, keys.size()), unionKey);

        // score 값을 포함한 튜플 조회
        Set<ZSetOperations.TypedTuple<String>> tuples =
                redisTemplate.opsForZSet().reverseRangeWithScores(unionKey, 0, 4);

        return tuples != null ? tuples : Set.of();
    }

    // 테스트 시 과거기록 세팅용 날짜 포함 함수(운영코드 X)
    public void recordOrder(String productId, int quantity, LocalDate date) {
        String key = getDailyKey(date);
        redisTemplate.opsForZSet().incrementScore(key, productId, quantity);

        LocalDateTime expireAt = date.plusDays(TTL_DAYS).atStartOfDay();
        Instant instant = expireAt.atZone(ZoneId.systemDefault()).toInstant();
        redisTemplate.expireAt(key, instant);
    }

    public void clearAll() {
        redisTemplate.delete(ISSUED_ORDER_SET);
        LocalDate today = LocalDate.now();
        for (int i = 0; i < TTL_DAYS; i++) {
            redisTemplate.delete(getDailyKey(today.minusDays(i)));
        }
        redisTemplate.delete(PRODUCT_RANKING_PREFIX + "TOP5LAST3DAYS");
    }
}
