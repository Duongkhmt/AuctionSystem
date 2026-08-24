package com.duong.auction.system.service.worker;

import com.duong.auction.system.config.RedisStreamConfig;
import com.duong.auction.system.dto.event.BidEventMessage;
import com.duong.auction.system.entity.Auction;
import com.duong.auction.system.entity.Bid;
import com.duong.auction.system.entity.User;
import com.duong.auction.system.repository.AuctionRepository;
import com.duong.auction.system.repository.BidRepository;
import com.duong.auction.system.repository.UserRepository;
import com.duong.auction.system.service.stream.AsyncBidStreamPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.stream.ObjectRecord;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Worker ngầm phía sau (Consumer Group) rút tin nhắn từ Redis Stream và ghi PostgreSQL ngầm
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AsyncBidWriteWorker {

    private final StringRedisTemplate redisTemplate;
    private final BidRepository bidRepository;
    private final AuctionRepository auctionRepository;
    private final UserRepository userRepository;

    /**
     * Quét Redis Stream ngầm mỗi 500ms để rút tin nhắn gom Batch lưu DB
     */
    @Scheduled(fixedDelay = 500)
    @Transactional
    public void processPendingBidsFromStream() {
        try {
            // 1. Đọc tin nhắn từ Stream bằng Consumer Group
            List<ObjectRecord<String, BidEventMessage>> records = redisTemplate.opsForStream()
                    .read(BidEventMessage.class, 
                            org.springframework.data.redis.connection.stream.Consumer.from(RedisStreamConfig.CONSUMER_GROUP, "worker-1"),
                            org.springframework.data.redis.connection.stream.StreamOffset.create(
                                    RedisStreamConfig.STREAM_KEY, 
                                    org.springframework.data.redis.connection.stream.ReadOffset.lastConsumed()));

            if (records == null || records.isEmpty()) {
                return;
            }

            for (ObjectRecord<String, BidEventMessage> record : records) {
                BidEventMessage event = record.getValue();

                // 2. KHẮC PHỤC LỖI IDEMPOTENCY: Kiểm tra eventId đã ghi DB chưa trước khi save!
                if (event.getEventId() != null && bidRepository.existsByEventId(event.getEventId())) {
                    log.warn("Bỏ qua tin nhắn trùng lặp do Worker retry (EventId: {})", event.getEventId());
                    // Gửi lệnh ACK xác nhận xóa tin nhắn trùng khỏi Stream
                    redisTemplate.opsForStream().acknowledge(RedisStreamConfig.STREAM_KEY, RedisStreamConfig.CONSUMER_GROUP, record.getId());
                    continue;
                }

                // 3. Nạp Entity và thực hiện lưu DB
                Auction auction = auctionRepository.findById(event.getAuctionId()).orElse(null);
                User bidder = userRepository.findById(event.getBidderId()).orElse(null);

                if (auction != null && bidder != null) {
                    Bid bid = new Bid();
                    bid.setEventId(event.getEventId()); // Lưu eventId chống ghi trùng DB
                    bid.setAuction(auction);
                    bid.setBidder(bidder);
                    bid.setBidAmount(event.getBidAmount());
                    bid.setAutoBid(false);

                    bidRepository.save(bid);

                    auction.setCurrentPrice(event.getBidAmount());
                    auctionRepository.save(auction);
                }

                // 4. Gửi lệnh XACK xác nhận xóa tin nhắn khỏi Stream
                redisTemplate.opsForStream().acknowledge(RedisStreamConfig.STREAM_KEY, RedisStreamConfig.CONSUMER_GROUP, record.getId());
            }
        } catch (Exception e) {
            log.error("Lỗi Worker ngầm khi ghi DB từ Redis Stream", e);
        }
    }
}
