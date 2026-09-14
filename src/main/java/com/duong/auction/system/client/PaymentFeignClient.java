package com.duong.auction.system.client;

import com.duong.auction.system.dto.request.PaymentRequestDTO;
import com.duong.auction.system.dto.response.PaymentResponseDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

@FeignClient(name = "PAYMENT-SERVICE", fallback = PaymentFeignFallback.class)
public interface PaymentFeignClient {

    @PostMapping("/v1/payments/process-order-payment")
    PaymentResponseDTO processPayment(
            @RequestHeader(value = "Idempotency-Key", required = true) String idempotencyKey,
            @RequestBody PaymentRequestDTO request
    );

    @PostMapping("/v1/payments/disburse-seller")
    PaymentResponseDTO disbursePayment(
            @RequestHeader(value = "Idempotency-Key", required = true) String idempotencyKey,
            @RequestBody com.duong.auction.system.dto.request.DisburseRequestDTO request
    );
}
