package DuAnTrainning.AuctionSystem.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class BidRequestDTO {

    @NotNull(message = "Vui lòng nhập giá đặt")
    @DecimalMin(value = "0.01", message = "Giá đặt phải lớn hơn 0")
    private BigDecimal bidAmount;

    @DecimalMin(value = "0.01", message = "Giá Auto Bid phải lớn hơn 0")
    private BigDecimal maxAutoBidAmount;
}