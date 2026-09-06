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
import org.springframework.kafka.support.serializer.JacksonJsonDeserializer;
import org.springframework.kafka.support.serializer.JacksonJsonSerializer;

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

    // Topic chính tiếp nhận sự kiện kết thúc đấu giá
    public static final String TOPIC_AUCTION_ENDED = "auction.events.ended";

    /**
     * 1. TỰ ĐỘNG KHỞI TẠO TOPIC CHÍNH TRÊN KAFKA BROKER
     *
     * Khởi tạo Topic "auction.events.ended" với 3 Partitions để cho phép 3 Worker Consumer
     * xử lý song song, giúp tối ưu hiệu năng khi có hàng ngàn phiên đấu giá cùng hết hạn.
     */
    @Bean
    public NewTopic auctionEndedTopic() {
        return TopicBuilder.name(TOPIC_AUCTION_ENDED)
                .partitions(3)
                .build();
    }
    /**
     * 2. CẤU HÌNH PRODUCER FACTORY (BÊN GỬI TIN NHẮN)
     *
     * Định nghĩa cách mã hóa dữ liệu trước khi bắn qua mạng:
     * - Key: Chuỗi String (AuctionId)
     * - Value: Đóng gói đối tượng Java (AuctionEndedEvent) thành chuỗi JSON String
     */
    @Bean
    public ProducerFactory<Object, Object> producerFactory() {
        Map<String, Object> configProps = new HashMap<>();
        configProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        configProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        configProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JacksonJsonSerializer.class);
        return new DefaultKafkaProducerFactory<>(configProps);
    }

    /**
     * 3. KHỞI TẠO KAFKATEMPLATE
     * Công cụ làm việc chính của Spring Kafka cung cấp hàm .send() để Producer phát thông điệp lên Broker.
     */
    @Bean
    public KafkaTemplate<Object, Object> kafkaTemplate() {
        return new KafkaTemplate<>(producerFactory());
    }

    /**
     * 4. CẤU HÌNH CONSUMER FACTORY (BÊN NHẬN TIN NHẮN)
     *
     * Định nghĩa cách giải mã dữ liệu nhận về từ Kafka:
     * - GROUP_ID: "auction-service-group" định danh nhóm người đọc.
     * - Value Deserializer: Tự động chuyển chuỗi JSON String trở lại thành đối tượng Java AuctionEndedEvent.
     */
    @Bean
    public ConsumerFactory<Object, Object> consumerFactory() {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "auction-service-group");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, JacksonJsonDeserializer.class);
        props.put(JacksonJsonDeserializer.TRUSTED_PACKAGES, "*");
        return new DefaultKafkaConsumerFactory<>(props);
    }

    /**
     * 5. KHỞI TẠO LISTENER CONTAINER FACTORY
     *
     * Cung cấp bộ Container quản lý vòng đời cho annotation @KafkaListener và xử lý Retry Topic ngầm.
     */
    @Bean
    public ConcurrentKafkaListenerContainerFactory<Object, Object> kafkaListenerContainerFactory() {
        ConcurrentKafkaListenerContainerFactory<Object, Object> factory = new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory());
        return factory;
    }

    /**
     * 6.  CẤU HÌNH CHI TIẾT NON-BLOCKING RETRY TOPIC & DEAD LETTER TOPIC (DLT)
     *
     * LUỒNG DIỄN BIẾN THỜI GIAN KHI 1 TIN NHẮN BỊ LỖI (Ví dụ: DB Timeout):
     * - [Lần 1]: Consumer đọc từ "auction.events.ended" -> Bị nổ Exception -> Không chặn luồng chính,
     *            đẩy tin nhắn sang Topic trung gian "auction.events.ended-retry".
     * - [Chờ 2 giây]: Tính theo fixedBackOff(2000L).
     * - [Lần 2]: Consumer đọc lại tin nhắn từ "-retry" -> Vẫn bị lỗi -> Đẩy lại vào chính Topic "-retry".
     * - [Chờ 2 giây].
     * - [Lần 3]: Consumer đọc lại lần cuối -> Vẫn lỗi -> Hết 3 lượt thử (maxAttempts(3)).
     * - [Kết thúc]: Tin nhắn bị coi là "Poison Pill" -> Đẩy vào DLT "auction.events.ended-dlt".
     *               Hàm @DltHandler được kích hoạt để log cảnh báo cho Admin kiểm tra thủ công.
     */
    @Bean
    public RetryTopicConfiguration auctionEndedRetryTopicConfig(KafkaTemplate<Object, Object> kafkaTemplate) {
        return RetryTopicConfigurationBuilder
                .newInstance()
                // 1. Tổng số lần được thử xử lý tin nhắn (bao gồm lần đầu tiên). Quá 3 lần coi là thất bại vĩnh viễn.
                .maxAttempts(3)
                // 2. Khoảng thời gian chờ cố định giữa các lần thử lại = 2000ms (2 giây).
                .fixedBackOff(2000L)
                // 3. Tối ưu Topic: Vì dùng thời gian chờ cố định (2s), nên cả 3 lần thử đều dùng chung 1 Topic "-retry",
                //    tránh việc Spring tự tạo ra nhiều Topic riêng lẻ (như -retry-0, -retry-1) gây lãng phí tài nguyên.
                .sameIntervalTopicReuseStrategy(SameIntervalTopicReuseStrategy.SINGLE_TOPIC)
                // 4. Quy ước hậu tố đặt tên Topic tự động: "auction.events.ended-retry" và "auction.events.ended-dlt".
                .retryTopicSuffix("-retry")
                .dltSuffix("-dlt")
                // 5. Chỉ định cấu hình Retry/DLT này chỉ áp dụng riêng cho Topic "auction.events.ended".
                .includeTopic(TOPIC_AUCTION_ENDED)
                // 6. Xử lý lỗi nghiêm trọng: Nếu chính hàm @DltHandler cũng bị lỗi khi chạy thì văng lỗi rõ ràng,
                //    không được âm thầm nuốt lỗi để tránh làm biến mất tin nhắn mà không ai hay biết.
                .dltProcessingFailureStrategy(DltStrategy.FAIL_ON_ERROR)
                .create(kafkaTemplate);
    }
}
