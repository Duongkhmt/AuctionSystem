package com.duong.auction.system.service.producer;

import com.duong.auction.system.config.KafkaConfig;
import com.duong.auction.system.dto.event.AuctionEndedEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.math.BigDecimal;
import java.util.concurrent.CompletableFuture;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;

@ExtendWith(MockitoExtension.class)
@DisplayName("Unit Test Cho Class AuctionKafkaProducer")
class AuctionKafkaProducerTest {

    @Mock
    private KafkaTemplate<String, Object> kafkaTemplate;

    @InjectMocks
    private AuctionKafkaProducer auctionKafkaProducer;

    @Test
    @DisplayName("Gửi sự kiện AUCTION_ENDED lên Kafka Topic thành công với Partition Key = auctionId")
    void sendAuctionEndedEvent_Success_ShouldSendToTopic() {
        // 1. GIVEN: Tạo sự kiện kết thúc phiên đấu giá mẫu
        AuctionEndedEvent event = AuctionEndedEvent.builder()
                .eventId("uuid-999")
                .auctionId(10L)
                .winnerId(3L)
                .finalPrice(BigDecimal.valueOf(15000000))
                .endedReason("EXPIRED")
                .build();

        CompletableFuture<SendResult<String, Object>> future = new CompletableFuture<>();
        future.complete(mock(SendResult.class));

        given(kafkaTemplate.send(eq(KafkaConfig.TOPIC_AUCTION_ENDED), eq("10"), eq(event)))
                .willReturn(future);

        // 2. WHEN: Gọi hàm gửi sự kiện bất đồng bộ
        auctionKafkaProducer.sendAuctionEndedEvent(event);

        // 3. THEN: Xác minh KafkaTemplate đã phát tin nhắn đúng Topic và Partition Key
        then(kafkaTemplate).should(times(1)).send(eq(KafkaConfig.TOPIC_AUCTION_ENDED), eq("10"), eq(event));
    }
}
