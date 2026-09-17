package com.duong.auction.payment.service.helper;

import com.duong.auction.payment.dto.request.PaymentRequestDTO;
import com.duong.auction.payment.dto.response.PaymentResponseDTO;
import com.duong.auction.payment.entity.PaymentTransaction;
import com.duong.auction.payment.entity.Wallet;
import com.duong.auction.payment.enums.PaymentStatus;
import com.duong.auction.payment.mapper.PaymentMapper;
import com.duong.auction.payment.repository.PaymentTransactionRepository;
import com.duong.auction.payment.repository.WalletRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentTxHelper {

    private static final ZoneId HO_CHI_MINH_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    private final WalletRepository walletRepository;
    private final PaymentTransactionRepository paymentTransactionRepository;
    private final PaymentMapper paymentMapper;

    @Transactional(readOnly = true)
    public Optional<PaymentResponseDTO> findIdempotentResponse(String idempotencyKey, Long userId) {
        return paymentTransactionRepository.findByIdempotencyKey(idempotencyKey)
                .map(tx -> {
                    log.info("Trả về kết quả cũ cho IdempotencyKey: {}", tx.getIdempotencyKey());
                    BigDecimal currentBalance = walletRepository.findByUserId(userId)
                            .map(Wallet::getBalance)
                            .orElse(BigDecimal.ZERO);
                    return paymentMapper.toResponseDTO(tx, currentBalance);
                });
    }

    @Transactional
    public PaymentResponseDTO executePaymentTransaction(String idempotencyKey, PaymentRequestDTO request) {
        // A. Khóa dòng ví bằng SELECT ... FOR UPDATE
        Wallet wallet = walletRepository.findByUserIdForUpdate(request.getUserId())
                .orElseGet(() -> createDefaultWallet(request.getUserId()));

        // B. Kiểm tra số dư ví
        if (wallet.getBalance().compareTo(request.getAmount()) < 0) {
            log.error("Số dư ví không đủ. UserID: {}, Balance: {}, Amount: {}", request.getUserId(), wallet.getBalance(), request.getAmount());

            PaymentTransaction failedTx = paymentMapper.toTransactionEntity(idempotencyKey, request, PaymentStatus.INSUFFICIENT_BALANCE);
            paymentTransactionRepository.saveAndFlush(failedTx);

            return paymentMapper.toResponseDTO(failedTx, wallet.getBalance());
        }

        // C. Trừ tiền ví
        BigDecimal newBalance = wallet.getBalance().subtract(request.getAmount());
        wallet.setBalance(newBalance);
        wallet.setUpdatedAt(LocalDateTime.now(ZoneId.of("Asia/Ho_Chi_Minh")));
        walletRepository.save(wallet);

        // D. Lưu lịch sử giao dịch & ép DB flush
        PaymentTransaction successTx = paymentMapper.toTransactionEntity(idempotencyKey, request, PaymentStatus.SUCCESS);
        paymentTransactionRepository.saveAndFlush(successTx);

        log.info("Thanh toán OrderID {} thành công. Số dư ví mới: {}", request.getOrderId(), newBalance);
        return paymentMapper.toResponseDTO(successTx, newBalance);
    }

    @Transactional
    public PaymentResponseDTO disburseToSeller(String idempotencyKey, com.duong.auction.payment.dto.request.DisburseRequestDTO request) {
        Wallet wallet = walletRepository.findByUserIdForUpdate(request.getSellerId())
                .orElseGet(() -> createDefaultWallet(request.getSellerId()));

        BigDecimal newBalance = wallet.getBalance().add(request.getAmount());
        wallet.setBalance(newBalance);
        wallet.setUpdatedAt(LocalDateTime.now(HO_CHI_MINH_ZONE));
        walletRepository.save(wallet);

        PaymentRequestDTO payReq = PaymentRequestDTO.builder()
                .orderId(request.getOrderId())
                .userId(request.getSellerId())
                .amount(request.getAmount())
                .build();
        PaymentTransaction successTx = paymentMapper.toTransactionEntity(idempotencyKey, payReq, PaymentStatus.SUCCESS);
        paymentTransactionRepository.saveAndFlush(successTx);

        log.info("Giải ngân OrderID {} thành công cho SellerID {}. Số dư ví mới: {}", request.getOrderId(), request.getSellerId(), newBalance);
        return paymentMapper.toResponseDTO(successTx, newBalance);
    }

    public Wallet createDefaultWallet(Long userId) {
        log.info("Tự động tạo ví mặc định kèm 5.000.000.000đ cho userId = {}", userId);
        Wallet newWallet = new Wallet();
        newWallet.setUserId(userId);
        newWallet.setBalance(new BigDecimal("5000000000.00"));
        newWallet.setStatus("ACTIVE");
        newWallet.setCreatedAt(LocalDateTime.now(HO_CHI_MINH_ZONE));
        newWallet.setUpdatedAt(LocalDateTime.now(HO_CHI_MINH_ZONE));
        return walletRepository.save(newWallet);
    }
}
