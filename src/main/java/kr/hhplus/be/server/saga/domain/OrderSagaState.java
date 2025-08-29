package kr.hhplus.be.server.saga.domain;

import jakarta.persistence.*;
import lombok.*;

import java.util.List;

@Entity
@Table(name = "order_saga_state")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class OrderSagaState {

    @Id
    private Long orderId;   // 주문 ID (Order 엔티티 PK와 동일)

    private Long userId;

    @ElementCollection
    @CollectionTable(name = "order_saga_items", joinColumns = @JoinColumn(name = "order_id"))
    private List<OrderSagaItem> items;

    private Long couponId;

    // 각 도메인 상태
    @Enumerated(EnumType.STRING)
    private OrderSagaEventStatus productReserved;

    @Enumerated(EnumType.STRING)
    private OrderSagaEventStatus couponApplied;

    @Enumerated(EnumType.STRING)
    private OrderSagaEventStatus balanceCharged;

    // 금액 정보
    private Integer subTotalAmount;
    private Integer discountAmount;
    private Integer totalAmount;

    public void markProductReservedSuccess(int subTotalAmount) {
        this.productReserved = OrderSagaEventStatus.SUCCESS;
        this.subTotalAmount = subTotalAmount;
    }

    public void markCouponAppliedSuccess(int discountAmount) {
        this.couponApplied = OrderSagaEventStatus.SUCCESS;
        this.discountAmount = discountAmount;
    }

    public void markBalanceChargedSuccess(int totalAmount) {
        this.balanceCharged = OrderSagaEventStatus.SUCCESS;
        this.totalAmount = totalAmount;
    }

    public void markFailedDomain(String domain) {
        switch (domain) {
            case "PRODUCT" -> this.productReserved = OrderSagaEventStatus.FAILED;
            case "COUPON" -> this.couponApplied = OrderSagaEventStatus.FAILED;
            case "BALANCE" -> this.balanceCharged = OrderSagaEventStatus.FAILED;
        }
    }

    public boolean isReadyForCalculation() {
        return productReserved == OrderSagaEventStatus.SUCCESS
                && couponApplied == OrderSagaEventStatus.SUCCESS
                && subTotalAmount != null
                && discountAmount != null;
    }
}
