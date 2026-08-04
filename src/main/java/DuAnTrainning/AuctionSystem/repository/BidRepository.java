package DuAnTrainning.AuctionSystem.repository;

import DuAnTrainning.AuctionSystem.entity.Bid;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface BidRepository extends JpaRepository<Bid, Long> {

    List<Bid> findByAuctionIdOrderByCreatedAtDesc(Long auctionId);
    // Lấy lượt Bid cao nhất hiện tại
    Optional<Bid> findTopByAuctionIdOrderByBidAmountDescCreatedAtAsc(Long auctionId);
    Optional<Bid> findTopByAuctionIdAndMaxAutoBidIsNotNullOrderByMaxAutoBidDescCreatedAtAsc(Long auctionId);
}
