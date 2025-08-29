package kr.hhplus.be.server.coupon.domain;

import jakarta.persistence.*;
import kr.hhplus.be.server.coupon.exception.InvalidCouponException;
import kr.hhplus.be.server.user.domain.User;
import lombok.*;

@Entity
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
@Table(name = "coupon", indexes = {
        @Index(name = "idx_coupon_user_used", columnList = "user_id, used"), // 사용 여부 필터 조회 용 목적
        @Index(name = "idx_coupon_policy_user", columnList = "policy_id, user_id"), // 중복 발급 방지 등 정책 기준 비즈니스 목적
})
public class Coupon {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "policy_id", nullable = false)
    private CouponPolicy policy;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @ManyToOne(fetch = FetchType.LAZY) //
    @JoinColumn(name = "user_id", insertable = false, updatable = false)
    private User user;

    private int discountAmount; // 정액 할인
    private double discountRate; // 정률 할인
    private boolean used;

    public void use() {
        if (!isAvailable()) {
            throw new InvalidCouponException("사용할 수 없는 쿠폰입니다.");
        }
        this.used = true;
    }

    public void restore() {
        if (!used) {
            throw new InvalidCouponException("이미 사용 취소된 쿠폰입니다.");
        }
        this.used = false;
    }

    public boolean isAvailable() {
        return !used && policy.isWithinPeriod();
    }
}
