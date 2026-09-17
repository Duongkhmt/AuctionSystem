package com.duong.auction.system.controller;

import com.duong.auction.system.client.PaymentFeignClient;
import com.duong.auction.system.dto.response.PaymentTransactionResponseDTO;
import com.duong.auction.system.dto.response.WalletResponseDTO;
import com.duong.auction.system.security.UserCustomDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Controller uỷ quyền (Proxy) tiếp nhận request xem ví tiền từ Frontend (Port 8080).
 * Lấy userId an toàn từ JWT Token đã được xác thực qua JwtAuthenticationFilter của auction-service,
 * sau đó gọi PaymentFeignClient (/v1/wallets/{userId}) uỷ quyền sang PAYMENT-SERVICE.
 */
@RestController
@RequestMapping("/v1/wallets")
@RequiredArgsConstructor
public class WalletProxyController {

    private final PaymentFeignClient paymentFeignClient;

    @GetMapping("/me")
    public ResponseEntity<WalletResponseDTO> getMyWalletInfo(@AuthenticationPrincipal UserCustomDetails userDetails) {
        Long userId = userDetails.getUser().getId();
        return ResponseEntity.ok(paymentFeignClient.getWalletByUserId(userId));
    }

    @GetMapping("/me/transactions")
    public ResponseEntity<List<PaymentTransactionResponseDTO>> getMyTransactionHistory(@AuthenticationPrincipal UserCustomDetails userDetails) {
        Long userId = userDetails.getUser().getId();
        return ResponseEntity.ok(paymentFeignClient.getTransactionHistory(userId));
    }
}
