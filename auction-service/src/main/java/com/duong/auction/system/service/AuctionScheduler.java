package com.duong.auction.system.service;

import com.duong.auction.system.client.PaymentFeignClient;
import com.duong.auction.system.dto.request.PaymentRequestDTO;
import com.duong.auction.system.dto.response.PaymentResponseDTO;
import com.duong.auction.system.entity.Auction;
import com.duong.auction.system.entity.Bid;
import com.duong.auction.system.entity.Order;
import com.duong.auction.system.enums.AuctionStatus;
import com.duong.auction.system.enums.AuctionType;
import com.duong.auction.system.enums.OrderStatus;
import com.duong.auction.system.enums.PaymentStatus;
import com.duong.auction.system.enums.ProductStatus;
import com.duong.auction.system.repository.AuctionRepository;
import com.duong.auction.system.repository.BidRepository;
import com.duong.auction.system.repository.OrderRepository;
import com.duong.auction.system.service.helper.AuctionEndedSettlementHelper;
import com.duong.auction.system.service.helper.OrderPaymentTxHelper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Robot điều hành thời gian hệ thống chạy ngầm định kỳ 10 giây/lần.
 * Tự động kích hoạt phiên, chốt Winner, bắn sự kiện Kafka AUCTION_ENDED,
 * retry thanh toán bị lỗi (giới hạn 50 đơn/lần) & hủy đơn bùng quá hạn (48h với UNPAID, 24h với PENDING_RETRY).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AuctionScheduler {

    private final AuctionRepository auctionRepository;
    private final BidRepository bidRepository;
    private final OrderRepository orderRepository;
    private final AuctionEndedSettlementHelper settlementHelper;
    private final PaymentFeignClient paymentFeignClient;
    private final OrderPaymentTxHelper orderPaymentTxHelper;
    private final Clock clock;

    // Robot điều phối chạy ngầm định kỳ mỗi 10 giây (fixedRate = 10000ms)
    // 🟢 KHÔNG GẮN @Transactional ĐỂ TRÁNH GIỮ HIKARICP DB CONNECTION POOL KHI GỌI FEIGN HTTP
    @Scheduled(fixedRate = 10000)
    @CacheEvict(value = "auctions", allEntries = true)
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

        // 🟢 4. Tự động thử lại thanh toán cho các đơn PAYMENT_PENDING_RETRY (Throttling tối đa 50 đơn/lần)
        retryPendingPayments(now);

        // 🟢 5. Tự động quét hủy các đơn bùng tiền quá hạn chót (UNPAID quá 48h hoặc PAYMENT_PENDING_RETRY quá 24h)
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
                // Gọi qua Injected Spring Bean Proxy -> Kích hoạt @Transactional(REQUIRES_NEW) 100%!
                settlementHelper.processSingleAuctionEnded(auction, highestBid, now);
            } catch (Exception e) {
                log.error(" Lỗi xử lý chốt thầu độc lập cho AuctionId: {}. Bỏ qua phiên này!", auction.getId(), e);
            }
        }
    }

    // 🟢 Tự động quét và thử lại thanh toán (Giới hạn 50 đơn mỗi 10s để tránh nghẽn Thread)
    private void retryPendingPayments(LocalDateTime now) {
        List<Order> pendingOrders = orderRepository
                .findByStatusAndPaymentDeadlineGreaterThan(
                        OrderStatus.PAYMENT_PENDING_RETRY,
                        now,
                        PageRequest.of(0, 50)
                );

        if (pendingOrders.isEmpty()) return;

        for (Order order : pendingOrders) {
            try {
                String idempotencyKey = "PAY_ORDER_" + order.getId() + "_" + order.getBuyer().getId();
                PaymentRequestDTO paymentRequest = PaymentRequestDTO.builder()
                        .orderId(order.getId())
                        .userId(order.getBuyer().getId())
                        .amount(order.getWinningPrice())
                        .build();

                // Gọi Feign Client sang PAYMENT-SERVICE (ngoài Transaction)
                PaymentResponseDTO paymentResponse = paymentFeignClient.processPayment(idempotencyKey, paymentRequest);

                // Nếu thanh toán thành công -> Ủy quyền cho Helper mở Transaction cập nhật Order & Payment
                if (paymentResponse.getStatus() == PaymentStatus.SUCCESS) {
                    orderPaymentTxHelper.updateOrderStatusAndSavePayment(order, null, paymentResponse);
                    log.info(" Retried thanh toán thành công cho Order ID: {}", order.getId());
                }
            } catch (Exception e) {
                log.warn(" Retry thanh toán chưa thành công cho Order ID: {}. Lý do: {}", order.getId(), e.getMessage());
            }
        }
    }

    // 🟢 Tự động quét và xử lý các đơn hàng bùng tiền quá hạn chót (UNPAID quá 48h, PENDING_RETRY quá 24h)
    private void processExpiredUnpaidOrders(LocalDateTime now) {
        List<OrderStatus> expiredStatuses = List.of(OrderStatus.UNPAID, OrderStatus.PAYMENT_PENDING_RETRY);
        List<Order> expiredOrders = orderRepository
                .findByStatusInAndPaymentDeadlineLessThanEqual(expiredStatuses, now);

        for (Order order : expiredOrders) {
            try {
                // 🟢 Ủy quyền cho OrderPaymentTxHelper -> Mở 1 Transaction NGUYÊN TỬ (Atomic) cho từng đơn hàng!
                orderPaymentTxHelper.cancelExpiredOrderAndPenalizeBuyer(order.getId(), now);
            } catch (Exception e) {
                log.error("Lỗi xử lý hủy đơn hết hạn cho OrderId: {}", order.getId(), e);
            }
        }
    }
}
