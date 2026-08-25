package com.duong.auction.system.repository;

import com.duong.auction.system.entity.Order;
import com.duong.auction.system.enums.OrderStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Set;

@Repository
public interface OrderRepository extends JpaRepository<Order, Long> {

//    // 1. Truy vấn danh sách tất cả các đơn hàng trúng thầu của 1 Người Mua (Buyer), sắp xếp đơn mới nhất xếp trên
    @EntityGraph(attributePaths = {"product", "product.images"})
    List<Order> findByBuyer_IdOrderByCreatedAtDesc(Long buyerId);

//    //Select * from orders where Buy_id = ? order by create_at desc
//    // 2. Truy vấn danh sách tất cả các đơn hàng bán được của 1 Người Bán (Seller), sắp xếp đơn mới nhất xếp trên

    @EntityGraph(attributePaths = {"product", "product.images", "buyer"})
    List<Order> findBySeller_IdOrderByCreatedAtDesc(Long sellerId);

//    // 3. Truy vấn danh sách đơn hàng bán được của Seller có bộ lọc theo trạng thái

    @EntityGraph(attributePaths = {"product", "product.images", "buyer"})
    List<Order> findBySeller_IdAndStatusOrderByCreatedAtDesc(Long sellerId, OrderStatus status);

    // 4. Kiểm tra xem một phiên đấu giá (auctionId) đã được tạo đơn hàng trong Database hay chưa (tránh tạo trùng)
    boolean existsByAuction_Id(Long auctionId);

    // Truy vấn danh sách các đơn hàng UNPAID đã quá hạn chót 48h (paymentDeadline <= now)
    List<Order> findByStatusAndPaymentDeadlineLessThanEqual(OrderStatus status, LocalDateTime now);

    // 🟢 TỐI ƯU BATCH SCHEDULER: Lấy tập hợp auctionId đã tồn tại Order trong 1 câu SQL
    @Query("SELECT o.auction.id FROM Order o WHERE o.auction.id IN :auctionIds")
    Set<Long> findAuctionIdsByAuctionIdIn(@Param("auctionIds") Collection<Long> auctionIds);

}

