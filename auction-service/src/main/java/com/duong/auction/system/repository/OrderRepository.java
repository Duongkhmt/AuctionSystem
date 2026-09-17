package com.duong.auction.system.repository;

import com.duong.auction.system.entity.Order;
import com.duong.auction.system.enums.OrderStatus;
import org.springframework.data.domain.Pageable;
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

    // Truy vấn các đơn hàng PAYMENT_PENDING_RETRY còn trong thời hạn gia hạn 24h (paymentDeadline > now)
    List<Order> findByStatusAndPaymentDeadlineGreaterThan(OrderStatus status, LocalDateTime now);

    // Truy vấn các đơn hàng đã quá hạn chót cho tập hợp nhiều trạng thái (UNPAID quá 48h, PAYMENT_PENDING_RETRY quá 24h gia hạn)
    @EntityGraph(attributePaths = {"buyer"})
    List<Order> findByStatusInAndPaymentDeadlineLessThanEqual(Collection<OrderStatus> statuses, LocalDateTime now);

    // Bổ sung tham số Pageable pageable để hỗ trợ Throttling 50 đơn/lượt
    List<Order> findByStatusAndPaymentDeadlineGreaterThan(OrderStatus status, LocalDateTime now, Pageable pageable);


    // TỐI ƯU BATCH SCHEDULER: Lấy tập hợp auctionId đã tồn tại Order trong 1 câu SQL
    @Query("SELECT o.auction.id FROM Order o WHERE o.auction.id IN :auctionIds")
    Set<Long> findAuctionIdsByAuctionIdIn(@Param("auctionIds") Collection<Long> auctionIds);

    // 5. Admin: Truy vấn toàn bộ danh sách đơn hàng toàn hệ thống để quản lý Két Escrow Sàn
    @EntityGraph(attributePaths = {"product", "product.images", "buyer", "seller"})
    List<Order> findByOrderByCreatedAtDesc();

    @EntityGraph(attributePaths = {"product", "product.images", "buyer", "seller"})
    List<Order> findByStatusOrderByCreatedAtDesc(OrderStatus status);
}

