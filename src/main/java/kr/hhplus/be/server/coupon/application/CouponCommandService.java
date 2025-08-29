package kr.hhplus.be.server.coupon.application;

import java.util.List;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import kr.hhplus.be.server.common.event.coupon.CouponUseFailedEvent;
import kr.hhplus.be.server.common.event.coupon.CouponUsedEvent;
import kr.hhplus.be.server.common.lock.DistributedLock;
import kr.hhplus.be.server.common.lock.LockKey;
import kr.hhplus.be.server.coupon.domain.Coupon;
import kr.hhplus.be.server.coupon.domain.CouponRepository;
import kr.hhplus.be.server.coupon.exception.InvalidCouponException;
import kr.hhplus.be.server.saga.domain.OrderSagaItem;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class CouponCommandService {

    private final CouponRepository couponRepository;
    private final ApplicationEventPublisher publisher;

    @Transactional
    @DistributedLock(prefix = LockKey.COUPON, ids = "#couponId")
    public void useCoupon(Long couponId, Long userId, Long orderId, List<OrderSagaItem> items) {
        try {
            int updated = couponRepository.markCouponAsUsed(couponId, userId);
            if (updated == 0) {
                publisher.publishEvent(new CouponUseFailedEvent(
                        orderId, couponId, "쿠폰을 사용할 수 없거나 이미 사용됨", items));
                return;
            }
            Coupon coupon = couponRepository.findById(couponId)
                    .orElseThrow(() -> new InvalidCouponException("쿠폰 조회 실패: id=" + couponId));

            int discountAmount = coupon.getDiscountAmount();
            double discountRate = coupon.getDiscountRate();

            publisher.publishEvent(new CouponUsedEvent(orderId, couponId, discountAmount, discountRate));
        } catch (Exception e) {
            publisher.publishEvent(new CouponUseFailedEvent(orderId, couponId, e.getMessage(), items));
        }
    }

    @Transactional
    public void skipCoupon(Long orderId) {
        publisher.publishEvent(new CouponUsedEvent(orderId, null, 0, 0.0));
    }

    @Transactional
    @DistributedLock(prefix = LockKey.COUPON, ids = "#couponId")
    public void restoreCoupon(Long couponId) {
        couponRepository.restoreCouponIfUsed(couponId);
    }
}
