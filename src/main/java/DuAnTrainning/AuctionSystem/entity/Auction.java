package DuAnTrainning.AuctionSystem.entity;

import DuAnTrainning.AuctionSystem.enums.AuctionStatus;
import DuAnTrainning.AuctionSystem.enums.AuctionType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "auctions")
@Getter @Setter
public class Auction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Enumerated(EnumType.STRING)
    private AuctionType auctionType;

    @Column(name = "buy_now_price", precision = 15, scale = 2)
    private BigDecimal buyNowPrice;

    @Column(name = "start_price", nullable = false, precision = 15, scale = 2)
    private BigDecimal startPrice;

    @Column(name = "reserve_price", precision = 15, scale = 2)
    private BigDecimal reservePrice; // Giá sàn ẩn (nếu có)

    @Column(name = "bid_step", nullable = false, precision = 15, scale = 2)
    private BigDecimal bidStep;

    @Column(name = "current_price", nullable = false, precision = 15, scale = 2)
    private BigDecimal currentPrice;

    @Column(name = "start_time", nullable = false)
    private LocalDateTime startTime;

    @Column(name = "end_time", nullable = false)
    private LocalDateTime endTime;

    @Enumerated(EnumType.STRING)
    private AuctionStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}