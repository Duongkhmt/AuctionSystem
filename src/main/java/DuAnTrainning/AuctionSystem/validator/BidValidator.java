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

/**
 * Validator chuyên trách kiểm tra các quy tắc an toàn nghiệp vụ trước khi người dùng thực hiện Đặt Giá hoặc Mua Ngay.
 */
@Component
@RequiredArgsConstructor
public class BidValidator {

    private final BidStepCalculatorHelper bidStepCalculatorHelper;

    // Validate quy tắc khi người mua thực hiện đặt giá cạnh tranh (ENGLISH / RESERVE)
    public void validateBid(User bidder, Auction auction, Optional<Bid> highestBidOpt, BidRequestDTO requestDTO) {
        LocalDateTime now = LocalDateTime.now();

        // 1. Kiểm tra trạng thái phiên đấu giá bắt buộc phải là RUNNING và nằm trong khoảng thời gian [startTime, endTime]
        if (auction.getStatus() != AuctionStatus.RUNNING) {
            throw new ApplicationException(ErrorCode.AUCTION_NOT_RUNNING);
        }
        if (now.isBefore(auction.getStartTime())) {
            throw new ApplicationException(ErrorCode.AUCTION_NOT_STARTED);
        }
        if (now.isAfter(auction.getEndTime())) {
            throw new ApplicationException(ErrorCode.AUCTION_ENDED);
        }

        // 2. Quy tắc Chống Gian Lận (Anti-Shill Bidding): Người Bán không được tự đặt giá sản phẩm do chính mình đăng bán
        if (auction.getProduct().getSeller().getId().equals(bidder.getId())) {
            throw new ApplicationException(ErrorCode.CANNOT_BID_OWN_PRODUCT);
        }

        // 3. Quy tắc Đè Giá: Người đang nắm giữ vị trí dẫn đầu giá cao nhất không được phép tự đặt giá đè lên chính mình
        if (highestBidOpt.isPresent() && highestBidOpt.get().getBidder().getId().equals(bidder.getId())) {
            throw new ApplicationException(ErrorCode.ALREADY_HIGHEST_BIDDER);
        }

        // 4. Kiểm tra mức giá đặt tối thiểu bắt buộc >= (currentPrice + bước giá động hợp lệ)
        BigDecimal minValidBid = bidStepCalculatorHelper.calculateMinValidBid(auction.getCurrentPrice(), auction.getBidStep());
        if (requestDTO.getBidAmount().compareTo(minValidBid) < 0) {
            throw new ApplicationException(ErrorCode.BID_AMOUNT_TOO_LOW);
        }

        // 5. Kiểm tra hạn mức ngân sách trần Auto-bid (nếu có) bắt buộc phải lớn hơn hoặc bằng giá khởi điểm đặt
        if (requestDTO.getMaxAutoBidAmount() != null
                && requestDTO.getMaxAutoBidAmount().compareTo(requestDTO.getBidAmount()) < 0) {
            throw new ApplicationException(ErrorCode.MAX_AUTO_BID_TOO_LOW);
        }
    }

    // Validate quy tắc khi người mua bấm nút [⚡ MUA NGAY] giá cố định
    public void validateBuyNow(User bidder, Auction auction) {
        // 1. Kiểm tra BẮT BUỘC bài đăng phải thuộc loại hình BUY_NOW (Không cho phép chốt ngang bài ENGLISH/RESERVE)
        if (auction.getAuctionType() != AuctionType.BUY_NOW) {
            throw new ApplicationException(ErrorCode.BUY_NOW_NOT_SUPPORTED);
        }

        // 2. Kiểm tra BẮT BUỘC buyNowPrice phải có giá trị niêm yết (Không tự âm thầm fallback về startPrice)
        if (auction.getBuyNowPrice() == null) {
            throw new ApplicationException(ErrorCode.BUY_NOW_PRICE_NOT_SET);
        }

        // 3. Kiểm tra BẮT BUỘC phiên đấu giá phải đang ở trạng thái RUNNING
        if (auction.getStatus() != AuctionStatus.RUNNING) {
            throw new ApplicationException(ErrorCode.AUCTION_NOT_RUNNING);
        }

        // 4. Lớp phòng vệ 2 nấc: Đảm bảo thời gian hiển thị chưa quá hạn 30 ngày (Defense-in-depth song song với Robot Scheduler)
        if (LocalDateTime.now().isAfter(auction.getEndTime())) {
            throw new ApplicationException(ErrorCode.AUCTION_ENDED);
        }

        // 5. Kiểm tra Quy tắc Chống Gian Lận: Người Bán không được tự bấm Mua Ngay bài viết của chính mình
        if (auction.getProduct().getSeller().getId().equals(bidder.getId())) {
            throw new ApplicationException(ErrorCode.CANNOT_BID_OWN_PRODUCT);
        }
    }
}
