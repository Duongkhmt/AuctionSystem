package com.duong.auction.system.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;


@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class BidHistoryResponseDTO implements Serializable {
    private static final long serialVersionUID = 1L;
    private Long bidId;
    private String maskedBidderName;
    private BigDecimal bidAmount;
    private boolean autoBid;
    private LocalDateTime createdAt;
}
