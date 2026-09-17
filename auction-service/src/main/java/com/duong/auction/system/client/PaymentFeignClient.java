package com.duong.auction.system.client;

import com.duong.auction.system.dto.request.DisburseRequestDTO;
import com.duong.auction.system.dto.request.PaymentRequestDTO;
import com.duong.auction.system.dto.response.PaymentResponseDTO;
import com.duong.auction.system.dto.response.PaymentTransactionResponseDTO;
import com.duong.auction.system.dto.response.WalletResponseDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

import java.util.List;

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
            @RequestBody DisburseRequestDTO request
    );

    @GetMapping("/v1/wallets/{userId}")
    WalletResponseDTO getWalletByUserId(@PathVariable("userId") Long userId);

    @GetMapping("/v1/wallets/{userId}/transactions")
    List<PaymentTransactionResponseDTO> getTransactionHistory(@PathVariable("userId") Long userId);
}
