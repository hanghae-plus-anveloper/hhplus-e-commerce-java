package kr.hhplus.be.server.kafka.consumer;

import kr.hhplus.be.server.coupon.application.CouponService;
import kr.hhplus.be.server.coupon.domain.Coupon;
import kr.hhplus.be.server.kafka.message.CouponPendedMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class CouponIssueKafkaConsumer {

    private final CouponService couponService;

    @KafkaListener(
            topics = "${app.kafka.topics.coupon-pended}",
            groupId = "${app.kafka.consumer-group.coupon}",
            containerFactory = "couponPendedKafkaListenerContainerFactory"
    )
    public void onMessage(CouponPendedMessage msg) {
        try {
            Coupon coupon = couponService.issueCoupon(msg.userId(), msg.policyId());
            log.info("[KAFKA][coupon-pended] issue couponId={} userId={} policyId={}",
                    coupon.getId(), msg.userId(), msg.policyId());
        } catch (Exception e) {
            log.warn("[KAFKA][coupon-pended] issue failed userId={} policyId={} - {}",
                    msg.userId(), msg.policyId(), e.getMessage(), e);
        }
    }
}
