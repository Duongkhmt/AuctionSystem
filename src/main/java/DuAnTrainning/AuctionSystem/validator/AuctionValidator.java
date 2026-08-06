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

/**
 * Validator chuyên trách kiểm tra các thuộc tính cấu hình của Phiên Đấu Giá (Auction)
 * khi Tạo Mới, Chỉnh Sửa hoặc Đăng Lại (Relist).
 */
@Component
public class AuctionValidator {

    private static final long MIN_AUCTION_DURATION_MINUTES = 30; // Thời lượng phiên tối thiểu = 30 phút

    // Validate cấu hình khi Tạo Mới sản phẩm (truyền ProductRequestDTO)
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

    // Validate cấu hình khi Cập Nhật sản phẩm (truyền Auction Entity)
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

    // Validate quy tắc khi Người Bán thực hiện hành động "Đăng Lại" (Relist Action)
    public void validateRelist(Long sellerId, Auction auction) {
        // 1. Validate quyền sở hữu: Bắt buộc chính chủ Người Bán mới được phép Đăng lại
        if (!auction.getProduct().getSeller().getId().equals(sellerId)) {
            throw new ApplicationException(ErrorCode.UNAUTHORIZED_ACCESS);
        }
        // 2. Validate trạng thái: CHỈ cho phép Đăng lại khi bài đăng bị EXPIRED (Hết hạn 30 ngày thụ động)
        if (auction.getStatus() != AuctionStatus.EXPIRED) {
            throw new ApplicationException(ErrorCode.AUCTION_NOT_RELISTABLE);
        }
    }

    // HÀM DÙNG CHUNG DUY NHẤT: Tập trung 100% logic kiểm tra nghiệp vụ các thuộc tính phiên đấu giá
    private void validateAuctionFields(
            AuctionType auctionType,
            BigDecimal startPrice,
            BigDecimal reservePrice,
            BigDecimal bidStep,
            BigDecimal buyNowPrice,
            LocalDateTime startTime,
            LocalDateTime endTime
    ) {
        // 1. Kiểm tra các tham số bắt buộc không được để null
        if (auctionType == null || startPrice == null || bidStep == null || startTime == null || endTime == null) {
            throw new ApplicationException(ErrorCode.INVALID_REQUEST);
        }

        boolean hasReservePrice = reservePrice != null;

        // 2. Kiểm tra quy tắc loại RESERVE (Đấu giá có giá bảo lưu): Bắt buộc phải có reservePrice
        if (auctionType == AuctionType.RESERVE && !hasReservePrice) {
            throw new ApplicationException(ErrorCode.RESERVE_PRICE_REQUIRED);
        }

        // 3. Kiểm tra quy tắc loại không phải RESERVE: Không được phép có reservePrice
        if (auctionType != AuctionType.RESERVE && hasReservePrice) {
            throw new ApplicationException(ErrorCode.RESERVE_PRICE_NOT_ALLOWED);
        }

        // 4. Kiểm tra quy tắc loại BUY_NOW (Mua Ngay): Bắt buộc phải có buyNowPrice niêm yết
        if (auctionType == AuctionType.BUY_NOW && buyNowPrice == null) {
            throw new ApplicationException(ErrorCode.BUY_NOW_PRICE_REQUIRED);
        }

        // 5. Kiểm tra thời gian kết thúc phải sau thời gian bắt đầu
        if (!endTime.isAfter(startTime)) {
            throw new ApplicationException(ErrorCode.INVALID_AUCTION_TIME);
        }

        // 6. Kiểm tra thời gian bắt đầu không được ở trong quá khứ
        if (startTime.isBefore(LocalDateTime.now())) {
            throw new ApplicationException(ErrorCode.START_TIME_IN_PAST);
        }

        // 7. Kiểm tra giá bảo lưu (nếu có) phải lớn hơn hoặc bằng giá khởi điểm startPrice
        if (hasReservePrice && reservePrice.compareTo(startPrice) < 0) {
            throw new ApplicationException(ErrorCode.RESERVE_PRICE_TOO_LOW);
        }

        // 8. Kiểm tra thời lượng phiên kéo dài tối thiểu 30 phút
        Duration duration = Duration.between(startTime, endTime);
        if (duration.toMinutes() < MIN_AUCTION_DURATION_MINUTES) {
            throw new ApplicationException(ErrorCode.AUCTION_DURATION_TOO_SHORT);
        }
    }
}
