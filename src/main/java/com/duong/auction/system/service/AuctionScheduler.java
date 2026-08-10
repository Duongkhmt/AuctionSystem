package com.duong.auction.system.service;

import com.duong.auction.system.entity.Auction;
import com.duong.auction.system.entity.Bid;
import com.duong.auction.system.entity.Order;
import com.duong.auction.system.entity.User;
import com.duong.auction.system.enums.AuctionStatus;
import com.duong.auction.system.enums.AuctionType;
import com.duong.auction.system.enums.OrderStatus;
import com.duong.auction.system.enums.ProductStatus;
import com.duong.auction.system.mapper.OrderMapper;
import com.duong.auction.system.repository.AuctionRepository;
import com.duong.auction.system.repository.BidRepository;
import com.duong.auction.system.repository.OrderRepository;
import com.duong.auction.system.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Robot điều hành thời gian hệ thống chạy ngầm định kỳ 10 giây/lần.
 * Tự động kích hoạt phiên, chốt Winner, sinh Đơn hàng, hủy đơn bùng quá 48h và phạt Gậy Vi Phạm.
 */
@Component
@RequiredArgsConstructor
public class AuctionScheduler {

    private final AuctionRepository auctionRepository;
    private final BidRepository bidRepository;
    private final OrderRepository orderRepository;
    private final UserRepository userRepository;
    private final OrderMapper orderMapper;

    // Robot điều phối chạy ngầm định kỳ mỗi 10 giây (fixedRate = 10000ms)
    @Scheduled(fixedRate = 10000)
    @Transactional
    public void processAuctionStatusTransitions() {
        LocalDateTime now = LocalDateTime.now();

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

        // 3. Tự động chốt Winner và sinh Đơn hàng mới (UNPAID + Hạn 48h) khi hết giờ
        processEndedAuctions(now);

        // 4. Backfill Self-Healing: Bổ sung Đơn hàng cho các phiên ENDED có Winner bị thiếu đơn
        backfillMissingOrders(now);

        // 5. Tự động quét hủy các đơn UNPAID quá 48h & Phạt Gậy Vi Phạm / Khóa 90 ngày nếu bùng 3 lần
        processExpiredUnpaidOrders(now);
    }

    // =========================================================================
    // PRIVATE HELPER METHODS (Đóng gói nội bộ - Không ảnh hưởng bên ngoài)
    // =========================================================================

    // Xử lý chốt Winner và sinh Đơn hàng cho các phiên đến giờ kết thúc
    private void processEndedAuctions(LocalDateTime now) {
        List<Auction> endedAuctions = auctionRepository
                .findByStatusAndAuctionTypeNotAndEndTimeLessThanEqual(
                        AuctionStatus.RUNNING,
                        AuctionType.BUY_NOW,
                        now
                );

        for (Auction auction : endedAuctions) {
            Optional<Bid> highestBidOpt = bidRepository
                    .findTopByAuctionIdOrderByBidAmountDescCreatedAtAsc(auction.getId());

            User winner = null;

            if (highestBidOpt.isPresent()) {
                Bid highestBid = highestBidOpt.get();

                if (auction.getAuctionType() == AuctionType.ENGLISH) {
                    winner = highestBid.getBidder();
                }
                else if (auction.getAuctionType() == AuctionType.RESERVE) {
                    if (highestBid.getBidAmount().compareTo(auction.getReservePrice()) >= 0) {
                        winner = highestBid.getBidder();
                    }
                }

                if (winner != null && !orderRepository.existsByAuction_Id(auction.getId())) {
                    Order order = orderMapper.toEntity(auction, winner, highestBid.getBidAmount());
                    order.setPaymentDeadline(now.plusHours(48)); // 👈 Gán hạn chót 48h
                    orderRepository.save(order);
                }
            }

            auction.setWinner(winner);
            auction.setStatus(AuctionStatus.ENDED);
        }
    }

    // Tự động bổ sung Đơn hàng bị khuyết cho các phiên ENDED có Winner
    private void backfillMissingOrders(LocalDateTime now) {
        List<Auction> endedWithWinnerAuctions = auctionRepository.findByStatusAndWinnerIsNotNull(AuctionStatus.ENDED);
        for (Auction auction : endedWithWinnerAuctions) {
            if (!orderRepository.existsByAuction_Id(auction.getId())) {
                Optional<Bid> highestBidOpt = bidRepository
                        .findTopByAuctionIdOrderByBidAmountDescCreatedAtAsc(auction.getId());
                BigDecimal winningPrice = highestBidOpt.map(Bid::getBidAmount).orElse(auction.getCurrentPrice());

                Order order = orderMapper.toEntity(auction, auction.getWinner(), winningPrice);
                order.setPaymentDeadline(now.plusHours(48)); // 👈 Gán hạn chót 48h
                orderRepository.save(order);
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
                    buyer.setBannedUntil(now.plusDays(90)); // 👈 Đủ 3 gậy -> Khóa 90 ngày
                }
                userRepository.save(buyer);
            }
        }
    }
}
