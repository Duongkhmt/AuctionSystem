package DuAnTrainning.AuctionSystem.service.helper;

import DuAnTrainning.AuctionSystem.entity.Auction;
import DuAnTrainning.AuctionSystem.entity.Bid;
import DuAnTrainning.AuctionSystem.entity.User;
import DuAnTrainning.AuctionSystem.repository.BidRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Động cơ Đấu Giá Tự Động Proxy Bidding (eBay Style Algorithm).
 * Tự động so kè trần ngân sách giữa các đối thủ và sinh ra các bản ghi Bid đè giá ngầm 
 * để đảm bảo lưu giữ trọn vẹn 100% Nhật ký vết giao dịch (Audit Trail).
 */
@Component
@RequiredArgsConstructor
public class ProxyBiddingEngineHelper {

    private final BidRepository bidRepository;
    private final BidStepCalculatorHelper bidStepCalculatorHelper;

    // Record kết quả trả về cho BiddingService
    public record ProxyBiddingResult(
            List<Bid> bidsToSave,     // Danh sách TẤT CẢ các bản ghi Bid sinh ra cần lưu xuống DB (Audit Trail)
            Bid winningBid,           // Bản ghi Bid đang dẫn đầu giá cao nhất sau đợt so kè này
            BigDecimal newCurrentPrice // Mức giá công khai mới của phiên đấu giá
    ) {}

    public ProxyBiddingResult processProxyBidding(
            Auction auction,
            User newBidder,
            BigDecimal newBidAmount,
            BigDecimal newMaxAutoBid
    ) {
        // 1. Xác định mức trần hiệu lực tối đa của Người Đấu Giá mới (B): Nếu không nhập maxAutoBid -> Dùng chính newBidAmount
        BigDecimal effectiveNewMax = (newMaxAutoBid != null) ? newMaxAutoBid : newBidAmount;
        List<Bid> bidsToSave = new ArrayList<>();

        // 2. LUÔN LUÔN tạo bản ghi Bid đặt giá ban đầu do con người (B) chủ động bấm nút (Lưu 100% Audit Trail)
        Bid humanBidOfB = createBidEntity(auction, newBidder, newBidAmount, newMaxAutoBid, false);
        bidsToSave.add(humanBidOfB);

        // 3. Tìm đối thủ cũ (A) đang cài mức trần Auto-bid cao nhất trong hệ thống trước đó
        Optional<Bid> existingAutoBidOpt = bidRepository
                .findTopByAuctionIdAndMaxAutoBidIsNotNullOrderByMaxAutoBidDescCreatedAtAsc(auction.getId());

        // KỊCH BẢN 1: Chưa từng có ai đặt Auto-bid trước đó (Hoặc chính B là người cập nhật lại trần của bài mình)
        if (existingAutoBidOpt.isEmpty() || existingAutoBidOpt.get().getBidder().getId().equals(newBidder.getId())) {
            return new ProxyBiddingResult(bidsToSave, humanBidOfB, newBidAmount);
        }

        Bid existingAutoBidder = existingAutoBidOpt.get();
        BigDecimal existingMax = existingAutoBidder.getMaxAutoBid();
        User existingUser = existingAutoBidder.getBidder();

        // KỊCH BẢN 2: SO SÁNH 2 MỨC GIÁ TRẦN NGÂN SÁCH (MAX AUTO-BID) GIỮA A VÀ B
        if (existingMax.compareTo(effectiveNewMax) >= 0) {
            // 2A: Người cũ (A) THẮNG! Giá tự nhảy = Trần của B + 1 bước giá (nhưng không vượt trần của A)
            BigDecimal calculatedPrice = bidStepCalculatorHelper.calculateMinValidBid(effectiveNewMax, auction.getBidStep());
            BigDecimal finalPrice = calculatedPrice.min(existingMax);

            // Robot đại diện A tự động sinh tiếp bản ghi Auto-bid thứ hai đè giá phản công ngay lập tức
            Bid autoBidForA = createBidEntity(auction, existingUser, finalPrice, existingMax, true);
            bidsToSave.add(autoBidForA);

            return new ProxyBiddingResult(bidsToSave, autoBidForA, finalPrice);
        } else {
            // 2B: Người mới (B) THẮNG! Giá tự nhảy = Trần của A + 1 bước giá (nhưng không vượt trần của B)
            BigDecimal calculatedPrice = bidStepCalculatorHelper.calculateMinValidBid(existingMax, auction.getBidStep());
            BigDecimal finalPrice = calculatedPrice.min(effectiveNewMax);

            // Cập nhật lại giá đặt tối thiểu B cần bỏ ra để đè bẹp hoàn toàn mức trần của A
            humanBidOfB.setBidAmount(finalPrice);

            return new ProxyBiddingResult(bidsToSave, humanBidOfB, finalPrice);
        }
    }

    // Helper tạo đối tượng Bid nhanh
    private Bid createBidEntity(Auction auction, User bidder, BigDecimal bidAmount, BigDecimal maxAutoBid, boolean isAutoBid) {
        Bid bid = new Bid();
        bid.setAuction(auction);
        bid.setBidder(bidder);
        bid.setBidAmount(bidAmount);
        bid.setMaxAutoBid(maxAutoBid);
        bid.setAutoBid(isAutoBid);
        return bid;
    }
}
