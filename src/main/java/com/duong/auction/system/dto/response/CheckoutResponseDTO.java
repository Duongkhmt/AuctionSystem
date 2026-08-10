package com.duong.auction.system.dto.response;

import com.duong.auction.system.enums.OrderStatus;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;
import java.math.BigDecimal;

@Getter
@Setter
@Builder
public class CheckoutResponseDTO implements Serializable {
    private static final long serialVersionUID = 1L;
    private Long orderId;
    private OrderStatus status;
    private BigDecimal winningPrice;
    private String transactionCode;
}
