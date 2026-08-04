package DuAnTrainning.AuctionSystem.validator;

import DuAnTrainning.AuctionSystem.dto.request.ProductRequestDTO;
import DuAnTrainning.AuctionSystem.entity.Auction;
import DuAnTrainning.AuctionSystem.enums.AuctionStatus;
import DuAnTrainning.AuctionSystem.enums.AuctionType;
import DuAnTrainning.AuctionSystem.exception.ApplicationException;
import DuAnTrainning.AuctionSystem.exception.ErrorCode;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;

@Component
public class AuctionValidator {

    private static final long MIN_AUCTION_DURATION_MINUTES = 30;

    // Dùng cho Create (truyền DTO)
    public void validate(ProductRequestDTO dto) {
        validateAuctionFields(
                dto.getAuctionType(),
                dto.getStartPrice(),
                dto.getReservePrice(),
                dto.getBidStep(),
                dto.getBuyNowPrice(),
                dto.getStartTime(),
                dto.getEndTime()
        );
    }

    // Dùng cho Update (truyền Auction Entity)
    public void validateAuctionEntity(Auction auction) {
        validateAuctionFields(
                auction.getAuctionType(),
                auction.getStartPrice(),
                auction.getReservePrice(),
                auction.getBidStep(),
                auction.getBuyNowPrice(),
                auction.getStartTime(),
                auction.getEndTime()
        );
    }

    public void validateRelist(Long sellerId, Auction auction) {
        // Validate chính chủ Seller
        if (!auction.getProduct().getSeller().getId().equals(sellerId)) {
            throw new ApplicationException(ErrorCode.UNAUTHORIZED_ACCESS);
        }
        // CHỈ cho phép Đăng Lại khi bài đang ở trạng thái EXPIRED (Hết hạn 30 ngày)
        if (auction.getStatus() != AuctionStatus.EXPIRED) {
            throw new ApplicationException(ErrorCode.AUCTION_NOT_RELISTABLE);
        }
    }

    // HÀM DÙNG CHUNG DUY NHẤT: Chứa 100% logic kiểm tra nghiệp vụ
    private void validateAuctionFields(
            AuctionType auctionType,
            BigDecimal startPrice,
            BigDecimal reservePrice,
            BigDecimal bidStep,
            BigDecimal buyNowPrice,
            LocalDateTime startTime,
            LocalDateTime endTime
    ) {
        if (auctionType == null || startPrice == null || bidStep == null || startTime == null || endTime == null) {
            throw new ApplicationException(ErrorCode.INVALID_REQUEST);
        }

        boolean hasReservePrice = reservePrice != null;

        if (auctionType == AuctionType.RESERVE && !hasReservePrice) {
            throw new ApplicationException(ErrorCode.RESERVE_PRICE_REQUIRED);
        }

        if (auctionType != AuctionType.RESERVE && hasReservePrice) {
            throw new ApplicationException(ErrorCode.RESERVE_PRICE_NOT_ALLOWED);
        }

        if (auctionType == AuctionType.BUY_NOW && buyNowPrice == null) {
            throw new ApplicationException(ErrorCode.BUY_NOW_PRICE_REQUIRED);
        }

        if (!endTime.isAfter(startTime)) {
            throw new ApplicationException(ErrorCode.INVALID_AUCTION_TIME);
        }

        if (startTime.isBefore(LocalDateTime.now())) {
            throw new ApplicationException(ErrorCode.START_TIME_IN_PAST);
        }

        if (hasReservePrice && reservePrice.compareTo(startPrice) < 0) {
            throw new ApplicationException(ErrorCode.RESERVE_PRICE_TOO_LOW);
        }

        Duration duration = Duration.between(startTime, endTime);
        if (duration.toMinutes() < MIN_AUCTION_DURATION_MINUTES) {
            throw new ApplicationException(ErrorCode.AUCTION_DURATION_TOO_SHORT);
        }
    }
}
