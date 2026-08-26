package com.duong.auction.system.service.producer;

import com.duong.auction.system.config.KafkaConfig;
import com.duong.auction.system.dto.event.AuctionEndedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

/**
 * 🚀 KAFKA PRODUCER: Chịu trách nhiệm phát sự kiện AUCTION_ENDED lên Kafka Broker bất đồng bộ.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AuctionKafkaProducer {

    private final KafkaTemplate<Object, Object> kafkaTemplate;

    /**
     * Bắn thông điệp phiên đấu giá kết thúc lên Kafka Topic "auction.events.ended"
     * Bổ sung CompletableFuture callback xác nhận kết quả gửi bất đồng bộ!
     */
    public void sendAuctionEndedEvent(AuctionEndedEvent event) {
        log.info("🚀 [Kafka Producer] Đang gửi sự kiện AUCTION_ENDED lên Kafka. AuctionId: {}, WinnerId: {}",
                event.getAuctionId(), event.getWinnerId());

        CompletableFuture<SendResult<Object, Object>> future = kafkaTemplate.send(
                KafkaConfig.TOPIC_AUCTION_ENDED,
                String.valueOf(event.getAuctionId()), // Partition Key
                event
        );

        // Bắt callback xác nhận kết quả gửi ngầm từ Kafka Broker
        future.whenComplete((result, ex) -> {
            if (ex == null) {
                log.info("✅ [Kafka Producer SUCCESS] Đã gửi thành công AuctionId: {} vào Topic: {}, Partition: {}, Offset: {}",
                        event.getAuctionId(),
                        result.getRecordMetadata().topic(),
                        result.getRecordMetadata().partition(),
                        result.getRecordMetadata().offset());
            } else {
                log.error("❌ [Kafka Producer ERROR] Gửi thất bại sự kiện AuctionId: {} lên Kafka! Nguyên nhân: {}",
                        event.getAuctionId(), ex.getMessage(), ex);
            }
        });
    }
}
