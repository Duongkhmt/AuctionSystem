package DuAnTrainning.AuctionSystem.dto.response;

import DuAnTrainning.AuctionSystem.enums.OrderStatus;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
public class SellerOrderResponseDTO {
    private Long orderId;
    private Long productId;
    private String productTitle;
    private BigDecimal winningPrice;
    private String buyerName;
    private String buyerPhone;
    private String shippingAddress;
    private OrderStatus status;
    private String courierName;
    private String trackingNumber;
    private LocalDateTime createdAt;
}
