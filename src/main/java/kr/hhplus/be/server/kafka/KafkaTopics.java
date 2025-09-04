package kr.hhplus.be.server.kafka;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.stereotype.Component;

@Component
public class KafkaTopics {

    @Bean
    NewTopic orderCompletedTopic(
            @Value("${app.kafka.topics.order-completed}") String name,
            @Value("${app.kafka.partitions.order-completed:3}") int partitions
    ) {
        return TopicBuilder.name(name).partitions(partitions).replicas(1).build();
    }


    @Bean
    NewTopic couponPendedTopic(
            @Value("${app.kafka.topics.coupon-pended}") String name,
            @Value("${app.kafka.partitions.coupon-pended:3}") int partitions
    ) {
        return TopicBuilder.name(name).partitions(partitions).replicas(1).build();
    }

}
