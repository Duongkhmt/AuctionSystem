package com.duong.auction.payment.dto.request;

import lombok.*;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DisburseRequestDTO {
    private Long orderId;
    private Long sellerId;
    private BigDecimal amount;
}
