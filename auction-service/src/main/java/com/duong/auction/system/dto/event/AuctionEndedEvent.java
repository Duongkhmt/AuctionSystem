package com.duong.auction.system.dto.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 📦 DTO đại diện cho Sự Kiện Phiên Đấu Giá Kết Thúc (AUCTION_ENDED).
 * Đối tượng này được Producer đóng gói và gửi lên Kafka Topic "auction.events.ended".
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuctionEndedEvent implements Serializable {

    // 1. Định danh duy nhất cho từng tin nhắn sự kiện (Ví dụ: UUID random)
    // Giúp Consumer kiểm tra chống ghi trùng (Idempotency) và truy vết log khi bị đẩy vào DLT
    private String eventId;

    // 2. ID phiên đấu giá vừa kết thúc
    private Long auctionId;

    // 3. ID người thắng thầu (Null nếu phiên kết thúc mà không có ai tham gia đặt giá)
    private Long winnerId;

    // 4. Mức giá trúng thầu cuối cùng (Hoặc giá mua ngay Buy-Now)
    private BigDecimal finalPrice;

    // 5. Lý do kết thúc phiên: "TIMEOUT" (Đã hết giờ) hoặc "BUY_NOW" (Kích hoạt mua ngay)
    private String endedReason;

    // 6. Thời điểm tạo ra sự kiện này
    private LocalDateTime timestamp;
}
