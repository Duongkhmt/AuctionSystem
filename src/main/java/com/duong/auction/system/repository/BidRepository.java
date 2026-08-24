package com.duong.auction.system.repository;

import com.duong.auction.system.entity.Bid;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface BidRepository extends JpaRepository<Bid, Long> {
    //Lấy tất cả lượt Bid của một Auction và sắp xếp lượt mới nhất lên trước.
    @EntityGraph(attributePaths = {"bidder"})
    List<Bid> findByAuctionIdOrderByCreatedAtDesc(Long auctionId);

//    @Query("""
//    SELECT b
//    FROM Bid b
//    JOIN FETCH b.bidder
//    WHERE b.auction.id = :auctionId
//    ORDER BY b.createdAt DESC
//""")
//    List<Bid> findByAuctionIdOrderByCreatedAtDesc(
//            @Param("auctionId") Long auctionId
//    );
    // Select * from bids where auctionid=? order by create_at desc

    // Lấy lượt Bid cao nhất hiện tại
    Optional<Bid> findTopByAuctionIdOrderByBidAmountDescCreatedAtAsc(Long auctionId);
    //Select * from bids where auction_id= ? order by Bidamount desc limit 1
    //lấy lươt autobid cao nhất hiện tại
    Optional<Bid> findTopByAuctionIdAndMaxAutoBidIsNotNullOrderByMaxAutoBidDescCreatedAtAsc(Long auctionId);

    // 🟢 BÁ BẠO TỐI ƯU SCHEDULER: Lấy Top Bid của N phiên đấu giá trong duy nhất 1 câu SQL
    @Query(value = """
        SELECT b.* FROM bids b
        INNER JOIN (
            SELECT DISTINCT ON (auction_id) id
            FROM bids
            WHERE auction_id IN (:auctionIds)
            ORDER BY auction_id, bid_amount DESC, created_at ASC
        ) top_bids ON b.id = top_bids.id
    """, nativeQuery = true)
//    @EntityGraph(attributePaths = {"bidder"})
    List<Bid> findHighestBidsByAuctionIdIn(@Param("auctionIds") Collection<Long> auctionIds);

    // 🟢 Kiểm tra Idempotency chống ghi trùng DB từ Redis Stream Worker
    boolean existsByEventId(String eventId);
}
