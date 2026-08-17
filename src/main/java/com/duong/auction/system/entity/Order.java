package com.duong.auction.system.entity;

import com.duong.auction.system.enums.OrderStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(
        name = "orders",
        indexes = {
                // Tối ưu cho Người Mua tìm đơn trúng thầu (findByBuyer_IdOrderByCreatedAtDesc)
                @Index(name = "idx_order_buyer", columnList = "buyer_id, created_at DESC"),
                // Tối ưu cho Người Bán tìm đơn bán được (findBySeller_IdOrderByCreatedAtDesc)
                @Index(name = "idx_order_seller", columnList = "seller_id, created_at DESC"),
                // Tối ưu cho Robot quét hủy đơn bùng quá 48h (findByStatusAndPaymentDeadlineLessThanEqual)
                @Index(name = "idx_order_status_deadline", columnList = "status, payment_deadline")
        }
)
@Getter
@Setter
@NoArgsConstructor
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "auction_id", nullable = false, unique = true)
    private Auction auction;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "buyer_id", nullable = false)
    private User buyer;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "seller_id", nullable = false)
    private User seller;

    @Column(name = "winning_price", nullable = false, precision = 15, scale = 2)
    private BigDecimal winningPrice;

    @Column(name = "shipping_address", columnDefinition = "TEXT")
    private String shippingAddress;

    @Column(name = "phone_number", length = 20)
    private String phoneNumber;

    @Column(name = "courier_name", length = 50)
    private String courierName;

    @Column(name = "tracking_number", length = 100)
    private String trackingNumber;

    @Column(name = "payment_deadline", nullable = false)
    private LocalDateTime paymentDeadline; // Hạn chót 48 tiếng để thanh toán

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private OrderStatus status; // 👈 Set tường minh ở Service/Mapper, không gán mặc định ở đây!

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();
}
