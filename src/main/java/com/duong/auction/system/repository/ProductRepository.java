package com.duong.auction.system.repository;

import com.duong.auction.system.entity.Product;
import com.duong.auction.system.enums.ProductStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ProductRepository extends JpaRepository<Product, Long> {

    /**
     * Truy vấn danh sách sản phẩm của Người Bán với thứ tự ưu tiên UX chuẩn:
     * 1. PENDING (Chờ duyệt)
     * 2. REJECTED (Bị từ chối)
     * 3. RUNNING (Đang đấu giá)
     * 4. SCHEDULED (Sắp đấu giá)
     * 5. ENDED / EXPIRED / CANCELLED (Đã kết thúc / Hết hạn / Hủy -> Xếp cuối)
     */
    @Query("""
        SELECT p FROM Product p
        LEFT JOIN Auction a ON a.product.id = p.id
        WHERE p.seller.id = :sellerId
        ORDER BY
            CASE
                WHEN p.status = 'PENDING' THEN 1
                WHEN p.status = 'REJECTED' THEN 2
                WHEN a.status = 'RUNNING' THEN 3
                WHEN a.status = 'SCHEDULED' THEN 4
                ELSE 5
            END ASC,
            p.createdAt DESC
    """)
    List<Product> findProductsBySellerIdSorted(@Param("sellerId") Long sellerId);

    /**
     * Truy vấn danh sách sản phẩm công khai trên Sàn Đấu Giá (Chỉ lấy bài đã APPROVED):
     * 1. Các bài RUNNING & SCHEDULED lên đầu (bài sắp hết hạn endTime ASC đứng trước)
     * 2. Các bài ENDED & EXPIRED xếp xuống cuối
     */
    @Query("""
        SELECT p FROM Product p
        LEFT JOIN Auction a ON a.product.id = p.id
        WHERE p.status = 'APPROVED'
        ORDER BY
            CASE
                WHEN a.status IN ('RUNNING', 'SCHEDULED') THEN 1
                ELSE 2
            END ASC,
            CASE
                WHEN a.status IN ('RUNNING', 'SCHEDULED') THEN a.endTime
            END ASC,
            p.createdAt DESC
    """)
    List<Product> findAllApprovedProductsSorted();

    //Lấy danh sách sản phẩm của người bán mới nhất sắp xếp giảm dần
    List<Product> findBySeller_IdOrderByCreatedAtDesc(Long sellerId);
    //Lấy danh sách sản phẩm có trạng thái ? sắp sếp giảm dần
    List<Product> findByStatusOrderByCreatedAtDesc(ProductStatus status);
}
