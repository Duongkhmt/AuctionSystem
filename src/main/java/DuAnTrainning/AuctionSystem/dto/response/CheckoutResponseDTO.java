package DuAnTrainning.AuctionSystem.dto.response;

import DuAnTrainning.AuctionSystem.enums.OrderStatus;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@Builder
public class CheckoutResponseDTO {
    private Long orderId;
    private OrderStatus status;
    private BigDecimal winningPrice;
    private String transactionCode;
}
