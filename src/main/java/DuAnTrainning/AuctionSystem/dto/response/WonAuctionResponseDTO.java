package DuAnTrainning.AuctionSystem.dto.response;

import DuAnTrainning.AuctionSystem.enums.OrderStatus;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
public class WonAuctionResponseDTO {
    private Long orderId;
    private Long auctionId;
    private Long productId;
    private String productTitle;
    private String productImage;
    private BigDecimal winningPrice;
    private OrderStatus status;
    private String shippingAddress;
    private String phoneNumber;
    private String courierName;
    private String trackingNumber;
    private LocalDateTime createdAt;
}

