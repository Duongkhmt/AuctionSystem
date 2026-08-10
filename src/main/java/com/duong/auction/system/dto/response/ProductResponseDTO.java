package com.duong.auction.system.dto.response;

import com.duong.auction.system.enums.AuctionStatus;
import com.duong.auction.system.enums.AuctionType;
import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
@Getter
@Setter
public class ProductResponseDTO implements Serializable {
    private static final long serialVersionUID = 1L;
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
