package kr.hhplus.be.server.coupon.application;

import kr.hhplus.be.server.coupon.domain.CouponPolicy;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
@RequiredArgsConstructor
public class CouponWorker {

    private final CouponService couponService;

    @Scheduled(fixedDelay = 10_000) // 10초마다 실행
    public void syncActivePolicies() {
        List<CouponPolicy> activePolicies = couponService.getActivePolicies();

        List<Long> activePolicyIds = activePolicies.stream()
                .map(CouponPolicy::getId)
                .toList();

        List<Long> redisPolicyIds = couponService.getAllPolicyIdsInRedis();

        for (Long redisPolicyId : redisPolicyIds) {
            if (!activePolicyIds.contains(redisPolicyId)) {
                couponService.removePolicy(redisPolicyId);
            }
        }

        for (CouponPolicy policy : activePolicies) {
            couponService.setRemainingCount(policy.getId(), policy.getRemainingCount());
        }
    }

    // 확정처리는 Kafka로 이전
    // @Scheduled(fixedDelay = 1000) // 1초마다 실행
    public void processAllPending() {
        List<Long> policyIds = couponService.getAllPolicyIdsInRedis();

        for (Long policyId : policyIds) {
            while (true) {
                List<Long> userIds = couponService.peekPending(policyId, 500);

                // PENDING 수동 만료 전략
                if (userIds.isEmpty()) {
                    couponService.removePendingKey(policyId);
                    break;
                }

                List<Long> succeeded = new ArrayList<>();
                for (Long userId : userIds) {
                    try {
                        couponService.issueCoupon(userId, policyId);
                        succeeded.add(userId);
                    } catch (Exception ignored) {
                    }
                }
                couponService.removePending(policyId, succeeded);
            }
        }
    }
}
