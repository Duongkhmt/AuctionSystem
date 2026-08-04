package DuAnTrainning.AuctionSystem.validator;

import DuAnTrainning.AuctionSystem.dto.request.BidRequestDTO;
import DuAnTrainning.AuctionSystem.entity.Auction;
import DuAnTrainning.AuctionSystem.entity.Bid;
import DuAnTrainning.AuctionSystem.entity.User;
import DuAnTrainning.AuctionSystem.enums.AuctionStatus;
import DuAnTrainning.AuctionSystem.enums.AuctionType;
import DuAnTrainning.AuctionSystem.exception.ApplicationException;
import DuAnTrainning.AuctionSystem.exception.ErrorCode;
import DuAnTrainning.AuctionSystem.service.helper.BidStepCalculatorHelper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class BidValidator {

    private final BidStepCalculatorHelper bidStepCalculatorHelper;

    public void validateBid(User bidder, Auction auction, Optional<Bid> highestBidOpt, BidRequestDTO requestDTO) {
        LocalDateTime now = LocalDateTime.now();

        // 1. Check đúng trạng thái RUNNING của dự án bạn!
        if (auction.getStatus() != AuctionStatus.RUNNING) {
            throw new ApplicationException(ErrorCode.AUCTION_NOT_RUNNING);
        }
        if (now.isBefore(auction.getStartTime())) {
            throw new ApplicationException(ErrorCode.AUCTION_NOT_STARTED);
        }
        if (now.isAfter(auction.getEndTime())) {
            throw new ApplicationException(ErrorCode.AUCTION_ENDED);
        }

        // 2. Anti-Shill Bidding: Người bán không được tự đặt giá sản phẩm của chính mình
        if (auction.getProduct().getSeller().getId().equals(bidder.getId())) {
            throw new ApplicationException(ErrorCode.CANNOT_BID_OWN_PRODUCT);
        }

        // 3. Không cho phép người đang dẫn đầu giá tự đè giá chính mình
        if (highestBidOpt.isPresent() && highestBidOpt.get().getBidder().getId().equals(bidder.getId())) {
            throw new ApplicationException(ErrorCode.ALREADY_HIGHEST_BIDDER);
        }

        // 4. Validate giá đặt >= minValidBid (currentPrice + bước giá động)
        BigDecimal minValidBid = bidStepCalculatorHelper.calculateMinValidBid(auction.getCurrentPrice());
        if (requestDTO.getBidAmount().compareTo(minValidBid) < 0) {
            throw new ApplicationException(ErrorCode.BID_AMOUNT_TOO_LOW);
        }

        // 5. Validate giá Auto-bid tối đa (nếu có) phải >= bidAmount
        if (requestDTO.getMaxAutoBidAmount() != null
                && requestDTO.getMaxAutoBidAmount().compareTo(requestDTO.getBidAmount()) < 0) {
            throw new ApplicationException(ErrorCode.MAX_AUTO_BID_TOO_LOW);
        }
    }

    public void validateBuyNow(User bidder, Auction auction) {
        // 1. BẮT BUỘC phải là loại hình BUY_NOW
        if (auction.getAuctionType() != AuctionType.BUY_NOW) {
            throw new ApplicationException(ErrorCode.BUY_NOW_NOT_SUPPORTED);
        }
        // 2. BẮT BUỘC buyNowPrice phải có giá trị (Không âm thầm chữa cháy fallback về startPrice!)
        if (auction.getBuyNowPrice() == null) {
            throw new ApplicationException(ErrorCode.BUY_NOW_PRICE_NOT_SET);
        }
        // 3. BẮT BUỘC trạng thái phải là RUNNING
        if (auction.getStatus() != AuctionStatus.RUNNING) {
            throw new ApplicationException(ErrorCode.AUCTION_NOT_RUNNING);
        }
        // 4. Lớp phòng vệ 2 nấc: Đảm bảo chưa quá hạn 30 ngày (Defense-in-depth song song với Scheduler)
        if (LocalDateTime.now().isAfter(auction.getEndTime())) {
            throw new ApplicationException(ErrorCode.AUCTION_ENDED);
        }
        // 5. Người bán không được tự mua bài của chính mình
        if (auction.getProduct().getSeller().getId().equals(bidder.getId())) {
            throw new ApplicationException(ErrorCode.CANNOT_BID_OWN_PRODUCT);
        }
    }
}

