package com.duong.auction.system.config;

// Các thư viện Spring Boot cung cấp
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.RedisSerializer;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

@Configuration // [1] Báo cho Spring biết: "Đây là File Cấu Hình Hệ Thống, hãy chạy nó ngay khi bật app"
@EnableCaching // [2] CÔNG TẮC TỔNG: Bật tính năng Caching (Giúp @Cacheable và @CacheEvict có hiệu lực)
public class RedisConfig {

    @Bean // [3] Đăng ký hàm này thành 1 Bean để Spring tự động quản lý trong bộ nhớ
    public RedisCacheManager cacheManager(RedisConnectionFactory connectionFactory) {
        // 'connectionFactory': Spring tự đọc IP/Port (localhost:6379) từ application.properties
        // để mở đường ống kết nối xuống Redis Server.

        // -----------------------------------------------------------------------------------
        // KHỐI 1: THIẾT LẬP CẤU HÌNH MẶC ĐỊNH CHO TẤT CẢ CÁC CACHE
        // -----------------------------------------------------------------------------------
        RedisCacheConfiguration defaultConfig = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofMinutes(5)) // [4] Tờ giấy nháp nào không dặn gì thì mặc định tự xóa sau 5 phút
                .serializeKeysWith(
                        RedisSerializationContext.SerializationPair.fromSerializer(RedisSerializer.string())
                ) // [5] Ép tên Key lưu dưới dạng chữ Plain Text sạch đẹp (ví dụ: "bid_history::101")
                .serializeValuesWith(
                        RedisSerializationContext.SerializationPair.fromSerializer(RedisSerializer.json())
                ); // [6] Ép dữ liệu Value lưu dưới dạng văn bản JSON chuẩn

        // -----------------------------------------------------------------------------------
        // KHỐI 2: THIẾT LẬP THỜI GIAN SỐNG (TTL) RIÊNG CHO TỪNG LOẠI DỮ LIỆU
        // -----------------------------------------------------------------------------------
        Map<String, RedisCacheConfiguration> cacheConfigs = new HashMap<>();

        // [7] Riêng vùng "bid_history" (Lịch sử đặt giá): Ép thời gian sống ngắn lại (chỉ 30 giây)
        // Vì lịch sử đấu giá thay đổi rất nhanh, không nên để quá lâu 5 phút!
        cacheConfigs.put("bid_history", defaultConfig.entryTtl(Duration.ofSeconds(30)));

        // -----------------------------------------------------------------------------------
        // KHỐI 3: ĐÓNG GÓI VÀ BÀN GIAO CHO SPRING BOOT
        // -----------------------------------------------------------------------------------
        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(defaultConfig) // Áp dụng cấu hình mặc định
                .withInitialCacheConfigurations(cacheConfigs) // Áp dụng các cấu hình riêng (như 30s của bid_history)
                .build(); // Hoàn tất khởi tạo Trưởng phòng Quản lý Cache!
    }
}
