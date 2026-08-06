package DuAnTrainning.AuctionSystem.service;

import DuAnTrainning.AuctionSystem.entity.Auction;
import DuAnTrainning.AuctionSystem.entity.Bid;
import DuAnTrainning.AuctionSystem.entity.User;
import DuAnTrainning.AuctionSystem.enums.AuctionStatus;
import DuAnTrainning.AuctionSystem.enums.AuctionType;
import DuAnTrainning.AuctionSystem.enums.ProductStatus;
import DuAnTrainning.AuctionSystem.repository.AuctionRepository;
import DuAnTrainning.AuctionSystem.repository.BidRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Robot điều hành thời gian hệ thống chạy ngầm định kỳ 10 giây/lần.
 * Tự động chuyển đổi trạng thái vòng đời sản phẩm mà không cần con người trực 24/7.
 */
@Component
@RequiredArgsConstructor
public class AuctionScheduler {

    private final AuctionRepository auctionRepository;
    private final BidRepository bidRepository;

    // Robot chạy ngầm mỗi 10 giây (fixedRate = 10000ms)
    @Scheduled(fixedRate = 10000)
    @Transactional
    public void processAuctionStatusTransitions() {
        LocalDateTime now = LocalDateTime.now();

        // 1. Tự động kích hoạt các phiên hẹn giờ: Chuyển SCHEDULED -> RUNNING khi đến giờ startTime (Dùng SQL Bulk Update siêu tốc 1ms)
        auctionRepository.autoStartAuctions(
                now,
                AuctionStatus.SCHEDULED,
                AuctionStatus.RUNNING,
                ProductStatus.APPROVED
        );

        // 2. Tự động đánh nhãn các bài Mua Ngay bị ế: Chuyển BUY_NOW 30 ngày -> EXPIRED khi hết 30 ngày (Dùng SQL Bulk Update siêu tốc 1ms)
        auctionRepository.autoExpireBuyNowAuctions(
                now,
                AuctionStatus.RUNNING,
                AuctionStatus.EXPIRED,
                AuctionType.BUY_NOW
        );

        // 3. TỰ ĐỘNG CHỐT NGHỆ THUẬT GÁN WINNER & ĐỔI SANG ENDED CHO CẢ 2 LOẠI HÌNH ENGLISH VÀ RESERVE KHI HẾT GIỜ!
        // 3.1. Tìm danh sách các phiên đang RUNNING (trừ loại BUY_NOW) đã quá giờ endTime
        List<Auction> endedAuctions = auctionRepository
                .findByStatusAndAuctionTypeNotAndEndTimeLessThanEqual(
                        AuctionStatus.RUNNING,
                        AuctionType.BUY_NOW,
                        now
                );

        // 3.2. Vòng lặp duyệt qua từng phiên hết giờ để xác định Người Thắng Cuộc (Winner) theo quy tắc nghiệp vụ
        for (Auction auction : endedAuctions) {
            // Lấy ra bản ghi Bid có giá đặt cao nhất và sớm nhất của phiên này
            Optional<Bid> highestBidOpt = bidRepository
                    .findTopByAuctionIdOrderByBidAmountDescCreatedAtAsc(auction.getId());

            User winner = null; // Mặc định winner = null (Nếu ế không ai bid hoặc không đạt giá bảo lưu)

            if (highestBidOpt.isPresent()) {
                Bid highestBid = highestBidOpt.get();

                if (auction.getAuctionType() == AuctionType.ENGLISH) {
                    // Kịch bản A (ENGLISH): Có người bid -> Người bid cao nhất chính thức thắng cuộc!
                    winner = highestBid.getBidder();
                }
                else if (auction.getAuctionType() == AuctionType.RESERVE) {
                    // Kịch bản B (RESERVE): CHỈ CÓ WINNER KHI mức bid cao nhất >= reservePrice (giá sàn bảo lưu của Seller)
                    if (highestBid.getBidAmount().compareTo(auction.getReservePrice()) >= 0) {
                        winner = highestBid.getBidder();
                    }
                    // Nếu bid < reservePrice -> winner giữ nguyên là null (Bán không thành công)
                }
            }
            // Kịch bản C (Không có bid nào): winner giữ nguyên là null (Sản phẩm ế)

            // 3.3. Gán người thắng (User hoặc null) và cập nhật trạng thái phiên sang ENDED cùng lúc
            auction.setWinner(winner);
            auction.setStatus(AuctionStatus.ENDED);
        }
    }
}
