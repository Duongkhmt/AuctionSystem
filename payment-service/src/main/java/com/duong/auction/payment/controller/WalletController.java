package com.duong.auction.payment.controller;

import com.duong.auction.payment.dto.response.PaymentTransactionResponseDTO;
import com.duong.auction.payment.dto.response.WalletResponseDTO;
import com.duong.auction.payment.service.PaymentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Controller phục vụ truy vấn số dư ví và lịch sử giao dịch nội bộ.
 * Nhận userId trực tiếp từ path do auction-service gửi sang (đã được bảo vệ qua Lớp 1 X-Internal-Service-Key).
 */
@RestController
@RequestMapping("/v1/wallets")
@RequiredArgsConstructor
public class WalletController {

    private final PaymentService paymentService;

    @GetMapping("/{userId}")
    public ResponseEntity<WalletResponseDTO> getWalletByUserId(@PathVariable Long userId) {
        return ResponseEntity.ok(paymentService.getWalletByUserId(userId));
    }

    @GetMapping("/{userId}/transactions")
    public ResponseEntity<List<PaymentTransactionResponseDTO>> getTransactionHistory(@PathVariable Long userId) {
        return ResponseEntity.ok(paymentService.getTransactionHistory(userId));
    }
}
