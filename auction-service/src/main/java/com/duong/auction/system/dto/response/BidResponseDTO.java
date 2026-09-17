package com.duong.auction.system.dto.response;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
@Builder
public class BidResponseDTO implements Serializable {
    private static final long serialVersionUID = 1L;
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
