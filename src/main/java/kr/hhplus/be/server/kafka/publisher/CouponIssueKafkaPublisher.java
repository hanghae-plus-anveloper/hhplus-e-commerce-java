package kr.hhplus.be.server.kafka.publisher;

import kr.hhplus.be.server.common.event.coupon.CouponPendedEvent;
import kr.hhplus.be.server.kafka.message.CouponPendedMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Slf4j
@Component
@RequiredArgsConstructor
public class CouponIssueKafkaPublisher {

    private final KafkaTemplate<String, CouponPendedMessage> kafkaTemplate;

    @Value("${app.kafka.topics.coupon-pended}")
    private String topic;

    @EventListener
    public void on(CouponPendedEvent event) {
        CouponPendedMessage msg = new CouponPendedMessage(event.userId(), event.policyId(), Instant.now());
        String key = String.valueOf(event.policyId());

        kafkaTemplate.send(topic, key, msg).whenComplete((res, ex) -> {
            if (ex != null) {
                log.error("[KAFKA] publish failed: policyId={}, userId={}, reason={}", event.policyId(), event.userId(), ex.getMessage(), ex);
            } else {
                var md = res.getRecordMetadata();
                log.info("[KAFKA] published coupon pended: topic={}, partition={}, offset={}", md.topic(), md.partition(), md.offset());
            }
        });
    }
}