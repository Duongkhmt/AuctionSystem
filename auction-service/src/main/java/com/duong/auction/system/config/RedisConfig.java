package com.duong.auction.system.config;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.jsontype.impl.LaissezFaireSubTypeValidator;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
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
        // [CẤU HÌNH JACKSON OBJECTMAPPER CHO REDIS SERIALIZATION (HỖ TRỢ LOCALDATETIME)]
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        objectMapper.activateDefaultTyping(
                LaissezFaireSubTypeValidator.instance,
                ObjectMapper.DefaultTyping.NON_FINAL,
                JsonTypeInfo.As.PROPERTY
        );

        GenericJackson2JsonRedisSerializer jsonSerializer = new GenericJackson2JsonRedisSerializer(objectMapper);

        // -----------------------------------------------------------------------------------
        // KHỐI 1: THIẾT LẬP CẤU HÌNH MẶC ĐỊNH CHO TẤT CẢ CÁC CACHE
        // -----------------------------------------------------------------------------------
        RedisCacheConfiguration defaultConfig = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofMinutes(5)) // [4] Mặc định tự xóa sau 5 phút
                .serializeKeysWith(
                        RedisSerializationContext.SerializationPair.fromSerializer(RedisSerializer.string())
                ) // [5] Ép tên Key lưu dưới dạng chữ Plain Text
                .serializeValuesWith(
                        RedisSerializationContext.SerializationPair.fromSerializer(jsonSerializer)
                ); // [6] Ép dữ liệu Value lưu dưới dạng văn bản JSON có Jackson JavaTimeModule

        // -----------------------------------------------------------------------------------
        // KHỐI 2: THIẾT LẬP THỜI GIAN SỐNG (TTL) RIÊNG CHO TỪNG LOẠI DỮ LIỆU
        // -----------------------------------------------------------------------------------
        Map<String, RedisCacheConfiguration> cacheConfigs = new HashMap<>();
        cacheConfigs.put("bid_history", defaultConfig.entryTtl(Duration.ofSeconds(30)));
        cacheConfigs.put("categories", defaultConfig.entryTtl(Duration.ofHours(24)));

        // -----------------------------------------------------------------------------------
        // KHỐI 3: ĐÓNG GÓI VÀ BÀN GIAO CHO SPRING BOOT
        // -----------------------------------------------------------------------------------
        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(defaultConfig)
                .withInitialCacheConfigurations(cacheConfigs)
                .build();
    }

    //Bean RedissonClient kết nối Redis phục vụ Khóa Phân Tán (Distributed Lock)
    @Bean
    public RedissonClient redissonClient() {
        Config config = new Config();
        config.useSingleServer().setAddress("redis://localhost:6379");
        return Redisson.create(config);
    }
}

