    package com.example.DuAnTrainning.validator;

    import com.example.DuAnTrainning.dto.request.ProductRequestDTO;
    import com.example.DuAnTrainning.enums.AuctionType;
    import com.example.DuAnTrainning.exception.ApplicationException;
    import com.example.DuAnTrainning.exception.ErrorCode;
    import org.springframework.stereotype.Component;

    import java.time.Duration;
    import java.time.LocalDateTime;

    @Component
    public class AuctionValidator {

        private static final long MIN_AUCTION_DURATION_MINUTES = 30;

        public void validate(ProductRequestDTO dto) {
            boolean hasReservePrice = dto.getReservePrice() != null;

            if (dto.getAuctionType() == AuctionType.RESERVE && !hasReservePrice) {
                throw new ApplicationException(ErrorCode.RESERVE_PRICE_REQUIRED);
            }

            if (dto.getAuctionType() != AuctionType.RESERVE && hasReservePrice) {
                throw new ApplicationException(ErrorCode.RESERVE_PRICE_NOT_ALLOWED);
            }

            if (!dto.getEndTime().isAfter(dto.getStartTime())) {
                throw new ApplicationException(ErrorCode.INVALID_AUCTION_TIME);
            }

            if (dto.getStartTime().isBefore(LocalDateTime.now())) {
                throw new ApplicationException(ErrorCode.START_TIME_IN_PAST);
            }

            if (hasReservePrice
                    && dto.getReservePrice().compareTo(dto.getStartPrice()) < 0) {
                throw new ApplicationException(ErrorCode.RESERVE_PRICE_TOO_LOW);
            }

            Duration duration = Duration.between(dto.getStartTime(), dto.getEndTime());
            if (duration.toMinutes() < MIN_AUCTION_DURATION_MINUTES) {
                throw new ApplicationException(ErrorCode.AUCTION_DURATION_TOO_SHORT);
            }
        }
    }
