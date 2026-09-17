package com.duong.auction.payment.dto.response;

import com.duong.auction.payment.enums.PaymentStatus;
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
