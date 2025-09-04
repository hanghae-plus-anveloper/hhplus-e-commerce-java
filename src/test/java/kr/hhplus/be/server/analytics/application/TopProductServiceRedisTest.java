package kr.hhplus.be.server.analytics.application;

import kr.hhplus.be.server.IntegrationTestContainersConfig;
import kr.hhplus.be.server.analytics.domain.TopProductView;
import kr.hhplus.be.server.balance.domain.Balance;
import kr.hhplus.be.server.balance.domain.BalanceRepository;
import kr.hhplus.be.server.external.mock.infrastructure.OrderExternalRedisRepository;
import kr.hhplus.be.server.kafka.message.OrderCompletedMessage;
import kr.hhplus.be.server.order.domain.Order;
import kr.hhplus.be.server.order.domain.OrderRepository;
import kr.hhplus.be.server.order.facade.OrderFacade;
import kr.hhplus.be.server.order.facade.OrderItemCommand;
import kr.hhplus.be.server.product.domain.Product;
import kr.hhplus.be.server.product.domain.ProductRepository;
import kr.hhplus.be.server.user.domain.User;
import kr.hhplus.be.server.user.domain.UserRepository;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Duration;
import java.time.LocalDate;
import java.util.*;
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


    @Test
    @DisplayName("OrderCompletedEvent 발생 시 Kafka 메시지가 발행된다")
    void kafkaMessageProduced(@Value("${app.kafka.topics.order-completed}") String topic) {
        // test 컨슈머
        try (KafkaConsumer<String, OrderCompletedMessage> consumer = newTestConsumer()) {
            consumer.subscribe(Collections.singletonList(topic)); // OrderCompleted Topic 구독
            consumer.poll(Duration.ofMillis(0));

            // 10건 주문 생성하여, 10건이 발생되는 지, 파티션별 순서가 보장되는 지 확인
            int expected = 10;
            IntStream.rangeClosed(1, expected)
                    .forEach(i -> orderFacade.placeOrder(
                            user.getId(),
                            List.of(new OrderItemCommand(p1.getId(), 1)),
                            null
                    ));

            // 최대 10초 동안
            long deadline = System.currentTimeMillis() + 10_000;
            List<ConsumerRecord<String, OrderCompletedMessage>> collected = new ArrayList<>();

            while (System.currentTimeMillis() < deadline && collected.size() < expected) {
                ConsumerRecords<String, OrderCompletedMessage> polled = consumer.poll(Duration.ofMillis(500));
                polled.forEach(collected::add);
            }

            printKafkaRecords(collected);

            // 10건 수집 건수 확인, 건 별 정합성 확인
            assertThat(collected.size()).isGreaterThanOrEqualTo(expected);
            for (ConsumerRecord<String, OrderCompletedMessage> rec : collected) {
                OrderCompletedMessage msg = rec.value();
                assertThat(msg).isNotNull();
                assertThat(msg.userId()).isEqualTo(user.getId().toString());
                assertThat(msg.lines()).isNotEmpty();
            }

            // 파티션 별 순번 보장(증가하는 지) 확인
            Map<TopicPartition, Long> lastOffsetByPartition = new HashMap<>();
            for (ConsumerRecord<String, OrderCompletedMessage> rec : collected) {
                TopicPartition tp = new TopicPartition(rec.topic(), rec.partition());
                Long prev = lastOffsetByPartition.get(tp);
                if (prev != null) {
                    assertThat(rec.offset()).isGreaterThan(prev);
                }
                lastOffsetByPartition.put(tp, rec.offset());
            }
        }
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

    private void printKafkaRecords(
            List<org.apache.kafka.clients.consumer.ConsumerRecord<String, OrderCompletedMessage>> records
    ) {
        System.out.println("=== Kafka 수집 결과 ===");
        for (var r : records) {
            var v = r.value();
            System.out.printf("partition=%d\toffset=%d\tkey=%s\torderId=%s\tuserId=%s\tlines=%d\tcompletedAt=%s%n",
                    r.partition(), r.offset(), r.key(), v != null ? v.orderId() : "null", v != null ? v.userId() : "null",
                    v != null && v.lines() != null ? v.lines().size() : -1, v != null ? v.completedAt() : "null");
        }
    }

    // test consumer 세팅
    private KafkaConsumer<String, kr.hhplus.be.server.kafka.message.OrderCompletedMessage> newTestConsumer() {
        Properties props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, System.getProperty("spring.kafka.bootstrap-servers"));
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "test-consumer-" + UUID.randomUUID());
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, "org.apache.kafka.common.serialization.StringDeserializer");
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, "org.springframework.kafka.support.serializer.JsonDeserializer");
        props.put(JsonDeserializer.TRUSTED_PACKAGES, "kr.hhplus.*");
        props.put(JsonDeserializer.USE_TYPE_INFO_HEADERS, false);
        props.put(JsonDeserializer.VALUE_DEFAULT_TYPE, "kr.hhplus.be.server.kafka.message.OrderCompletedMessage");

        return new KafkaConsumer<>(props);
    }
}
