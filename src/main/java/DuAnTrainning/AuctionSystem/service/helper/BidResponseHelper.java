package DuAnTrainning.AuctionSystem.service.helper;

import DuAnTrainning.AuctionSystem.dto.response.BidResponseDTO;
import DuAnTrainning.AuctionSystem.entity.Auction;
import DuAnTrainning.AuctionSystem.entity.Bid;
import DuAnTrainning.AuctionSystem.mapper.BidMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * Helper đóng gói dữ liệu phản hồi BidResponseDTO sau khi người dùng thực hiện Đặt Giá (Bid) hoặc Mua Ngay thành công.
 */
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
        // 1. Tính toán bước giá tối thiểu cho lượt bid tiếp theo dựa trên giá hiện tại mới
        BigDecimal nextMinBid = bidStepCalculatorHelper
                .calculateMinValidBid(auction.getCurrentPrice());

        // 2. Đóng gói đối tượng BidResponseDTO chứa đầy đủ thông tin giao dịch trả về cho Frontend
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
