package DuAnTrainning.AuctionSystem.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(
        name = "bids",
        indexes = {
                // Index 1: Tối ưu cho hàm lấy lượt Bid cao nhất hiện tại (findTopByAuctionIdOrderByBidAmountDesc)
                @Index(name = "idx_bid_auction_amount", columnList = "auction_id, bid_amount DESC, created_at ASC"),

                // Index 2: Tối ưu cho hàm lấy lịch sử Bid theo thời gian (findByAuctionIdOrderByCreatedAtDesc)
                @Index(name = "idx_bid_auction_created", columnList = "auction_id, created_at DESC")
        }
)
@Getter
@Setter
@NoArgsConstructor
public class Bid {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "auction_id", nullable = false)
    private Auction auction;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "bidder_id", nullable = false)
    private User bidder;

    @Column(name = "bid_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal bidAmount;

    @Column(name = "max_auto_bid", precision = 15, scale = 2)
    private BigDecimal maxAutoBid;

    // Đặt tên biến Java là 'autoBid' -> Lombok tự sinh Getter: isAutoBid() và Setter: setAutoBid()
    @Column(name = "is_auto_bid", nullable = false)
    private boolean autoBid = false;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
