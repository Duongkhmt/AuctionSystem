package com.duong.auction.system.config;

import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.retrytopic.DltStrategy;
import org.springframework.kafka.retrytopic.RetryTopicConfiguration;
import org.springframework.kafka.retrytopic.RetryTopicConfigurationBuilder;
import org.springframework.kafka.retrytopic.SameIntervalTopicReuseStrategy;

/**
 * ⚙️ CẤU HÌNH HẠ TẦNG KAFKA & NON-BLOCKING RETRY TOPIC + DEAD LETTER TOPIC (DLT) 3 TẦNG
 */
@Slf4j
@Configuration
public class KafkaConfig {

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
                .partitions(3)// Local development: 1 Kafka brok
                .build();
    }

    /**
     * 🔥 CẤU HÌNH NON-BLOCKING RETRY TOPIC & DLT DUY NHẤT (
     */
    @Bean
    @SuppressWarnings("SpringJavaInjectionPointsAutowiringInspection")
    public RetryTopicConfiguration auctionEndedRetryTopicConfig(KafkaTemplate<String, Object> kafkaTemplate) {
        return RetryTopicConfigurationBuilder
                .newInstance()
                .maxAttempts(3)                                                            // Tổng cộng tối đa 3 lần xử lý
                .fixedBackOff(2000L)                                                       // Hẹn giờ 2 giây giữa các lần thử
                .sameIntervalTopicReuseStrategy(SameIntervalTopicReuseStrategy.SINGLE_TOPIC) // Khóa đúng 1 Topic Retry duy nhất (-retry)!
                .retryTopicSuffix("-retry")                                                 // Hậu tố Topic Retry: auction.events.ended-retry
                .dltSuffix("-dlt")                                                         // Hậu tố Topic DLT: auction.events.ended-dlt
                .includeTopic(TOPIC_AUCTION_ENDED)
                .dltProcessingFailureStrategy(DltStrategy.FAIL_ON_ERROR)
                .create(kafkaTemplate);
    }
}
