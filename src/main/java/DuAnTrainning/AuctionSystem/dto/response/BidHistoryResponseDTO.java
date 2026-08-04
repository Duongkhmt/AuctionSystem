package DuAnTrainning.AuctionSystem.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.math.BigDecimal;
import java.time.LocalDateTime;


@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class BidHistoryResponseDTO {
    private Long bidId;
    private String maskedBidderName;
    private BigDecimal bidAmount;
    private boolean autoBid;
    private LocalDateTime createdAt;
}
