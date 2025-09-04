package kr.hhplus.be.server.kafka.config;

import kr.hhplus.be.server.kafka.message.CouponPendedMessage;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.support.serializer.JsonDeserializer;

import java.util.HashMap;
import java.util.Map;

@EnableKafka
@Configuration
public class KafkaCouponConsumerConfig {

    @Bean
    public ConsumerFactory<String, CouponPendedMessage> couponPendedConsumerFactory(KafkaProperties properties) {
        Map<String, Object> props = new HashMap<>(properties.buildConsumerProperties());

        props.keySet().removeIf(k -> k.toString().startsWith("spring.json."));

        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, JsonDeserializer.class);

        JsonDeserializer<CouponPendedMessage> value = new JsonDeserializer<>(CouponPendedMessage.class);
        value.ignoreTypeHeaders();
        value.addTrustedPackages("kr.hhplus.*");

        return new DefaultKafkaConsumerFactory<>(props, new StringDeserializer(), value);
    }

    @Bean(name = "couponPendedKafkaListenerContainerFactory")
    public ConcurrentKafkaListenerContainerFactory<String, CouponPendedMessage> couponPendedKafkaListenerContainerFactory(
            ConsumerFactory<String, CouponPendedMessage> couponPendedConsumerFactory
    ) {
        ConcurrentKafkaListenerContainerFactory<String, CouponPendedMessage> factory = new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(couponPendedConsumerFactory);
        factory.setBatchListener(false);
        return factory;
    }
}
