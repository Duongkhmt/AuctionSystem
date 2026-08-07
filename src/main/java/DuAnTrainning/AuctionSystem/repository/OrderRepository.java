package DuAnTrainning.AuctionSystem.repository;

import DuAnTrainning.AuctionSystem.entity.Order;
import DuAnTrainning.AuctionSystem.enums.OrderStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

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

    // 5. Tìm thông tin đơn hàng theo auctionId tương ứng
    Optional<Order> findByAuction_Id(Long auctionId);
}

