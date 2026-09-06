package com.duong.auction.system.service;

import com.duong.auction.system.entity.Auction;
import com.duong.auction.system.entity.Bid;
import com.duong.auction.system.entity.Order;
import com.duong.auction.system.entity.User;
import com.duong.auction.system.enums.AuctionStatus;
import com.duong.auction.system.enums.AuctionType;
import com.duong.auction.system.enums.OrderStatus;
import com.duong.auction.system.enums.ProductStatus;
import com.duong.auction.system.repository.AuctionRepository;
import com.duong.auction.system.repository.BidRepository;
import com.duong.auction.system.repository.OrderRepository;
import com.duong.auction.system.repository.UserRepository;
import com.duong.auction.system.service.helper.AuctionEndedSettlementHelper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Robot điều hành thời gian hệ thống chạy ngầm định kỳ 10 giây/lần.
 * Tự động kích hoạt phiên, chốt Winner, bắn sự kiện Kafka AUCTION_ENDED, hủy đơn bùng quá 48h và phạt Gậy Vi Phạm.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AuctionScheduler {

    private final AuctionRepository auctionRepository;
    private final BidRepository bidRepository;
    private final OrderRepository orderRepository;
    private final UserRepository userRepository;
    private final AuctionEndedSettlementHelper settlementHelper;
    private final Clock clock;

    // Robot điều phối chạy ngầm định kỳ mỗi 10 giây (fixedRate = 10000ms)
    @Scheduled(fixedRate = 10000)
    @CacheEvict(value = "auctions", allEntries = true)
    @Transactional
    public void processAuctionStatusTransitions() {
        LocalDateTime now = LocalDateTime.now(clock);

        // 1. Tự động kích hoạt các phiên hẹn giờ (SCHEDULED -> RUNNING)
        auctionRepository.autoStartAuctions(
                now,
                AuctionStatus.SCHEDULED,
                AuctionStatus.RUNNING,
                ProductStatus.APPROVED
        );

        // 2. Tự động đánh nhãn hết hạn bài Mua Ngay 30 ngày (RUNNING -> EXPIRED)
        auctionRepository.autoExpireBuyNowAuctions(
                now,
                AuctionStatus.RUNNING,
                AuctionStatus.EXPIRED,
                AuctionType.BUY_NOW
        );

        // 3. Tự động chốt Winner và bắn sự kiện Kafka AUCTION_ENDED bất đồng bộ
        processEndedAuctions(now);

        // 4. Tự động quét hủy các đơn UNPAID quá 48h & Phạt Gậy Vi Phạm / Khóa 90 ngày nếu bùng 3 lần
        processExpiredUnpaidOrders(now);
    }

    // Xử lý chốt Winner và bắn sự kiện Kafka cho các phiên đến giờ kết thúc
    private void processEndedAuctions(LocalDateTime now) {
        List<Auction> endedAuctions = auctionRepository
                .findByStatusAndAuctionTypeNotAndEndTimeLessThanEqual(AuctionStatus.RUNNING, AuctionType.BUY_NOW, now);
        if (endedAuctions.isEmpty()) return;

        List<Long> auctionIds = endedAuctions.stream().map(Auction::getId).toList();
        Map<Long, Bid> highestBids = bidRepository.findHighestBidsByAuctionIdIn(auctionIds)
                .stream().collect(Collectors.toMap(b -> b.getAuction().getId(), b -> b, (b1, b2) -> b1));

        for (Auction auction : endedAuctions) {
            Bid highestBid = highestBids.get(auction.getId());
            try {
                // 🟢 Gọi qua Injected Spring Bean Proxy -> Kích hoạt @Transactional(REQUIRES_NEW) 100%!
                settlementHelper.processSingleAuctionEnded(auction, highestBid, now);
            } catch (Exception e) {
                log.error(" Lỗi xử lý chốt thầu độc lập cho AuctionId: {}. Bỏ qua phiên này!", auction.getId(), e);
            }
        }
    }

    // Tự động quét và xử lý các đơn hàng bùng tiền quá 48h
    private void processExpiredUnpaidOrders(LocalDateTime now) {
        List<Order> expiredOrders = orderRepository
                .findByStatusAndPaymentDeadlineLessThanEqual(OrderStatus.UNPAID, now);

        for (Order order : expiredOrders) {
            order.setStatus(OrderStatus.CANCELLED);
            orderRepository.save(order);

            User buyer = order.getBuyer();
            if (buyer != null) {
                int strikes = (buyer.getUnpaidStrikeCount() != null ? buyer.getUnpaidStrikeCount() : 0) + 1;
                buyer.setUnpaidStrikeCount(strikes);

                if (strikes >= 3) {
                    buyer.setBannedUntil(now.plusDays(90));
                }
                userRepository.save(buyer);
            }
        }
    }
}
