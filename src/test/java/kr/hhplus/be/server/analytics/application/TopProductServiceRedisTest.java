package kr.hhplus.be.server.analytics.application;

import kr.hhplus.be.server.IntegrationTestContainersConfig;
import kr.hhplus.be.server.analytics.domain.TopProductView;
import kr.hhplus.be.server.balance.domain.Balance;
import kr.hhplus.be.server.balance.domain.BalanceRepository;
import kr.hhplus.be.server.external.mock.infrastructure.OrderExternalRedisRepository;
import kr.hhplus.be.server.order.domain.Order;
import kr.hhplus.be.server.order.domain.OrderRepository;
import kr.hhplus.be.server.order.facade.OrderFacade;
import kr.hhplus.be.server.order.facade.OrderItemCommand;
import kr.hhplus.be.server.product.domain.Product;
import kr.hhplus.be.server.product.domain.ProductRepository;
import kr.hhplus.be.server.user.domain.User;
import kr.hhplus.be.server.user.domain.UserRepository;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Testcontainers
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Import(IntegrationTestContainersConfig.class)
public class TopProductServiceRedisTest {

    @Autowired private OrderFacade orderFacade;
    @Autowired private UserRepository userRepository;
    @Autowired private BalanceRepository balanceRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private OrderRepository orderRepository;
    @Autowired private TopProductService topProductService;
    @Autowired private OrderExternalRedisRepository externalRedisRepository;

    private User user;
    private Product p1, p2, p3, p4, p5, p6;

    @BeforeEach
    void setUp() {
        orderRepository.deleteAll();
        productRepository.deleteAll();
        userRepository.deleteAll();
        productRepository.deleteAll();
        topProductService.clearAll();

        user = userRepository.save(User.builder().name("ranking-user").build());
        balanceRepository.save(Balance.builder().user(user).balance(1_000_000).build());

        p1 = productRepository.save(Product.builder().name("p1").price(100).stock(100).build());
        p2 = productRepository.save(Product.builder().name("p2").price(100).stock(100).build());
        p3 = productRepository.save(Product.builder().name("p3").price(100).stock(100).build());
        p4 = productRepository.save(Product.builder().name("p4").price(100).stock(100).build());
        p5 = productRepository.save(Product.builder().name("p5").price(100).stock(100).build());
        p6 = productRepository.save(Product.builder().name("p6").price(100).stock(100).build());

        // 오늘 이전 데이터 추가(날짜를 받아서 redis에 직접 세팅)
        IntStream.rangeClosed(1, 5).forEach(i -> record(p2, 5, 1)); // 1일 전, 5회 * 5개
        IntStream.rangeClosed(1, 5).forEach(i -> record(p3, 4, 2)); // 2일 전, 5회 * 4개
        IntStream.rangeClosed(1, 5).forEach(i -> record(p4, 3, 1)); // 1일 전, 5회 * 3개
        IntStream.rangeClosed(1, 5).forEach(i -> record(p5, 2, 2)); // 2일 전, 5회 * 2개
        IntStream.rangeClosed(1, 5).forEach(i -> record(p6, 1, 3)); // 3일 전, 5회 * 1개 - 조회되지 않아야 함
    }

    @Test
    @DisplayName("최근 3일 간 누적 집계를 통해 Top5 상품을 Redis 집계 정보를 기반으로 조회한다")
    void top5InLast3DaysWithRedis() throws InterruptedException {

        List<TopProductView> before = topProductService.getTop5InLast3DaysFromRedis();
        printRanking("오늘자 주문 발생 전 집계", before);
        assertThat(before).hasSize(4); // 6번 상품은 포함되지 않음 getTop5InLast3Days 검증

        // 오늘 자 주문 생성, 생성 시 Redis에 추가 되는 지, 집계 결과에 합산 되는 지 확인
        IntStream.rangeClosed(1, 5).forEach(i -> place(p1, 10)); // 오늘, 5회 * 10개 = 50개 TOP 1
        IntStream.rangeClosed(1, 4).forEach(i -> place(p2, 1)); // 오늘, 4회 * 1개
        IntStream.rangeClosed(1, 3).forEach(i -> place(p3, 1)); // 오늘, 3회 * 1개
        IntStream.rangeClosed(1, 2).forEach(i -> place(p4, 1)); // 오늘, 2회 * 1개
        IntStream.rangeClosed(1, 1).forEach(i -> place(p5, 1)); // 오늘, 1회 * 1개 // 총 15회 주문 완료

        List<TopProductView> after = topProductService.getTop5InLast3DaysFromRedisWithoutCache();
        TimeUnit.SECONDS.sleep(2);
        printRanking("오늘자 주문 발생 후 집계", after);

        assertThat(after).hasSize(5);
        assertThat(after.get(0).productId()).isEqualTo(p1.getId());
    }

    @Test
    @DisplayName("OrderCompletedEvent 발생 시 두 개의 핸들러(집계, 외부)가 모두 동작한다")
    void bothHandlersTriggered() throws Exception {
        Product product = productRepository.save(Product.builder().name("p10").price(100).stock(20).build());

        Order order = orderFacade.placeOrder(user.getId(), List.of(new OrderItemCommand(product.getId(), 2)), null);

        Thread.sleep(1000);

        List<TopProductView> ranking = topProductService.getTop5InLast3DaysFromRedisWithoutCache();
        printRanking("집계 핸들러 결과", ranking);

        boolean productRanked = ranking.stream().anyMatch(r -> r.productId().equals(product.getId()));
        assertThat(productRanked).isTrue();

        Long externalCount = externalRedisRepository.countRecords(order.getId());
        printExternal(order.getId(), externalCount);

        assertThat(externalCount).isGreaterThanOrEqualTo(1L);

    }

    private void place(Product product, int quantity) {
        orderFacade.placeOrder(user.getId(), List.of(new OrderItemCommand(product.getId(), quantity)), null);
    }

    // 오늘 날짜 이전(어제 ~ ) 데이터는 service에 직접 주입(집계용)
    private void record(Product product, int qty, int daysAgo) {
        topProductService.recordOrder(product.getId().toString(), qty, LocalDate.now().minusDays(daysAgo));
    }

    private void printRanking(String title, List<TopProductView> rankings) {
        System.out.println("=== " + title + " ===");
        for (int i = 0; i < rankings.size(); i++) {
            TopProductView dto = rankings.get(i);
            System.out.printf("%d위 - 상품ID: %d, 상품명: %s, 판매수량: %d%n",
                    i + 1,
                    dto.productId(),
                    dto.name(),
                    dto.soldQty());

        }
    }
    private void printExternal(Long orderId, Long count) {
        System.out.printf("=== %s ===%n", "외부 핸들러 결과");
        System.out.printf("주문ID: %d, 외부 기록 수: %d%n", orderId, count);
    }
}
