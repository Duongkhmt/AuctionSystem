package com.duong.auction.system.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentResponseDTO {
    private String transactionId;
    private String status; // "SUCCESS", "INSUFFICIENT_BALANCE", "PENDING_RETRY"
    private String errorCode;
    private String message;
    private BigDecimal currentBalance;
    private boolean paymentDeadlineExtended;
}
