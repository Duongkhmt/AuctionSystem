package com.duong.auction.system.dto.response;

import com.duong.auction.system.enums.PaymentStatus;
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
    private PaymentStatus status;
    private String errorCode;
    private String message;
    private BigDecimal currentBalance;
    private boolean paymentDeadlineExtended;
}
