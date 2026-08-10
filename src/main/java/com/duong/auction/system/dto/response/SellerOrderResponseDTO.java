package com.duong.auction.system.dto.response;

import com.duong.auction.system.enums.OrderStatus;
import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
public class SellerOrderResponseDTO implements Serializable {
    private static final long serialVersionUID = 1L;
    private Long orderId;
    private Long productId;
    private String productTitle;
    private String productImage;
    private BigDecimal winningPrice;
    private String buyerName;
    private String buyerPhone;
    private String shippingAddress;
    private OrderStatus status;
    private String courierName;
    private String trackingNumber;
    private LocalDateTime createdAt;
}
