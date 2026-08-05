package DuAnTrainning.AuctionSystem.dto.response;

import DuAnTrainning.AuctionSystem.enums.AuctionStatus;
import DuAnTrainning.AuctionSystem.enums.AuctionType;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
@Getter
@Setter
public class ProductResponseDTO {
    private Long productId;
    private Long sellerId;
    private Long categoryId;
    private String title;
    private String description;
    private Map<String, Object> attributes;
    private String status;
    private String rejectionReason;
    private List<ProductImageResponseDTO> images;
    private LocalDateTime createdAt;

    // Auction
    private Long auctionId;
    private AuctionType auctionType;
    private BigDecimal startPrice;
    private BigDecimal currentPrice;
    private BigDecimal bidStep;
    private BigDecimal reservePrice;
    private BigDecimal buyNowPrice;
    private Long winnerId;
    private String maskedWinnerName;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private AuctionStatus auctionStatus;
}
