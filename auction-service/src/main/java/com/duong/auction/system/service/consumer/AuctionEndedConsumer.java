package com.duong.auction.system.service.consumer;

import com.duong.auction.system.config.KafkaConfig;
import com.duong.auction.system.dto.event.AuctionEndedEvent;
import com.duong.auction.system.entity.Auction;
import com.duong.auction.system.entity.Order;
import com.duong.auction.system.entity.User;
import com.duong.auction.system.mapper.OrderMapper;
import com.duong.auction.system.repository.AuctionRepository;
import com.duong.auction.system.repository.OrderRepository;
import com.duong.auction.system.repository.UserRepository;
import com.duong.auction.system.service.EmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.kafka.annotation.DltHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;

/**
 * 📥 KAFKA CONSUMER & DLT HANDLER: Lắng nghe sự kiện AUCTION_ENDED, xử lý tạo Đơn hàng ngầm và DLT.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AuctionEndedConsumer {

    private final OrderRepository orderRepository;
    private final AuctionRepository auctionRepository;
    private final UserRepository userRepository;
    private final OrderMapper orderMapper;
    private final RedisTemplate<String, String> redisTemplate;
    private final Clock clock;
    private final EmailService emailService;

    private static final String IDEMPOTENT_KEY_PREFIX = "kafka:processed_event:";

    /**
     * 1. Consumer chính tiêu thụ sự kiện AUCTION_ENDED từ Topic "auction.events.ended"
     */
    @KafkaListener(topics = KafkaConfig.TOPIC_AUCTION_ENDED)
    @Transactional
    public void listenAuctionEnded(AuctionEndedEvent event) {
        log.info("[Kafka Consumer SUCCESS] Nhận sự kiện AUCTION_ENDED cho phiên auctionId: {}, winnerId: {}, finalPrice: {}",
                event.getAuctionId(),
                event.getWinnerId(),
                event.getFinalPrice());

        String idempotencyKey = IDEMPOTENT_KEY_PREFIX + event.getEventId();

        //  BƯỚC 1: READ-ONLY CHECK IDEMPOTENCY
        if (Boolean.TRUE.equals(redisTemplate.hasKey(idempotencyKey))) {
            log.warn("[Kafka Consumer IDEMPOTENT] Sự kiện eventId: {} đã xử lý THÀNH CÔNG trước đó. Bỏ qua ghi trùng!",
                    event.getEventId());
            return;
        }

        //  BƯỚC 2: THỰC THI NGHIỆP VỤ TẠO ĐƠN HÀNG VÀ GỬI EMAIL NGẦM
        if (event.getWinnerId() != null && !orderRepository.existsByAuction_Id(event.getAuctionId())) {
            Auction auction = auctionRepository.findById(event.getAuctionId()).orElse(null);
            User winner = userRepository.findById(event.getWinnerId()).orElse(null);

            if (auction != null && winner != null) {
                Order order = orderMapper.toEntity(auction, winner, event.getFinalPrice());
                order.setPaymentDeadline(LocalDateTime.now(clock).plusHours(48));
                orderRepository.save(order);

                log.info(" [Kafka Consumer SUCCESS] Đã tạo thành công Đơn hàng ngầm OrderId: {} (paymentDeadline: 48h) cho winnerId: {}",
                        order.getId(), winner.getId());
                // 🟢 GỌI GỬI EMAIL THÔNG BÁO THẮNG THẦU NGẦM QUA KAFKA
                emailService.sendAuctionWinnerEmail(
                        winner.getEmail(),
                        winner.getUsername() != null ? winner.getUsername() : winner.getEmail(),
                        auction.getProduct().getTitle(),
                        event.getFinalPrice(),
                        order.getId(),
                        order.getPaymentDeadline()
                );
            }
        }

        //  BƯỚC 3: ĐÁNH DẤU THÀNH CÔNG VÀO REDIS CHỈ KHI NGHIỆP VỤ TẠO ĐƠN HOÀN THÀNH HOÀN HẢO!
        redisTemplate.opsForValue().set(idempotencyKey, "PROCESSED", Duration.ofHours(24));
    }

    /**
     * 2. DLT Handler (Nghĩa Địa Tin Nhắn DLT): Tiếp nhận các tin nhắn bị hỏng (Poison Pill) sau 3 lần thử lại thất bại
     */
    @DltHandler
    public void handleDltMessage(AuctionEndedEvent event) {
        log.error(" [KAFKA DLT ALERT] Tin nhắn bị hỏng (Poison Pill) đã bị đẩy vào DLT! " +
                        "EventId: {}, AuctionId: {}, WinnerId: {}, Reason: {}. Cần Admin kiểm tra thủ công!",
                event.getEventId(),
                event.getAuctionId(),
                event.getWinnerId(),
                event.getEndedReason());
    }
}
