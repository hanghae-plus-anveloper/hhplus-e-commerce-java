package kr.hhplus.be.server.analytics.application;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import kr.hhplus.be.server.analytics.domain.TopProductNativeRepository;
import kr.hhplus.be.server.analytics.domain.TopProductView;
import kr.hhplus.be.server.analytics.infrastructure.TopProductRedisRepository;
import kr.hhplus.be.server.common.cache.CacheNames;
import kr.hhplus.be.server.common.event.order.OrderLineSummary;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class TopProductService {

    private final TopProductNativeRepository repository;
    private final TopProductRedisRepository redisRepository;

    @Cacheable(cacheNames = CacheNames.TOP_PRODUCTS, key = "T(kr.hhplus.be.server.common.cache.CacheKey).TOP_PRODUCTS"
            + ".key('LAST_N_DAYS', 3, 'TOP', 5)", sync = true)
    @Transactional(readOnly = true)
    public List<TopProductView> top5InLast3Days() {
        return topNLastNDays(3, 5);
    }

    @Cacheable(cacheNames = CacheNames.TOP_PRODUCTS, key = "T(kr.hhplus.be.server.common.cache.CacheKey).TOP_PRODUCTS"
            + ".key('LAST_N_DAYS', #days, 'TOP', #limit)", sync = true)
    @Transactional(readOnly = true)
    public List<TopProductView> topNLastNDays(int days, int limit) {
        if (days <= 0)
            throw new IllegalArgumentException("days 는 1 이상이어야 합니다.");
        if (limit <= 0)
            throw new IllegalArgumentException("limit 는 1 이상이어야 합니다.");

        LocalDate today = LocalDate.now(); // 오늘을 제외하고,
        LocalDate from = today.minusDays(days);
        LocalDate to = today.minusDays(1);
        return repository.findTopSoldBetween(from, to, limit);
    }

    // Redis 기반 코드 추가
    @Async
    public void recordOrdersAsync(Long orderId, List<OrderLineSummary> lines) {
        if (redisRepository.isAlreadyIssued(orderId)) {
            return;
        }
        try {
            redisRepository.recordOrders(lines.stream().map(TopProductMapper::toRecord).toList());
            redisRepository.markIssued(orderId);
        } catch (Exception ignored) {
        }
    }

    // test setup
    public void recordOrder(String productId, int quantity, LocalDate localDate) {
        redisRepository.recordOrder(productId, quantity, localDate);
    }

    public void clearAll() {
        redisRepository.clearAll();
    }

    // Redis 기반 조회 + DB join
    @Cacheable(cacheNames = CacheNames.TOP_PRODUCTS_REALTIME, // 반드시 캐시 네임스페이스를 바꿔줘야 함
            key = "T(kr.hhplus.be.server.common.cache.CacheKey).TOP_PRODUCTS_REALTIME.key('REDIS_LAST3_TOP5')", sync = true)
    @Transactional(readOnly = true)
    public List<TopProductView> getTop5InLast3DaysFromRedis() {
        Set<ZSetOperations.TypedTuple<String>> tuples = redisRepository.getTop5InLast3Days();
        if (tuples.isEmpty())
            return List.of();

        Map<Long, Long> qtyMap = tuples.stream().filter(t -> t.getValue() != null)
                .collect(Collectors.toMap(t -> Long.valueOf(t.getValue()),
                        t -> t.getScore() != null ? t.getScore().longValue() : 0L, (a, b) -> a, LinkedHashMap::new));

        List<TopProductView> views = repository.findNamesByIds(qtyMap);
        Map<Long, String> nameMap = views.stream()
                .collect(Collectors.toMap(TopProductView::productId, TopProductView::name));

        // Redis 순서를 유지하면서 name을 붙여 반환
        return qtyMap.entrySet().stream()
                .map(e -> new TopProductView(e.getKey(), nameMap.getOrDefault(e.getKey(), "[UNKNOWN]"), e.getValue()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<TopProductView> getTop5InLast3DaysFromRedisWithoutCache() {
        Set<ZSetOperations.TypedTuple<String>> tuples = redisRepository.getTop5InLast3Days();
        if (tuples.isEmpty())
            return List.of();

        Map<Long, Long> qtyMap = tuples.stream().filter(t -> t.getValue() != null)
                .collect(Collectors.toMap(t -> Long.valueOf(t.getValue()),
                        t -> t.getScore() != null ? t.getScore().longValue() : 0L, (a, b) -> a, LinkedHashMap::new));

        List<TopProductView> views = repository.findNamesByIds(qtyMap);
        Map<Long, String> nameMap = views.stream()
                .collect(Collectors.toMap(TopProductView::productId, TopProductView::name));

        // Redis 순서를 유지하면서 name을 붙여 반환
        return qtyMap.entrySet().stream()
                .map(e -> new TopProductView(e.getKey(), nameMap.getOrDefault(e.getKey(), "[UNKNOWN]"), e.getValue()))
                .toList();
    }
}
