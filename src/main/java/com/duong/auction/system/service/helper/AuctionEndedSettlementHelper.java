package com.duong.auction.system.service.helper;

import com.duong.auction.system.dto.event.AuctionEndedEvent;
import com.duong.auction.system.entity.Auction;
import com.duong.auction.system.entity.Bid;
import com.duong.auction.system.entity.User;
import com.duong.auction.system.enums.AuctionStatus;
import com.duong.auction.system.enums.AuctionType;
import com.duong.auction.system.repository.AuctionRepository;
import com.duong.auction.system.service.producer.AuctionKafkaProducer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 🛠️ HELPER XỬ LÝ CHỐT THẦU TỪNG PHIÊN ĐỘC LẬP (PER-ITEM REQUIRES_NEW TRANSACTION)
 * Tách riêng sang Helper Bean để Spring AOP Proxy can thiệp 100% (Triệt tiêu lỗi Spring Self-Invocation)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AuctionEndedSettlementHelper {

    private final AuctionRepository auctionRepository;
    private final AuctionKafkaProducer kafkaProducer;

    /**
     * Xử lý từng phiên đấu giá trong 1 Giao dịch DB hoàn toàn độc lập (REQUIRES_NEW).
     * Một phiên bị lỗi chỉ làm Rollback duy nhất phiên đó, hoàn toàn không ảnh hưởng tới các phiên khác!
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void processSingleAuctionEnded(Auction auction, Bid highestBid, LocalDateTime now) {
        User winner = determineWinner(auction, highestBid);

        // 1. Chốt trạng thái phiên thầu và Winner
        auction.setWinner(winner);
        auction.setStatus(AuctionStatus.ENDED);
        auctionRepository.save(auction);

        // 2. Đóng gói DTO Sự kiện
        AuctionEndedEvent endedEvent = AuctionEndedEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .auctionId(auction.getId())
                .winnerId(winner != null ? winner.getId() : null)
                .finalPrice(highestBid != null ? highestBid.getBidAmount() : auction.getCurrentPrice())
                .endedReason("TIMEOUT")
                .timestamp(now)
                .build();

        // 3. 🟢 CHỐNG DUAL-WRITE: CHỜ GIAO DỊCH ĐỘC LẬP NÀY COMMIT XONG MỚI BẮN KAFKA EVENT!
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    kafkaProducer.sendAuctionEndedEvent(endedEvent);
                }
            });
        } else {
            kafkaProducer.sendAuctionEndedEvent(endedEvent);
        }
    }

    private User determineWinner(Auction auction, Bid bid) {
        if (bid == null) return null;
        if (auction.getAuctionType() == AuctionType.ENGLISH) return bid.getBidder();
        if (auction.getAuctionType() == AuctionType.RESERVE && bid.getBidAmount().compareTo(auction.getReservePrice()) >= 0) {
            return bid.getBidder();
        }
        return null;
    }
}
