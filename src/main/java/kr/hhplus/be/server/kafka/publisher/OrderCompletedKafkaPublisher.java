package kr.hhplus.be.server.kafka.publisher;

import kr.hhplus.be.server.common.event.order.OrderCompletedEvent;
import kr.hhplus.be.server.kafka.message.OrderCompletedMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.Instant;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderCompletedKafkaPublisher {

    private final KafkaTemplate<String, OrderCompletedMessage> kafkaTemplate;


    @Value("${app.kafka.topics.order-completed}")
    private String topic;


    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void on(OrderCompletedEvent event) {
        OrderCompletedMessage message = new OrderCompletedMessage(
                String.valueOf(event.orderId()),
                String.valueOf(event.userId()), 
                event.lines().stream()
                        .map(l -> new OrderCompletedMessage.Line(l.productId(), l.quantity()))
                        .toList(),
                Instant.now()
        );

        String key = String.valueOf(event.orderId());

        kafkaTemplate.send(topic, key, message).whenComplete((result, ex) -> {
            if (ex != null) {
                log.error("[KAFKA] publish failed: orderId={}, reason={}", event.orderId(), ex.getMessage(), ex);
            } else {
                var md = result.getRecordMetadata();
                log.info("[KAFKA] published: orderId={}, partition={}, offset={}", event.orderId(), md.partition(), md.offset());
            }
        });
    }
}
