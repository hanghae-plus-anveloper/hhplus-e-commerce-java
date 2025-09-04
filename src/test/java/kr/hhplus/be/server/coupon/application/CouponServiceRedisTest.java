package kr.hhplus.be.server.coupon.application;

import kr.hhplus.be.server.IntegrationTestContainersConfig;
import kr.hhplus.be.server.coupon.domain.CouponPolicy;
import kr.hhplus.be.server.coupon.domain.CouponPolicyRepository;
import kr.hhplus.be.server.coupon.domain.CouponRepository;
import kr.hhplus.be.server.coupon.infrastructure.CouponRedisRepository;
import kr.hhplus.be.server.user.domain.User;
import kr.hhplus.be.server.user.domain.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Import(IntegrationTestContainersConfig.class)
public class CouponServiceRedisTest {

    @Autowired
    private CouponService couponService;

    @Autowired
    private CouponWorker couponWorker;

    @Autowired
    private CouponRepository couponRepository;

    @Autowired
    private CouponPolicyRepository couponPolicyRepository;

    @Autowired
    private CouponRedisRepository couponRedisRepository;

    @Autowired
    private UserRepository userRepository;

    @BeforeEach
    void setUp() {
        couponRepository.deleteAll();
        userRepository.deleteAll();
        couponPolicyRepository.deleteAll();
        couponRedisRepository.clearAll();
    }

    @Test
    @DisplayName("잔여수량이 1000개 쿠폰 정책에 대해 2000명이 동시에 발급 요청 시 Facade 응답 카운트와 DB 저장 결과를 검증한다")
    void issueCoupon_concurrently_facade() throws InterruptedException {
        for (int i = 1; i <= 2000; i++) {
            userRepository.save(User.builder().name("user-" + i).build());
        }

        int availableCount = 1000;
        CouponPolicy policy = couponPolicyRepository.save(
                CouponPolicy.builder()
                        .discountAmount(1000)
                        .availableCount(availableCount)
                        .remainingCount(availableCount)
                        .expireDays(30)
                        .startedAt(LocalDateTime.now().minusDays(1))
                        .endedAt(LocalDateTime.now().plusDays(10))
                        .build()
        );

        // couponWorker.syncActivePolicies();
        TimeUnit.SECONDS.sleep(12); // Worker 정책 10초마다 동기화

        List<User> users = userRepository.findAll();

        ExecutorService executor = Executors.newFixedThreadPool(20);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(users.size());

        AtomicInteger acceptedCount = new AtomicInteger();
        AtomicInteger rejectedCount = new AtomicInteger();

        int batchSize = 200; // 200명씩 10번 나눠서 동시 요청
        for (int i = 0; i < users.size(); i += batchSize) {
            List<User> batch = users.subList(i, Math.min(i + batchSize, users.size()));

            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch doneLatch = new CountDownLatch(batch.size());

            for (User user : batch) {
                executor.submit(() -> {
                    try {
                        startLatch.await(); // 동시에 시작
                        boolean result = couponService.tryIssue(user.getId(), policy.getId());
                        if (result) {
                            acceptedCount.incrementAndGet();
                        } else {
                            rejectedCount.incrementAndGet();
                        }
                    } catch (Exception e) {
                        rejectedCount.incrementAndGet();
                    } finally {
                        doneLatch.countDown();
                    }
                });
            }

            startLatch.countDown();
            doneLatch.await(5, TimeUnit.SECONDS);
            System.out.println("그룹\t" + (i / batchSize + 1) + "\t완료");
        }

        start.countDown();
        done.await(2, TimeUnit.SECONDS);
        executor.shutdownNow();

        System.out.println("Facade 수락된 요청 수: " + acceptedCount.get());
        System.out.println("Facade 거절된 요청 수: " + rejectedCount.get());

        // couponWorker.processAllPending();
        TimeUnit.SECONDS.sleep(3); // Worker process(limit 500) 1초마다, 1000건 2초 이상.

        long dbCount = couponRepository.count();
        System.out.println("DB 저장된 쿠폰 수: " + dbCount);

        assertThat(acceptedCount.get()).isEqualTo(availableCount);
        assertThat(dbCount).isEqualTo(availableCount);
    }
}
