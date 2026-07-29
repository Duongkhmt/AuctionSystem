package com.example.DuAnTrainning.validator;

import com.example.DuAnTrainning.dto.request.ProductRequestDTO;
import com.example.DuAnTrainning.entity.Auction;
import com.example.DuAnTrainning.enums.AuctionType;
import com.example.DuAnTrainning.exception.ApplicationException;
import com.example.DuAnTrainning.exception.ErrorCode;
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
                auction.getStartTime(),
                auction.getEndTime()
        );
    }

    // HÀM DÙNG CHUNG DUY NHẤT: Chứa 100% logic kiểm tra nghiệp vụ
    private void validateAuctionFields(
            AuctionType auctionType,
            BigDecimal startPrice,
            BigDecimal reservePrice,
            BigDecimal bidStep,
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
