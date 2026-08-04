package DuAnTrainning.AuctionSystem.repository;

import DuAnTrainning.AuctionSystem.entity.Auction;
import DuAnTrainning.AuctionSystem.enums.AuctionStatus;
import DuAnTrainning.AuctionSystem.enums.AuctionType;
import DuAnTrainning.AuctionSystem.enums.ProductStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface AuctionRepository extends JpaRepository<Auction, Long> {

    Optional<Auction> findByProduct_Id(Long productId);

    List<Auction> findByProduct_IdIn(Collection<Long> productIds);
    void deleteByProduct_Id(Long productId);

    // 1. Bulk Update: SCHEDULED -> RUNNING khi đến giờ startTime & Sản phẩm đã APPROVED
    @Modifying
    @Query("UPDATE Auction a SET a.status = :runningStatus " +
            "WHERE a.status = :scheduledStatus " +
            "AND a.startTime <= :now " +
            "AND a.product.status = :approvedStatus")
    int autoStartAuctions(
            @Param("now") LocalDateTime now,
            @Param("scheduledStatus") AuctionStatus scheduledStatus,
            @Param("runningStatus") AuctionStatus runningStatus,
            @Param("approvedStatus") ProductStatus approvedStatus
    );
    // 2. Bulk Update: RUNNING -> ENDED khi đến giờ endTime
    @Modifying
    @Query("UPDATE Auction a SET a.status = :endedStatus " +
            "WHERE a.status = :runningStatus " +
            "AND a.endTime <= :now")
    int autoEndAuctions(
            @Param("now") LocalDateTime now,
            @Param("runningStatus") AuctionStatus runningStatus,
            @Param("endedStatus") AuctionStatus endedStatus
    );
    //RUNNING -HẾT HẠN VỚI TRẠNG THÁI ĐẤU GIÁ MUA NGAY
    @Modifying
    @Query("UPDATE Auction a SET a.status = :expiredStatus " +
            "WHERE a.status = :runningStatus " +
            "AND a.auctionType = :buyNowType " +
            "AND a.endTime <= :now")
    int autoExpireBuyNowAuctions(
            @Param("now") LocalDateTime now,
            @Param("runningStatus") AuctionStatus runningStatus,
            @Param("expiredStatus") AuctionStatus expiredStatus,
            @Param("buyNowType") AuctionType buyNowType
    );
}
