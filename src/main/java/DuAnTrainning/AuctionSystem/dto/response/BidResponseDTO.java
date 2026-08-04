package DuAnTrainning.AuctionSystem.dto.response;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
@Builder
public class BidResponseDTO {
    private Long bidId;
    private Long auctionId;

    // Đã BỎ private Long bidderId hoàn toàn!
    private String maskedBidderName; // Chỉ giữ duy nhất tên ẩn danh (d***g)

    private BigDecimal bidAmount;
    private BigDecimal newCurrentPrice;
    private BigDecimal nextMinBidAmount;
    private boolean timeExtended;
    private LocalDateTime newEndTime;
    private LocalDateTime createdAt;
}
