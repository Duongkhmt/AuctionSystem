package com.duong.auction.system.service.stream;

import com.duong.auction.system.config.RedisStreamConfig;
import com.duong.auction.system.dto.event.BidEventMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.stream.ObjectRecord;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.connection.stream.StreamRecords;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Chịu trách nhiệm đẩy tin nhắn sự kiện đặt giá thắng vào Redis Stream Queue
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AsyncBidStreamPublisher {

    private final StringRedisTemplate redisTemplate;

    public void publishBidEvent(BidEventMessage event) {
        try {
            ObjectRecord<String, BidEventMessage> record = StreamRecords.newRecord()
                    .in(RedisStreamConfig.STREAM_KEY)
                    .ofObject(event);

            RecordId recordId = redisTemplate.opsForStream().add(record);
            log.info("Đã đẩy BidEventMessage thành công vào Redis Stream! EventId: {}, RecordId: {}", 
                    event.getEventId(), recordId);
        } catch (Exception e) {
            log.error("Lỗi khi đẩy tin nhắn vào Redis Stream cho eventId: {}", event.getEventId(), e);
        }
    }
}
