package kr.hhplus.be.server.order.facade;

import kr.hhplus.be.server.analytics.application.TopProductService;
import kr.hhplus.be.server.balance.application.BalanceService;
import kr.hhplus.be.server.coupon.application.CouponService;
import kr.hhplus.be.server.coupon.domain.Coupon;
import kr.hhplus.be.server.order.application.OrderService;
import kr.hhplus.be.server.order.domain.Order;
import kr.hhplus.be.server.product.application.ProductService;
import kr.hhplus.be.server.product.domain.Product;
import kr.hhplus.be.server.user.application.UserService;
import kr.hhplus.be.server.user.domain.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class OrderFacadeTest {

    private UserService userService;
    private ProductService productService;
    private CouponService couponService;
    private OrderService orderService;
    private BalanceService balanceService;
    private OrderFacade orderFacade;
    private ApplicationEventPublisher eventPublisher;

    @BeforeEach
    void setUp() {
        eventPublisher = mock(ApplicationEventPublisher.class);
        userService = mock(UserService.class);
        productService = mock(ProductService.class);
        couponService = mock(CouponService.class);
        orderService = mock(OrderService.class);
        balanceService = mock(BalanceService.class);
        orderFacade = new OrderFacade(eventPublisher, userService, productService, couponService, orderService, balanceService);
    }

    @Test
    @DisplayName("쿠폰 없이 주문을 성공적으로 생성한다")
    void placeOrder_withoutCoupon_success() {
        Long userId = 1L;
        Long productId = 10L;
        int quantity = 2;

        User user = new User("테스트 유저");
        ReflectionTestUtils.setField(user, "id", userId);

        Product product = Product.builder().name("상품A").price(1000).stock(10).build();
        OrderItemCommand command = new OrderItemCommand(productId, quantity);
        Order mockOrder = mock(Order.class);

        when(userService.findById(userId)).thenReturn(user);
        when(productService.verifyAndDecreaseStock(productId, quantity)).thenReturn(product);
        when(orderService.createOrder(eq(user), anyList(), eq(2000))).thenReturn(mockOrder);

        Order result = orderFacade.placeOrder(userId, List.of(command), null);

        assertThat(result).isNotNull();
        verify(userService).findById(userId);
        verify(productService).verifyAndDecreaseStock(productId, quantity);
        verify(balanceService).useBalance(user, 2000);
        verify(orderService).createOrder(eq(user), anyList(), eq(2000));
        verifyNoInteractions(couponService);
    }

    @Test
    @DisplayName("쿠폰 사용 시 할인 적용 후 주문을 생성한다")
    void placeOrder_withCoupon_success() {
        Long userId = 2L;
        Long couponId = 99L;
        Long productId = 20L;
        int quantity = 1;

        User user = new User("쿠폰 사용자");
        ReflectionTestUtils.setField(user, "id", userId);

        Product product = Product.builder().name("상품B").price(1000).stock(5).build();
        OrderItemCommand command = new OrderItemCommand(productId, quantity);
        Coupon coupon = mock(Coupon.class);
        Order mockOrder = mock(Order.class);

        when(userService.findById(userId)).thenReturn(user);
        when(productService.verifyAndDecreaseStock(productId, quantity)).thenReturn(product);
        when(couponService.useCoupon(couponId, userId)).thenReturn(coupon);
        when(coupon.getDiscountRate()).thenReturn(0.2); // 20% 할인
        when(coupon.getDiscountAmount()).thenReturn(0);
        when(orderService.createOrder(eq(user), anyList(), eq(800))).thenReturn(mockOrder);

        Order result = orderFacade.placeOrder(userId, List.of(command), couponId);

        assertThat(result).isNotNull();
        verify(balanceService).useBalance(user, 800);
        verify(orderService).createOrder(eq(user), anyList(), eq(800));
    }
}
