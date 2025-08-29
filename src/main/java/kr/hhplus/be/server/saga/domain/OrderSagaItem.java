package kr.hhplus.be.server.saga.domain;

import jakarta.persistence.Embeddable;
import lombok.*;

@Embeddable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class OrderSagaItem {
    private Long productId;
    private int quantity;
}
