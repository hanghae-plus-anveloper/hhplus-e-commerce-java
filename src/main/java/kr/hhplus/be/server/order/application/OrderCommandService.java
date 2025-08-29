package kr.hhplus.be.server.order.application;

import kr.hhplus.be.server.common.event.order.OrderDraftedEvent;
import kr.hhplus.be.server.common.lock.DistributedLock;
import kr.hhplus.be.server.common.lock.LockKey;
import kr.hhplus.be.server.order.domain.Order;
import kr.hhplus.be.server.order.domain.OrderRepository;
import kr.hhplus.be.server.order.facade.OrderItemCommand;
import kr.hhplus.be.server.saga.domain.OrderSagaMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class OrderCommandService {

    private final OrderRepository orderRepository;
    private final ApplicationEventPublisher publisher;

    @Transactional
    @DistributedLock(prefix = LockKey.ORDER, ids = "#userId")
    public Order createDraft(Long userId, List<OrderItemCommand> items, Long couponId) {

        Order order = Order.draft(userId, couponId);
        orderRepository.save(order);

        // Saga 초기화를 위해 DraftedEvent 발행
        publisher.publishEvent(new OrderDraftedEvent(
                order.getId(),
                order.getUserId(),
                OrderSagaMapper.toSagaItems(items),
                couponId
        ));

        return order;
    }
}
