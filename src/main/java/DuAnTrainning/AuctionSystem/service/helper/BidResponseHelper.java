package DuAnTrainning.AuctionSystem.service.helper;

import DuAnTrainning.AuctionSystem.dto.response.BidResponseDTO;
import DuAnTrainning.AuctionSystem.entity.Auction;
import DuAnTrainning.AuctionSystem.entity.Bid;
import DuAnTrainning.AuctionSystem.mapper.BidMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
@RequiredArgsConstructor
public class BidResponseHelper {

    private final BidStepCalculatorHelper bidStepCalculatorHelper;
    private final BidMapper bidMapper;

    public BidResponseDTO buildResponse(
            Auction auction,
            Bid winningBid,
            boolean timeExtended
    ) {
        BigDecimal nextMinBid = bidStepCalculatorHelper
                .calculateMinValidBid(auction.getCurrentPrice());

        return BidResponseDTO.builder()
                .bidId(winningBid.getId())
                .auctionId(auction.getId())
                .maskedBidderName(bidMapper.maskUsername(winningBid.getBidder().getUsername()))
                .bidAmount(winningBid.getBidAmount())
                .newCurrentPrice(auction.getCurrentPrice())
                .nextMinBidAmount(nextMinBid)
                .timeExtended(timeExtended)
                .newEndTime(auction.getEndTime())
                .createdAt(winningBid.getCreatedAt())
                .build();
    }
}
