package com.duong.auction.system.client;

import com.duong.auction.system.dto.request.PaymentRequestDTO;
import com.duong.auction.system.dto.response.PaymentResponseDTO;
import com.duong.auction.system.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class PaymentFeignFallback implements PaymentFeignClient {

    @Override
    public PaymentResponseDTO processPayment(String idempotencyKey, PaymentRequestDTO request) {
        log.warn("⚠️ [Circuit Breaker Fallback] PAYMENT-SERVICE bị sập hoặc timeout quá 3s! Đang kích hoạt gia hạn 24h cho đơn hàng ID: {}", 
                request != null ? request.getOrderId() : "N/A");

        return PaymentResponseDTO.builder()
                .status("PENDING_RETRY")
                .errorCode(ErrorCode.PAYMENT_SERVICE_UNAVAILABLE.name())
                .message(ErrorCode.PAYMENT_SERVICE_UNAVAILABLE.getMessage())
                .paymentDeadlineExtended(true)
                .build();
    }
}
