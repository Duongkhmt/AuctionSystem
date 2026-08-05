package DuAnTrainning.AuctionSystem.service;

import DuAnTrainning.AuctionSystem.entity.Auction;
import DuAnTrainning.AuctionSystem.entity.Bid;
import DuAnTrainning.AuctionSystem.entity.User;
import DuAnTrainning.AuctionSystem.enums.AuctionStatus;
import DuAnTrainning.AuctionSystem.enums.AuctionType;
import DuAnTrainning.AuctionSystem.enums.ProductStatus;
import DuAnTrainning.AuctionSystem.repository.AuctionRepository;
import DuAnTrainning.AuctionSystem.repository.BidRepository; // 👈 1. Import BidRepository
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class AuctionScheduler {

    private final AuctionRepository auctionRepository;
    private final BidRepository bidRepository; //

    @Scheduled(fixedRate = 10000)
    @Transactional
    public void processAuctionStatusTransitions() {
        LocalDateTime now = LocalDateTime.now();

        // 1. Tự động chuyển SCHEDULED -> RUNNING
        auctionRepository.autoStartAuctions(
                now,
                AuctionStatus.SCHEDULED,
                AuctionStatus.RUNNING,
                ProductStatus.APPROVED
        );

        // 2. Tự động chuyển BUY_NOW hết 30 ngày -> EXPIRED
        auctionRepository.autoExpireBuyNowAuctions(
                now,
                AuctionStatus.RUNNING,
                AuctionStatus.EXPIRED,
                AuctionType.BUY_NOW
        );

        // 3. TỰ ĐỘNG GÁN WINNER & ĐỔI SANG ENDED CHO ENGLISH & RESERVE (Xóa hẳn autoEndAuctions thừa!)
        List<Auction> endedAuctions = auctionRepository
                .findByStatusAndAuctionTypeNotAndEndTimeLessThanEqual(
                        AuctionStatus.RUNNING,
                        AuctionType.BUY_NOW,
                        now
                );

        for (Auction auction : endedAuctions) {
            Optional<Bid> highestBidOpt = bidRepository
                    .findTopByAuctionIdOrderByBidAmountDescCreatedAtAsc(auction.getId());

            User winner = null; // Mặc định null (Không người thắng)

            if (highestBidOpt.isPresent()) {
                Bid highestBid = highestBidOpt.get();

                if (auction.getAuctionType() == AuctionType.ENGLISH) {
                    // ENGLISH: Có bid -> Winner = Người bid cao nhất
                    winner = highestBid.getBidder();
                }
                else if (auction.getAuctionType() == AuctionType.RESERVE) {
                    // RESERVE: Chỉ có Winner KHI bid >= reservePrice
                    if (highestBid.getBidAmount().compareTo(auction.getReservePrice()) >= 0) {
                        winner = highestBid.getBidder();
                    }
                }
            }

            // Gán winner (User hoặc null) & Đổi trạng thái sang ENDED cùng lúc!
            auction.setWinner(winner);
            auction.setStatus(AuctionStatus.ENDED);
        }
    }
}
