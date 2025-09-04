package kr.hhplus.be.server.coupon.application;

import kr.hhplus.be.server.coupon.domain.Coupon;
import kr.hhplus.be.server.coupon.domain.CouponPolicy;
import kr.hhplus.be.server.coupon.domain.CouponPolicyRepository;
import kr.hhplus.be.server.coupon.domain.CouponRepository;
import kr.hhplus.be.server.coupon.exception.CouponSoldOutException;
import kr.hhplus.be.server.coupon.exception.InvalidCouponException;
import kr.hhplus.be.server.coupon.infrastructure.CouponRedisRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class CouponServiceTest {

    private CouponPolicyRepository couponPolicyRepository;
    private CouponRepository couponRepository;
    private CouponRedisRepository couponRedisRepository;
    private CouponService couponService;
    private ApplicationEventPublisher publisher;

    @BeforeEach
    void setUp() {
        couponPolicyRepository = mock(CouponPolicyRepository.class);
        couponRepository = mock(CouponRepository.class);
        couponService = new CouponService(couponPolicyRepository, couponRepository, couponRedisRepository, publisher);
    }

    @Test
    @DisplayName("쿠폰 발급 - 성공")
    void issueCoupon_success() {
        long userId = 1L; // id 없음
        CouponPolicy policy = CouponPolicy.builder()
                .discountAmount(1000)
                .availableCount(10)
                .remainingCount(10)
                .expireDays(30)
                .startedAt(LocalDateTime.now().minusMinutes(1))
                .endedAt(LocalDateTime.now().plusHours(1))
                .build();

        when(couponPolicyRepository.findById(1L)).thenReturn(Optional.of(policy));
        when(couponPolicyRepository.decreaseRemainingCount(1L)).thenReturn(1);
        when(couponRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        Coupon coupon = couponService.issueCoupon(userId, 1L);

        assertThat(coupon).isNotNull();
        assertThat(coupon.getUserId()).isEqualTo(userId);
        assertThat(coupon.getPolicy()).isEqualTo(policy);
        assertThat(coupon.isUsed()).isFalse();
    }

    @Test
    @DisplayName("쿠폰 발급 - 존재하지 않는 정책")
    void issueCoupon_invalidPolicy() {long userId = 1L;

        when(couponPolicyRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> couponService.issueCoupon(userId, 999L))
                .isInstanceOf(InvalidCouponException.class)
                .hasMessageContaining("존재하지 않는 쿠폰 정책");
    }

    @Test
    @DisplayName("쿠폰 발급 - 만료된 정책")
    void issueCoupon_expiredPolicy() {
        long userId = 1L;

        CouponPolicy expiredPolicy = CouponPolicy.builder()
                .discountRate(0.1)
                .availableCount(10)
                .remainingCount(10)
                .expireDays(30)
                .startedAt(LocalDateTime.now().minusHours(2))
                .endedAt(LocalDateTime.now().minusHours(1))
                .build();

        when(couponPolicyRepository.findById(2L)).thenReturn(Optional.of(expiredPolicy));

        assertThatThrownBy(() -> couponService.issueCoupon(userId, 2L))
                .isInstanceOf(InvalidCouponException.class)
                .hasMessageContaining("유효하지 않습니다");
    }

    @Test
    @DisplayName("쿠폰 발급 - 수량 초과")
    void issueCoupon_soldOut() {
        long userId = 1L;

        CouponPolicy soldOutPolicy = CouponPolicy.builder()
                .discountAmount(1000)
                .discountRate(0.1)
                .availableCount(10)
                .remainingCount(0) // 수량 없음
                .expireDays(30)
                .startedAt(LocalDateTime.now().minusMinutes(1))
                .endedAt(LocalDateTime.now().plusHours(1))
                .build();

        when(couponPolicyRepository.findById(3L)).thenReturn(Optional.of(soldOutPolicy));

        assertThatThrownBy(() -> couponService.issueCoupon(userId, 3L))
                .isInstanceOf(CouponSoldOutException.class)
                .hasMessageContaining("남은 쿠폰 수량이 없습니다");
    }

}
