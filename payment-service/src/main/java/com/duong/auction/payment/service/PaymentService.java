package com.duong.auction.payment.service;

import com.duong.auction.payment.dto.request.PaymentRequestDTO;
import com.duong.auction.payment.dto.response.PaymentResponseDTO;
import com.duong.auction.payment.dto.response.PaymentTransactionResponseDTO;
import com.duong.auction.payment.dto.response.WalletResponseDTO;
import com.duong.auction.payment.entity.PaymentTransaction;
import com.duong.auction.payment.entity.Wallet;
import com.duong.auction.payment.exception.ApplicationException;
import com.duong.auction.payment.exception.ErrorCode;
import com.duong.auction.payment.mapper.PaymentMapper;
import com.duong.auction.payment.repository.PaymentTransactionRepository;
import com.duong.auction.payment.repository.WalletRepository;
import com.duong.auction.payment.service.helper.PaymentTxHelper;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentService {

    private final PaymentTxHelper paymentTxHelper;
    private final WalletRepository walletRepository;
    private final PaymentTransactionRepository paymentTransactionRepository;
    private final PaymentMapper paymentMapper;

    public PaymentResponseDTO processPayment(String idempotencyKey, PaymentRequestDTO request) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new ApplicationException(ErrorCode.IDEMPOTENCY_KEY_REQUIRED);
        }

        // Step 1: Fast-path check Idempotency Key
        Optional<PaymentResponseDTO> existingResponse = paymentTxHelper.findIdempotentResponse(idempotencyKey, request.getUserId());
        if (existingResponse.isPresent()) {
            return existingResponse.get();
        }

        try {
            // Step 2: Thực thi giao dịch trừ tiền & lưu DB qua Spring Bean Proxy
            return paymentTxHelper.executePaymentTransaction(idempotencyKey, request);

        } catch (DataIntegrityViolationException e) {
            // Step 3: Xử lý Race Condition khi 2 request trùng IdempotencyKey gọi song song
            log.warn("Phát hiện Race Condition trên IdempotencyKey: {}. Đọc lại bản ghi transaction đã ghi thành công.", idempotencyKey);

            return paymentTxHelper.findIdempotentResponse(idempotencyKey, request.getUserId())
                    .orElseThrow(() -> new ApplicationException(ErrorCode.UNCATEGORIZED_EXCEPTION));
        }
    }

    public PaymentResponseDTO disbursePayment(String idempotencyKey, com.duong.auction.payment.dto.request.DisburseRequestDTO request) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new ApplicationException(ErrorCode.IDEMPOTENCY_KEY_REQUIRED);
        }

        Optional<PaymentResponseDTO> existingResponse = paymentTxHelper.findIdempotentResponse(idempotencyKey, request.getSellerId());
        if (existingResponse.isPresent()) {
            return existingResponse.get();
        }

        try {
            return paymentTxHelper.disburseToSeller(idempotencyKey, request);
        } catch (DataIntegrityViolationException e) {
            log.warn("Phát hiện Race Condition trên IdempotencyKey giải ngân: {}. Đọc lại bản ghi transaction.", idempotencyKey);
            return paymentTxHelper.findIdempotentResponse(idempotencyKey, request.getSellerId())
                    .orElseThrow(() -> new ApplicationException(ErrorCode.UNCATEGORIZED_EXCEPTION));
        }
    }

    @Transactional
    public WalletResponseDTO getWalletByUserId(Long userId) {
        Wallet wallet = walletRepository.findByUserId(userId)
                .orElseGet(() -> paymentTxHelper.createDefaultWallet(userId));
        return paymentMapper.toWalletResponseDTO(wallet);
    }

    @Transactional(readOnly = true)
    public List<PaymentTransactionResponseDTO> getTransactionHistory(Long userId) {
        List<PaymentTransaction> transactions = paymentTransactionRepository.findByUserIdOrderByCreatedAtDesc(userId);
        return paymentMapper.toTransactionResponseDTOList(transactions);
    }

}
