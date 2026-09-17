package com.duong.auction.payment.mapper;

import com.duong.auction.payment.dto.request.PaymentRequestDTO;
import com.duong.auction.payment.dto.response.PaymentResponseDTO;
import com.duong.auction.payment.dto.response.PaymentTransactionResponseDTO;
import com.duong.auction.payment.dto.response.WalletResponseDTO;
import com.duong.auction.payment.entity.PaymentTransaction;
import com.duong.auction.payment.entity.Wallet;
import com.duong.auction.payment.enums.PaymentStatus;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Mapper(componentModel = "spring")
public interface PaymentMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "idempotencyKey", source = "idempotencyKey")
    @Mapping(target = "transactionCode", expression = "java(generateTransactionCode())")
    @Mapping(target = "orderId", source = "request.orderId")
    @Mapping(target = "userId", source = "request.userId")
    @Mapping(target = "amount", source = "request.amount")
    @Mapping(target = "status", source = "status")
    @Mapping(target = "createdAt", expression = "java(java.time.LocalDateTime.now(java.time.ZoneId.of(\"Asia/Ho_Chi_Minh\")))")
    PaymentTransaction toTransactionEntity(String idempotencyKey, PaymentRequestDTO request, PaymentStatus status);

    @Mapping(target = "transactionId", source = "tx.transactionCode")
    @Mapping(target = "status", source = "tx.status")
    @Mapping(target = "errorCode", expression = "java(tx.getStatus() == com.duong.auction.payment.enums.PaymentStatus.SUCCESS ? null : tx.getStatus().name())")
    @Mapping(target = "message", expression = "java(tx.getStatus().name())")
    @Mapping(target = "currentBalance", source = "currentBalance")
    @Mapping(target = "paymentDeadlineExtended", constant = "false")
    PaymentResponseDTO toResponseDTO(PaymentTransaction tx, BigDecimal currentBalance);

    // MapStruct tự động ánh xạ Wallet -> WalletResponseDTO
    WalletResponseDTO toWalletResponseDTO(Wallet wallet);

    // MapStruct tự động ánh xạ danh sách PaymentTransaction -> List<PaymentTransactionResponseDTO>
    List<PaymentTransactionResponseDTO> toTransactionResponseDTOList(List<PaymentTransaction> transactions);

    default String generateTransactionCode() {
        return "TXN-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }
}
