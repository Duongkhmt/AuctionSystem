package com.duong.auction.system.config;

import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.*;
import org.springframework.kafka.retrytopic.DltStrategy;
import org.springframework.kafka.retrytopic.RetryTopicConfiguration;
import org.springframework.kafka.retrytopic.RetryTopicConfigurationBuilder;
import org.springframework.kafka.retrytopic.SameIntervalTopicReuseStrategy;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.kafka.support.serializer.JsonSerializer;

import java.util.HashMap;
import java.util.Map;

/**
 * ⚙️ CẤU HÌNH HẠ TẦNG KAFKA & NON-BLOCKING RETRY TOPIC + DEAD LETTER TOPIC (DLT) 3 TẦNG
 */
@Slf4j
@EnableKafka
@Configuration
public class KafkaConfig {

    @Value("${spring.kafka.bootstrap-servers:localhost:9092}")
    private String bootstrapServers;

    // 1. Topic chính tiếp nhận sự kiện kết thúc đấu giá
    public static final String TOPIC_AUCTION_ENDED = "auction.events.ended";

    // 2. Topic Retry không nghẽn mạch (Non-Blocking Retry Topic duy nhất)
    public static final String TOPIC_AUCTION_ENDED_RETRY = "auction.events.ended-retry";

    // 3. Topic Nghĩa Địa DLT (Dead Letter Topic duy nhất)
    public static final String TOPIC_AUCTION_ENDED_DLT = "auction.events.ended-dlt";

    /**
     * Tự động khởi tạo Topic chính "auction.events.ended" với 3 Partitions trên Kafka Broker
     */
    @Bean
    public NewTopic auctionEndedTopic() {
        return TopicBuilder.name(TOPIC_AUCTION_ENDED)
                .partitions(3)
                .build();
    }

    /**
     * Khởi tạo ProducerFactory tường minh cho Kafka Producer
     */
    @Bean
    public ProducerFactory<Object, Object> producerFactory() {
        Map<String, Object> configProps = new HashMap<>();
        configProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        configProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        configProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
        return new DefaultKafkaProducerFactory<>(configProps);
    }

    /**
     * Khai báo Bean KafkaTemplate tường minh cho Spring Boot Inject
     */
    @Bean
    public KafkaTemplate<Object, Object> kafkaTemplate() {
        return new KafkaTemplate<>(producerFactory());
    }

    /**
     * Khởi tạo ConsumerFactory tường minh cho Kafka Consumer
     */
    @Bean
    public ConsumerFactory<Object, Object> consumerFactory() {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "auction-service-group");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, JsonDeserializer.class);
        props.put(JsonDeserializer.TRUSTED_PACKAGES, "*");
        return new DefaultKafkaConsumerFactory<>(props);
    }

    /**
     * Khai báo ListenerContainerFactory tường minh cho @KafkaListener & Retry Topic
     */
    @Bean
    public ConcurrentKafkaListenerContainerFactory<Object, Object> kafkaListenerContainerFactory() {
        ConcurrentKafkaListenerContainerFactory<Object, Object> factory = new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory());
        return factory;
    }

    /**
     * 🔥 CẤU HÌNH NON-BLOCKING RETRY TOPIC & DLT DUY NHẤT
     */
    @Bean
    public RetryTopicConfiguration auctionEndedRetryTopicConfig(KafkaTemplate<Object, Object> kafkaTemplate) {
        return RetryTopicConfigurationBuilder
                .newInstance()
                .maxAttempts(3)
                .fixedBackOff(2000L)
                .sameIntervalTopicReuseStrategy(SameIntervalTopicReuseStrategy.SINGLE_TOPIC)
                .retryTopicSuffix("-retry")
                .dltSuffix("-dlt")
                .includeTopic(TOPIC_AUCTION_ENDED)
                .dltProcessingFailureStrategy(DltStrategy.FAIL_ON_ERROR)
                .create(kafkaTemplate);
    }
}
