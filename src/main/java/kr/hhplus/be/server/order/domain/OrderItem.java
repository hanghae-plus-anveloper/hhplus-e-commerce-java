package kr.hhplus.be.server.order.domain;

import jakarta.persistence.*;
import kr.hhplus.be.server.product.domain.Product;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Getter
@NoArgsConstructor
@Table(name = "order_item", indexes = {@Index(name = "idx_order_item_ordered_date_product", columnList = "ordered_date, product_id")
        // @Index(name = "idx_order_item_ordered_product", columnList = "ordered_at, product_id"), // X, 통계용 복합, 반정규화 한 ordered_at 으로 3일 이내 TOP 5 목적 방법2
        // @Index(name = "idx_order_item_product_ordered", columnList = "product_id, ordered_at"), // X, 상품 → 날짜 순서 차이 테스트용 목적
        // @Index(name = "idx_order_item_ordered_at", columnList = "ordered_at"), // X, 날짜 필터
})
public class OrderItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private int price; // 역정규화: Product의 Price지만 facade에서 주문 시점의 금액을 별도로 저장한다. 상품의 가격변동과 분리
    private int quantity;
    private int discountAmount;

    @Setter
    private LocalDateTime orderedAt; // 역정규화: OrderItem이 아닌 Order의 생성시점이며, Order 생성자에서 Setter로 직접 주입

    @Setter
    @Column(name = "ordered_date")
    private LocalDate orderedDate;

    @Setter
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Setter
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id")
    private Order order;

    static OrderItem create(Product product, int price, int quantity, int discountAmount, Order order, LocalDateTime orderedAt, LocalDate orderedDate) {
        if (product == null) throw new IllegalArgumentException("상품은 필수입니다.");
        if (price < 0) throw new IllegalArgumentException("상품 가격은 0 이상이어야 합니다.");
        if (quantity <= 0) throw new IllegalArgumentException("상품 수량은 1 이상이어야 합니다.");

        OrderItem item = new OrderItem();
        item.product = product;
        item.price = price;
        item.quantity = quantity;
        item.discountAmount = discountAmount;
        item.order = order;
        item.orderedAt = orderedAt;
        item.orderedDate = orderedDate;
        return item;
    }

    public static OrderItem of(Product product, int price, int quantity, int discountAmount) {
        OrderItem item = new OrderItem();
        item.product = product;
        item.price = price;
        item.quantity = quantity;
        item.discountAmount = discountAmount;
        return item;
    }

    public int getSubtotal() {
        return (price * quantity) - discountAmount;
    }
}
