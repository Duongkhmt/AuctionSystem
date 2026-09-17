package com.duong.auction.system.client;

import com.duong.auction.system.dto.request.DisburseRequestDTO;
import com.duong.auction.system.dto.request.PaymentRequestDTO;
import com.duong.auction.system.dto.response.PaymentResponseDTO;
import com.duong.auction.system.dto.response.PaymentTransactionResponseDTO;
import com.duong.auction.system.dto.response.WalletResponseDTO;
import com.duong.auction.system.enums.PaymentStatus;
import com.duong.auction.system.exception.ApplicationException;
import com.duong.auction.system.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@Slf4j
public class PaymentFeignFallback implements PaymentFeignClient {

    @Override
    public PaymentResponseDTO processPayment(String idempotencyKey, PaymentRequestDTO request) {
        log.warn("⚠️ [Circuit Breaker Fallback] PAYMENT-SERVICE bị sập hoặc timeout quá 3s! Đang kích hoạt gia hạn 24h cho đơn hàng ID: {}", 
                request != null ? request.getOrderId() : "N/A");

        return PaymentResponseDTO.builder()
                .status(PaymentStatus.PENDING_RETRY)
                .errorCode(ErrorCode.PAYMENT_SERVICE_UNAVAILABLE.name())
                .message(ErrorCode.PAYMENT_SERVICE_UNAVAILABLE.getMessage())
                .paymentDeadlineExtended(true)
                .build();
    }

    @Override
    public PaymentResponseDTO disbursePayment(String idempotencyKey, DisburseRequestDTO request) {
        log.warn("⚠️ [Circuit Breaker Fallback] PAYMENT-SERVICE bị sập! Không thể giải ngân cho OrderID: {}", 
                request != null ? request.getOrderId() : "N/A");

        return PaymentResponseDTO.builder()
                .status(PaymentStatus.PENDING_RETRY)
                .errorCode(ErrorCode.PAYMENT_SERVICE_UNAVAILABLE.name())
                .message(ErrorCode.PAYMENT_SERVICE_UNAVAILABLE.getMessage())
                .build();
    }

    @Override
    public WalletResponseDTO getWalletByUserId(Long userId) {
        log.error("❌ [Circuit Breaker Fallback] PAYMENT-SERVICE bị sập! Ném ApplicationException ngắt luồng cho UserId: {}", userId);
        throw new ApplicationException(ErrorCode.PAYMENT_SERVICE_UNAVAILABLE);
    }

    @Override
    public List<PaymentTransactionResponseDTO> getTransactionHistory(Long userId) {
        log.error("❌ [Circuit Breaker Fallback] PAYMENT-SERVICE bị sập! Ném ApplicationException ngắt luồng cho UserId: {}", userId);
        throw new ApplicationException(ErrorCode.PAYMENT_SERVICE_UNAVAILABLE);
    }
}
