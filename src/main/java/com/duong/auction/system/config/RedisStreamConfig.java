package com.duong.auction.system.config;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.RedisSystemException;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * Cấu hình tự động khởi tạo Redis Stream Consumer Group (XGROUP CREATE) khi bật app
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class RedisStreamConfig {

    private final StringRedisTemplate redisTemplate;
    public static final String STREAM_KEY = "auction:bid:events";
    public static final String CONSUMER_GROUP = "auction-worker-group";

    @PostConstruct
    public void initStreamConsumerGroup() {
        try {
            // Thử tạo Consumer Group từ offset "0-0"
            redisTemplate.opsForStream().createGroup(
                    STREAM_KEY,
                    ReadOffset.from("0-0"),
                    CONSUMER_GROUP
            );
            log.info("Khởi tạo thành công Consumer Group '{}' cho Stream '{}'", CONSUMER_GROUP, STREAM_KEY);
        } catch (RedisSystemException e) {
            // Bắt chuẩn lỗi BUSYGROUP khi Consumer Group đã tồn tại trước đó -> Bỏ qua an toàn!
            if (e.getMessage() != null && e.getMessage().contains("BUSYGROUP")) {
                log.info("Consumer Group '{}' đã tồn tại sẵn trên Redis Stream.", CONSUMER_GROUP);
            } else {
                log.warn("Cảnh báo khi khởi tạo Consumer Group: {}", e.getMessage());
            }
        } catch (Exception e) {
            log.warn("Stream key chưa tồn tại, Consumer Group sẽ tự động được khởi tạo khi có lượt bid đầu tiên: {}", e.getMessage());
        }
    }
}
