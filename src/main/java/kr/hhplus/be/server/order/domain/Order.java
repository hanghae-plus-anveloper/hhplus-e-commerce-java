package kr.hhplus.be.server.order.domain;

import jakarta.persistence.*;
import kr.hhplus.be.server.user.domain.User;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Getter
@NoArgsConstructor
@Table(name = "`order`", indexes = {
        @Index(name = "idx_order_user_status", columnList = "user_id, status"), // 유저별 주문 상태 조회, 사용자 관점 비즈니스 목적
        // @Index(name = "idx_order_ordered_at", columnList = "ordered_at"), // 날짜 필터, 3일 이내 TOP 5 조회 목적, 방법 1
})
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", insertable = false, updatable = false)
    private User user;

    private Long couponId;
    private Integer totalAmount;
    private Integer discountAmount;

    @Setter
    private OrderStatus status;

    private LocalDateTime orderedAt;

    @Setter
    @Column(name = "ordered_date")
    private LocalDate orderedDate;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<OrderItem> items = new ArrayList<>();


    // 기존 코드 유지용 overload
    public static Order create(User user, List<OrderItem> items, int totalAmount) {
        if (user == null) throw new IllegalArgumentException("User는 필수입니다.");
        return create(user.getId(), items, totalAmount, null);
    }

    // UserId 생성 방식
    public static Order create(Long userId, List<OrderItem> items, int totalAmount, Integer discountAmount) {
        if (userId == null) throw new IllegalArgumentException("userId는 필수입니다.");
        if (items == null || items.isEmpty()) throw new IllegalArgumentException("주문 아이템은 하나 이상이어야 합니다.");
        if (totalAmount < 0) throw new IllegalArgumentException("총 금액은 0 이상이어야 합니다.");

        Order order = new Order();
        order.userId = userId;
        order.status = OrderStatus.PAID; // 기존 테스트 로직 상 직접 생성 시(Saga X)
        order.orderedAt = LocalDateTime.now();
        order.orderedDate = LocalDate.now();
        order.totalAmount = totalAmount;
        order.discountAmount = discountAmount == null ? 0 : discountAmount;

        for (OrderItem item : items) {
            item.setOrder(order);
            item.setOrderedAt(order.orderedAt);
            item.setOrderedDate(order.orderedDate);
            order.items.add(item);
        }
        return order;
    }

    public static Order draft(Long userId, Long couponId) {
        if (userId == null) {
            throw new IllegalArgumentException("userId는 필수입니다.");
        }
        Order order = new Order();
        order.userId = userId;
        order.couponId = couponId;
        order.status = OrderStatus.DRAFT;
        order.orderedAt = LocalDateTime.now();
        order.orderedDate = LocalDate.now();
        order.totalAmount = null; // 아직 미확정
        order.discountAmount = null; // 아직 미확정
        return order;
    }

    public void confirmTotal(int totalAmount) {
        if (totalAmount < 0) throw new IllegalArgumentException("금액은 0 이상이어야 합니다.");
        this.totalAmount = totalAmount;
    }

    public void applyCoupon(Long couponId) {
        this.couponId = couponId;
    }

    public void markPending() {
        if (this.status != OrderStatus.DRAFT) {
            throw new IllegalStateException("DRAFT 상태에서만 PENDING으로 변경할 수 있습니다.");
        }
        this.status = OrderStatus.PENDING;
    }

    public void markPaid() {
        if (this.status != OrderStatus.PENDING) {
            throw new IllegalStateException("결제 대기 상태에서만 결제 완료로 전환 가능합니다.");
        }
        this.status = OrderStatus.PAID;
    }

    public void cancel() {
        if (this.status == OrderStatus.PAID) {
            throw new IllegalStateException("이미 결제 완료된 주문은 취소할 수 없습니다.");
        }
        this.status = OrderStatus.CANCELLED;
    }
}
