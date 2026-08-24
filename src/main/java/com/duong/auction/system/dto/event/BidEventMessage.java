package com.duong.auction.system.dto.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * DTO đóng gói dữ liệu sự kiện đặt giá thành công để đẩy vào Redis Stream Queue
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BidEventMessage implements Serializable {
    private String eventId;      // UUID duy nhất chống ghi trùng DB (Idempotency)
    private Long auctionId;
    private Long bidderId;
    private BigDecimal bidAmount;
    private LocalDateTime timestamp;
}
