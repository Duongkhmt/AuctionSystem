package DuAnTrainning.AuctionSystem.service;

import DuAnTrainning.AuctionSystem.dto.request.BidRequestDTO;
import DuAnTrainning.AuctionSystem.dto.response.BidHistoryResponseDTO;
import DuAnTrainning.AuctionSystem.dto.response.BidResponseDTO;
import DuAnTrainning.AuctionSystem.entity.Auction;
import DuAnTrainning.AuctionSystem.entity.Bid;
import DuAnTrainning.AuctionSystem.entity.User;
import DuAnTrainning.AuctionSystem.enums.AuctionStatus;
import DuAnTrainning.AuctionSystem.exception.ApplicationException;
import DuAnTrainning.AuctionSystem.exception.ErrorCode;
import DuAnTrainning.AuctionSystem.mapper.BidMapper;
import DuAnTrainning.AuctionSystem.repository.AuctionRepository;
import DuAnTrainning.AuctionSystem.repository.BidRepository;
import DuAnTrainning.AuctionSystem.repository.UserRepository;
import DuAnTrainning.AuctionSystem.service.helper.BidResponseHelper;
import DuAnTrainning.AuctionSystem.service.helper.ProxyBiddingEngineHelper;
import DuAnTrainning.AuctionSystem.validator.BidValidator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class BiddingService {

    private final AuctionRepository auctionRepository;
    private final BidRepository bidRepository;
    private final UserRepository userRepository;
    private final BidValidator bidValidator;
    private final BidMapper bidMapper;
    private final ProxyBiddingEngineHelper proxyBiddingEngineHelper;
    private final BidResponseHelper bidResponseHelper;
    private static final int ANTI_SNIPING_WINDOW_MINUTES = 3;
    private static final int EXTENSION_MINUTES = 3;

    // =========================================================================
    // 1. NGHIỆP VỤ ĐẶT GIÁ (BID) & ĐỘNG CƠ PROXY BIDDING TỰ ĐỘNG
    // =========================================================================

    @Transactional
    public BidResponseDTO placeBid(Long bidderId, Long auctionId, BidRequestDTO requestDTO) {
        // 1. Tìm thông tin Người Đấu Giá (Bidder) trong hệ thống
        User bidder = userRepository.findById(bidderId)
                .orElseThrow(() -> new ApplicationException(ErrorCode.USER_NOT_FOUND));

        // 2. Tìm phiên đấu giá Auction (Hỗ trợ linh hoạt tìm theo Auction ID hoặc Product ID)
        Auction auction = auctionRepository.findById(auctionId)
                .or(() -> auctionRepository.findByProduct_Id(auctionId))
                .orElseThrow(() -> new ApplicationException(ErrorCode.AUCTION_NOT_FOUND));

        // 3. Lấy ra bản ghi Bid có mức giá cao nhất hiện tại (dùng auction.getId() chuẩn xác)
        Optional<Bid> highestBidOpt = bidRepository.findTopByAuctionIdOrderByBidAmountDescCreatedAtAsc(auction.getId());

        // 4. Validate toàn bộ quy tắc đặt giá (Kiểm tra trạng thái RUNNING, bước giá động, chống đè giá chính mình & chống seller tự bid)
        bidValidator.validateBid(bidder, auction, highestBidOpt, requestDTO);

        // 5. Kích hoạt động cơ Proxy Bidding Engine: So kè ngân sách maxAutoBid và sinh danh sách các lượt bid đè giá tự động
        var proxyResult = proxyBiddingEngineHelper.processProxyBidding(
                auction, bidder, requestDTO.getBidAmount(), requestDTO.getMaxAutoBidAmount()
        );

        // 6. Xử lý Soft-close Anti-sniping: Nếu có bid hợp lệ trong 3 phút cuối -> Tự động kéo dài thời gian kết thúc thêm 3 phút
        LocalDateTime now = LocalDateTime.now();
        boolean timeExtended = false;
        if (auction.getEndTime().minusMinutes(ANTI_SNIPING_WINDOW_MINUTES).isBefore(now)) {
            auction.setEndTime(auction.getEndTime().plusMinutes(EXTENSION_MINUTES));
            timeExtended = true;
        }

        // 7. Cập nhật giá hiện tại mới currentPrice và lưu trọn vẹn 100% Audit Trail các lượt bid xuống Database
        auction.setCurrentPrice(proxyResult.newCurrentPrice());
        auctionRepository.save(auction);
        bidRepository.saveAll(proxyResult.bidsToSave());

        // 8. Gọi Helper đóng gói dữ liệu phản hồi DTO trả về cho Frontend
        return bidResponseHelper.buildResponse(auction, proxyResult.winningBid(), timeExtended);
    }

    // =========================================================================
    // 2. NGHIỆP VỤ XEM LỊCH SỬ ĐẤU GIÁ CÔNG KHAI (ẨN DANH TÊN)
    // =========================================================================

    @Transactional(readOnly = true)
    public List<BidHistoryResponseDTO> getAuctionBidHistory(Long auctionId) {
        // 1. Kiểm tra sự tồn tại của phiên đấu giá
        if (!auctionRepository.existsById(auctionId)) {
            throw new ApplicationException(ErrorCode.AUCTION_NOT_FOUND);
        }
        // 2. Lấy danh sách lịch sử bid giảm dần theo thời gian tạo mới nhất
        List<Bid> bids = bidRepository.findByAuctionIdOrderByCreatedAtDesc(auctionId);
        // 3. Map sang DTO kèm mã hóa ẩn danh tên bidder (d***g)
        return bidMapper.toHistoryDTOList(bids);
    }

    // =========================================================================
    // 3. NGHIỆP VỤ MUA NGAY GIÁ CỐ ĐỊNH (BUY-NOW - FIRST COME, FIRST SERVED)
    // =========================================================================

    @Transactional
    public BidResponseDTO executeBuyNow(Long bidderId, Long auctionId) {
        // 1. Tìm thông tin Người Mua trong hệ thống
        User bidder = userRepository.findById(bidderId)
                .orElseThrow(() -> new ApplicationException(ErrorCode.USER_NOT_FOUND));

        // 2. Tìm phiên đấu giá Mua Ngay
        Auction auction = auctionRepository.findById(auctionId)
                .orElseThrow(() -> new ApplicationException(ErrorCode.AUCTION_NOT_FOUND));

        // 3. Validate quy tắc luồng Mua Ngay (Bắt buộc auctionType=BUY_NOW, status=RUNNING, buyNowPrice!=null, ko phải seller)
        bidValidator.validateBuyNow(bidder, auction);

        // 4. Lấy giá niêm yết Mua Ngay buyNowPrice và cập nhật currentPrice
        BigDecimal actualPrice = auction.getBuyNowPrice();
        auction.setCurrentPrice(actualPrice);

        // 5. Ghi nhận chính thức người bấm mua đầu tiên là Người Thắng Cuộc (Winner) và chốt đơn sang trạng thái ENDED
        auction.setWinner(bidder);
        auction.setStatus(AuctionStatus.ENDED);

        // 6. Khởi tạo bản ghi Bid mua hàng thành công duy nhất
        Bid winningBid = new Bid();
        winningBid.setAuction(auction);
        winningBid.setBidder(bidder);
        winningBid.setBidAmount(actualPrice);
        winningBid.setAutoBid(false);

        // 7. Lưu bản ghi Bid thắng cuộc xuống Database
        Bid savedBid = bidRepository.save(winningBid);

        // 8. Gọi Helper đóng gói dữ liệu phản hồi trả về cho Frontend
        return bidResponseHelper.buildResponse(auction, savedBid, false);
    }

}
