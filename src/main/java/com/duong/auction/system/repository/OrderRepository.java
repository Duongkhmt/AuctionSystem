package com.duong.auction.system.repository;

import com.duong.auction.system.entity.Order;
import com.duong.auction.system.enums.OrderStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface OrderRepository extends JpaRepository<Order, Long> {

    // 1. Truy vấn danh sách tất cả các đơn hàng trúng thầu của 1 Người Mua (Buyer), sắp xếp đơn mới nhất xếp trên
    List<Order> findByBuyer_IdOrderByCreatedAtDesc(Long buyerId);

    // 2. Truy vấn danh sách tất cả các đơn hàng bán được của 1 Người Bán (Seller), sắp xếp đơn mới nhất xếp trên
    List<Order> findBySeller_IdOrderByCreatedAtDesc(Long sellerId);

    // 3. Truy vấn danh sách đơn hàng bán được của Seller có bộ lọc theo trạng thái
    List<Order> findBySeller_IdAndStatusOrderByCreatedAtDesc(Long sellerId, OrderStatus status);

    // 4. Kiểm tra xem một phiên đấu giá (auctionId) đã được tạo đơn hàng trong Database hay chưa (tránh tạo trùng)
    boolean existsByAuction_Id(Long auctionId);

    // Truy vấn danh sách các đơn hàng UNPAID đã quá hạn chót 48h (paymentDeadline <= now)
    List<Order> findByStatusAndPaymentDeadlineLessThanEqual(OrderStatus status, LocalDateTime now);

}

