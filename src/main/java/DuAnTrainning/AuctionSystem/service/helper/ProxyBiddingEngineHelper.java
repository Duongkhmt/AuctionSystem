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


@Component
@RequiredArgsConstructor
public class ProxyBiddingEngineHelper {
    private final BidRepository bidRepository;
    private final BidStepCalculatorHelper bidStepCalculatorHelper;
    // Record kết quả trả về cho Service
    public record ProxyBiddingResult(
            List<Bid> bidsToSave,     // Danh sách TẤT CẢ các bản ghi Bid cần lưu DB (Audit Trail)
            Bid winningBid,           // Bản ghi Bid dẫn đầu giá cao nhất sau đợt này
            BigDecimal newCurrentPrice
    ) {}
    public ProxyBiddingResult processProxyBidding(
            Auction auction,
            User newBidder,
            BigDecimal newBidAmount,
            BigDecimal newMaxAutoBid
    ) {
        // Nếu không điền maxAutoBid -> Dùng chính newBidAmount làm mức giá trần tối đa
        BigDecimal effectiveNewMax = (newMaxAutoBid != null) ? newMaxAutoBid : newBidAmount;
        List<Bid> bidsToSave = new ArrayList<>();
        // 1. LUÔN TẠO BẢN GHI BID ĐẦU TIÊN do người dùng B thực hiện (Lưu 100% Audit Trail)
        Bid humanBidOfB = createBidEntity(auction, newBidder, newBidAmount, newMaxAutoBid, false);
        bidsToSave.add(humanBidOfB);
        // 2. Tìm đối thủ cũ (A) đang cài mức trần Auto-bid cao nhất
        Optional<Bid> existingAutoBidOpt = bidRepository
                .findTopByAuctionIdAndMaxAutoBidIsNotNullOrderByMaxAutoBidDescCreatedAtAsc(auction.getId());
        // TH1: Chưa có ai đặt Auto-bid trước đó (hoặc chính B đặt lại bài mình)
        if (existingAutoBidOpt.isEmpty() || existingAutoBidOpt.get().getBidder().getId().equals(newBidder.getId())) {
            return new ProxyBiddingResult(bidsToSave, humanBidOfB, newBidAmount);
        }
        Bid existingAutoBidder = existingAutoBidOpt.get();
        BigDecimal existingMax = existingAutoBidder.getMaxAutoBid();
        User existingUser = existingAutoBidder.getBidder();
        // TH2: SO SÁNH 2 MỨC GIÁ TRẦN MAX
        if (existingMax.compareTo(effectiveNewMax) >= 0) {
            // A (Người cũ) THẮNG! Giá tự nhảy = Max của B + 1 bước giá (không vượt trần A)
            BigDecimal calculatedPrice = bidStepCalculatorHelper.calculateMinValidBid(effectiveNewMax);
            BigDecimal finalPrice = calculatedPrice.min(existingMax);
            // Robot đại diện A sinh tiếp bản ghi Auto-bid thứ hai phản công
            Bid autoBidForA = createBidEntity(auction, existingUser, finalPrice, existingMax, true);
            bidsToSave.add(autoBidForA);
            return new ProxyBiddingResult(bidsToSave, autoBidForA, finalPrice);
        } else {
            // B (Người mới) THẮNG! Giá nhảy = Max của A + 1 bước giá (không vượt trần B)
            BigDecimal calculatedPrice = bidStepCalculatorHelper.calculateMinValidBid(existingMax);
            BigDecimal finalPrice = calculatedPrice.min(effectiveNewMax);
            // Cập nhật giá công khai B cần bỏ ra để thắng trần của A
            humanBidOfB.setBidAmount(finalPrice);
            return new ProxyBiddingResult(bidsToSave, humanBidOfB, finalPrice);
        }
    }
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

