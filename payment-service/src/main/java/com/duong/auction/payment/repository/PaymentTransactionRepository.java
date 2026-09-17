package com.duong.auction.payment.repository;

import com.duong.auction.payment.entity.PaymentTransaction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PaymentTransactionRepository extends JpaRepository<PaymentTransaction, Long> {
    Optional<PaymentTransaction> findByIdempotencyKey(String idempotencyKey);

    // Lấy danh sách lịch sử giao dịch của user xếp mới nhất lên đầu
    List<PaymentTransaction> findByUserIdOrderByCreatedAtDesc(Long userId);
}
