package com.duong.auction.system.service.consumer;

import com.duong.auction.system.dto.event.AuctionEndedEvent;
import com.duong.auction.system.entity.Auction;
import com.duong.auction.system.entity.Order;
import com.duong.auction.system.entity.User;
import com.duong.auction.system.mapper.OrderMapper;
import com.duong.auction.system.repository.AuctionRepository;
import com.duong.auction.system.repository.OrderRepository;
import com.duong.auction.system.repository.UserRepository;
import com.duong.auction.system.service.EmailService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.RedisTemplate;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Unit Test Cho Class AuctionEndedConsumer (Kafka Listener)")
class AuctionEndedConsumerTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private AuctionRepository auctionRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private OrderMapper orderMapper;

    @Mock
    private RedisTemplate<String, String> redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Mock
    private Clock clock;

    @Mock
    private EmailService emailService;

    @InjectMocks
    private AuctionEndedConsumer auctionEndedConsumer;

    private AuctionEndedEvent sampleEvent;
    private Auction sampleAuction;
    private User sampleWinner;

    @BeforeEach
    void setUp() {
        lenient().when(clock.getZone()).thenReturn(ZoneId.systemDefault());
        lenient().when(clock.instant()).thenReturn(Instant.now());
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        sampleEvent = AuctionEndedEvent.builder()
                .eventId("test-event-uuid-1234")
                .auctionId(10L)
                .winnerId(5L)
                .finalPrice(BigDecimal.valueOf(15000000))
                .endedReason("EXPIRED")
                .build();

        com.duong.auction.system.entity.Product sampleProduct = new com.duong.auction.system.entity.Product();
        sampleProduct.setTitle("iPhone 15 Pro Max");

        sampleAuction = new Auction();
        sampleAuction.setId(10L);
        sampleAuction.setProduct(sampleProduct);

        sampleWinner = new User();
        sampleWinner.setId(5L);
        sampleWinner.setEmail("winner@example.com");
        sampleWinner.setUsername("nguyenvana");
    }

    @Test
    @DisplayName("Tiêu thụ sự kiện Kafka thành công - Tạo Đơn hàng 48h và lưu Idempotency Key vào Redis")
    void listenAuctionEnded_Success_ShouldCreateOrderAndSaveIdempotencyKey() {
        // 1. GIVEN: Chưa từng xử lý eventId này (redis key false)
        String idempotencyKey = "kafka:processed_event:test-event-uuid-1234";
        given(redisTemplate.hasKey(idempotencyKey)).willReturn(false);

        given(orderRepository.existsByAuction_Id(10L)).willReturn(false);
        given(auctionRepository.findById(10L)).willReturn(Optional.of(sampleAuction));
        given(userRepository.findById(5L)).willReturn(Optional.of(sampleWinner));

        Order mockOrder = new Order();
        mockOrder.setId(88L);
        given(orderMapper.toEntity(sampleAuction, sampleWinner, sampleEvent.getFinalPrice())).willReturn(mockOrder);

        // 2. WHEN: Consumer nhận tin nhắn từ Kafka
        auctionEndedConsumer.listenAuctionEnded(sampleEvent);

        // 3. THEN: Kiểm tra đã lưu Đơn hàng vào DB và đánh dấu PROCESSED vào Redis
        then(orderRepository).should(times(1)).save(mockOrder);
        then(valueOperations).should(times(1)).set(eq(idempotencyKey), eq("PROCESSED"), any(java.time.Duration.class));
    }

    @Test
    @DisplayName("Bỏ qua xử lý khi sự kiện bị trùng lặp - Redis Idempotency Check")
    void listenAuctionEnded_DuplicateEvent_ShouldSkipProcessing() {
        // 1. GIVEN: Event đã được xử lý trước đó (redis key true)
        String idempotencyKey = "kafka:processed_event:test-event-uuid-1234";
        given(redisTemplate.hasKey(idempotencyKey)).willReturn(true);

        // 2. WHEN: Consumer nhận lại tin nhắn từ Kafka
        auctionEndedConsumer.listenAuctionEnded(sampleEvent);

        // 3. THEN: Khẳng định dừng lại ngay, không bao giờ truy vấn DB hay tạo Đơn hàng
        then(orderRepository).should(never()).existsByAuction_Id(any());
        then(orderRepository).should(never()).save(any());
    }

    @Test
    @DisplayName("Bắt tin nhắn lỗi Poison Pill tại DLT Handler")
    void handleDltMessage_ShouldLogAlertWithoutThrowingException() {
        // 1. WHEN & THEN: Gọi DLT Handler xử lý tin nhắn hỏng
        auctionEndedConsumer.handleDltMessage(sampleEvent);
        // Kiểm tra chạy thành công không ném ra ngoại lệ
    }
}
