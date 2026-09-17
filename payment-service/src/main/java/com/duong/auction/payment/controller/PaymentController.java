package com.duong.auction.payment.controller;

import com.duong.auction.payment.dto.request.PaymentRequestDTO;
import com.duong.auction.payment.dto.response.PaymentResponseDTO;
import com.duong.auction.payment.service.PaymentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/v1/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    /**
     * REST API tiếp nhận yêu cầu trừ tiền ví từ auction-service thông qua OpenFeign.
     */
    @PostMapping("/process-order-payment")
    public ResponseEntity<PaymentResponseDTO> processOrderPayment(
            @RequestHeader(value = "Idempotency-Key", required = true) String idempotencyKey,
            @RequestBody PaymentRequestDTO request) {

        PaymentResponseDTO response = paymentService.processPayment(idempotencyKey, request);
        return ResponseEntity.ok(response);
    }

    /**
     * REST API tiếp nhận yêu cầu giải ngân tiền từ Escrow cho Người Bán thông qua OpenFeign.
     */
    @PostMapping("/disburse-seller")
    public ResponseEntity<PaymentResponseDTO> disburseSeller(
            @RequestHeader(value = "Idempotency-Key", required = true) String idempotencyKey,
            @RequestBody com.duong.auction.payment.dto.request.DisburseRequestDTO request) {

        PaymentResponseDTO response = paymentService.disbursePayment(idempotencyKey, request);
        return ResponseEntity.ok(response);
    }
}
