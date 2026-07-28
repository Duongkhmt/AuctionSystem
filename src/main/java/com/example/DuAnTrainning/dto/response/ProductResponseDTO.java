package com.example.DuAnTrainning.dto.response;

import com.example.DuAnTrainning.enums.AuctionStatus;
import com.example.DuAnTrainning.enums.AuctionType;
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
    private Map<String, Object> attributes; // Không cần tự parse thủ công nữa
    private String status;
    private List<String> imageUrls;
    private LocalDateTime createdAt;

    // Auction
    private Long auctionId;
    private AuctionType auctionType;
    private BigDecimal startPrice;
    private BigDecimal currentPrice;
    private BigDecimal bidStep;
    private BigDecimal reservePrice;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private AuctionStatus auctionStatus;
}
