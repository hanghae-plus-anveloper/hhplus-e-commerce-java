package kr.hhplus.be.server.order.facade;

import java.util.Comparator;
import java.util.List;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

// import kr.hhplus.be.server.analytics.application.TopProductService;
import kr.hhplus.be.server.balance.application.BalanceService;
import kr.hhplus.be.server.common.event.order.OrderCompletedEvent;
import kr.hhplus.be.server.common.event.order.OrderLineSummary;
import kr.hhplus.be.server.common.lock.DistributedLock;
import kr.hhplus.be.server.common.lock.LockKey;
import kr.hhplus.be.server.coupon.application.CouponService;
import kr.hhplus.be.server.coupon.domain.Coupon;
import kr.hhplus.be.server.order.application.OrderService;
import kr.hhplus.be.server.order.domain.Order;
import kr.hhplus.be.server.order.domain.OrderItem;
import kr.hhplus.be.server.product.application.ProductService;
import kr.hhplus.be.server.product.domain.Product;
import kr.hhplus.be.server.user.application.UserService;
import kr.hhplus.be.server.user.domain.User;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class OrderFacade {

    private final ApplicationEventPublisher eventPublisher;
    private final UserService userService;
    private final ProductService productService;
    private final CouponService couponService;
    private final OrderService orderService;
    private final BalanceService balanceService;
    // private final TopProductService topProductService;

    @Transactional
    @DistributedLock(prefix = LockKey.PRODUCT, ids = "#orderItems.![productId]")
    public Order placeOrder(Long userId, List<OrderItemCommand> orderItems, Long couponId) {
        User user = userService.findById(userId);

        List<OrderItem> items = orderItems.stream()
                .sorted(Comparator.comparing(OrderItemCommand::getProductId)) // 상품 순서 정렬
                .map(command -> {
                    Product product = productService.verifyAndDecreaseStock(command.getProductId(),
                            command.getQuantity());
                    return OrderItem.of(product, product.getPrice(), command.getQuantity(), 0);
                })
                .toList();

        int total = items.stream()
                .mapToInt(OrderItem::getSubtotal)
                .sum();

        if (couponId != null) {
            Coupon coupon = couponService.useCoupon(couponId, user.getId());
            int discount = coupon.getDiscountRate() > 0
                    ? (int) (total * coupon.getDiscountRate())
                    : coupon.getDiscountAmount();
            total = Math.max(0, total - discount);
        }

        balanceService.useBalance(user, total);

        Order order = orderService.createOrder(user, items, total);

        List<OrderLineSummary> lines = items.stream()
                .map(i -> new OrderLineSummary(i.getProduct().getId(), i.getQuantity()))
                .toList();

        // 비동기로 요청 > 이벤트 방식으로 수정예정
        // topProductService.recordOrdersAsync(rankingDtos);
        eventPublisher.publishEvent(new OrderCompletedEvent(order.getId(), userId, lines));

        return order;
    }

}
