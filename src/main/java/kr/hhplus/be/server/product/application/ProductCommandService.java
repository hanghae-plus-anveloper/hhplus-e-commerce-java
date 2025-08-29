package kr.hhplus.be.server.product.application;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import kr.hhplus.be.server.common.event.product.StockReserveFailedEvent;
import kr.hhplus.be.server.common.event.product.StockReservedEvent;
import kr.hhplus.be.server.common.lock.DistributedLock;
import kr.hhplus.be.server.common.lock.LockKey;
import kr.hhplus.be.server.product.domain.Product;
import kr.hhplus.be.server.product.domain.ProductRepository;
import kr.hhplus.be.server.saga.domain.OrderSagaItem;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProductCommandService {

    private final ProductRepository productRepository;
    private final ApplicationEventPublisher publisher;

    @Transactional
    @DistributedLock(prefix = LockKey.PRODUCT, ids = "#items.![productId]")
    public void reserveStock(Long orderId, List<OrderSagaItem> items, Long couponId) {
        try {
            Map<Long, Product> productMap = productRepository
                    .findAllById(items.stream().map(OrderSagaItem::getProductId).toList())
                    .stream().collect(Collectors.toMap(Product::getId, p -> p));
            if (productMap.size() != items.size()) {
                throw new IllegalStateException("요청한 상품 중 존재하지 않는 항목이 있습니다.");
            }

            int subTotal = 0;

            for (OrderSagaItem item : items) {
                Long pid = item.getProductId();
                int qty = item.getQuantity();

                int affected = productRepository.decreaseStockIfAvailable(pid, qty);
                if (affected == 0) {
                    throw new IllegalStateException("재고 부족 또는 동시성 경합: productId=" + pid);
                }

                Product p = productMap.get(pid);
                subTotal += p.getPrice() * qty;
            }

            log.info("[PRODUCT] order={} stock reserved successfully, subTotal={}", orderId, subTotal);

            // 성공 이벤트 발행
            publisher.publishEvent(new StockReservedEvent(orderId, subTotal));

        } catch (Exception e) {
            log.warn("[PRODUCT] order={} stock reservation failed, reason={}", orderId, e.getMessage());
            publisher.publishEvent(new StockReserveFailedEvent(orderId, couponId, e.getMessage()));
        }
    }

    @Transactional
    @DistributedLock(prefix = LockKey.PRODUCT, ids = "#items.![productId]")
    public void restoreStock(Long orderId, List<OrderSagaItem> items) {
        for (OrderSagaItem item : items) {
            productRepository.increaseStock(item.getProductId(), item.getQuantity());
        }
        log.info("[PRODUCT] order={} stock restored for {} items", orderId, items.size());
    }
}
