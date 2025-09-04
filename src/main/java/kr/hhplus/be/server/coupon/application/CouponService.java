package kr.hhplus.be.server.coupon.application;

import kr.hhplus.be.server.common.event.coupon.CouponPendedEvent;
import kr.hhplus.be.server.coupon.domain.Coupon;
import kr.hhplus.be.server.coupon.domain.CouponPolicy;
import kr.hhplus.be.server.coupon.domain.CouponPolicyRepository;
import kr.hhplus.be.server.coupon.domain.CouponRepository;
import kr.hhplus.be.server.coupon.exception.CouponSoldOutException;
import kr.hhplus.be.server.coupon.exception.InvalidCouponException;
import kr.hhplus.be.server.coupon.infrastructure.CouponRedisRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CouponService {

    private final CouponPolicyRepository couponPolicyRepository;
    private final CouponRepository couponRepository;
    private final CouponRedisRepository couponRedisRepository;
    private final ApplicationEventPublisher publisher;

    @Transactional(readOnly = true)
    public List<CouponPolicy> getActivePolicies() {
        return couponPolicyRepository.findAll()
                .stream()
                .filter(CouponPolicy::isWithinPeriod)
                .toList();
    }

    public boolean tryIssue(Long userId, Long policyId) {
        boolean accepted = couponRedisRepository.tryIssue(userId, policyId);
        if (accepted) {
            publisher.publishEvent(new CouponPendedEvent(userId, policyId));
        }
        return accepted;
    }

    @Transactional
    public Coupon issueCoupon(Long userId, Long policyId) {
        CouponPolicy policy = couponPolicyRepository.findById(policyId)
                .orElseThrow(() -> new InvalidCouponException("존재하지 않는 쿠폰 정책입니다."));

        if (!policy.isWithinPeriod()) {
            throw new InvalidCouponException("쿠폰 정책이 유효하지 않습니다.");
        }

        int updated = couponPolicyRepository.decreaseRemainingCount(policyId);
        if (updated == 0) {
            throw new CouponSoldOutException("남은 쿠폰 수량이 없습니다.");
        }

        Coupon coupon = Coupon.builder()
                .policy(policy)
                .userId(userId)
                .discountAmount(policy.getDiscountAmount())
                .discountRate(policy.getDiscountRate())
                .used(false)
                .build();

        return couponRepository.save(coupon);
    }

    @Transactional(readOnly = true)
    public List<Coupon> getCoupons(Long userId) {
        return couponRepository.findAllByUserId(userId);
    }

    @Transactional(readOnly = true)
    public Coupon findValidCouponOrThrow(Long couponId, Long userId) {
        Coupon coupon = couponRepository.findByIdAndUserId(couponId, userId)
                .orElseThrow(() -> new InvalidCouponException("쿠폰을 찾을 수 없습니다."));

        if (!coupon.isAvailable()) {
            throw new InvalidCouponException("사용할 수 없는 쿠폰입니다.");
        }

        return coupon;
    }

    @Transactional
    public Coupon useCoupon(Long couponId, Long userId) {
        Coupon coupon = findValidCouponOrThrow(couponId, userId);
        int updated = couponRepository.markCouponAsUsed(couponId, userId);
        if (updated == 0) {
            throw new InvalidCouponException("이미 사용된 쿠폰이거나 유효하지 않습니다.");
        }
        return couponRepository.findByIdAndUserId(couponId, userId)
                .orElseThrow(() -> new InvalidCouponException("쿠폰 조회 실패"));
    }

    public List<Long> getAllPolicyIdsInRedis() {
        return couponRedisRepository.getAllPolicyIds();
    }

    public void setRemainingCount(Long policyId, int remainingCount) {
        couponRedisRepository.setRemainingCount(policyId, remainingCount);
    }

    public void removePolicy(Long policyId) {
        couponRedisRepository.removePolicy(policyId);
    }

    public List<Long> peekPending(Long policyId, int limit) {
        return couponRedisRepository.peekPending(policyId, limit);
    }

    public void removePending(Long policyId, List<Long> userIds) {
        couponRedisRepository.removePending(policyId, userIds);
    }

    public void removePendingKey(Long policyId) {
        couponRedisRepository.removePendingKey(policyId);
    }
}
