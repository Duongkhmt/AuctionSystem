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

    // 1. THỰC HIỆN ĐẶT GIÁ (BID) & TỰ ĐỘNG NHẢY GIÁ PROXY BIDDING

    @Transactional
    public BidResponseDTO placeBid(Long bidderId, Long auctionId, BidRequestDTO requestDTO) {
        User bidder = userRepository.findById(bidderId)
                .orElseThrow(() -> new ApplicationException(ErrorCode.USER_NOT_FOUND));

        // Tìm Auction (Tự động hỗ trợ cả Auction ID lẫn Product ID)
        Auction auction = auctionRepository.findById(auctionId)
                .or(() -> auctionRepository.findByProduct_Id(auctionId))
                .orElseThrow(() -> new ApplicationException(ErrorCode.AUCTION_NOT_FOUND));

        // Dùng auction.getId() để lấy đúng ID phiên đấu giá trong bảng bids!
        Optional<Bid> highestBidOpt = bidRepository.findTopByAuctionIdOrderByBidAmountDescCreatedAtAsc(auction.getId());

        // 1. Validate quy tắc đặt giá
        bidValidator.validateBid(bidder, auction, highestBidOpt, requestDTO);

        // 2. Chạy thuật toán Proxy Bidding Engine
        var proxyResult = proxyBiddingEngineHelper.processProxyBidding(
                auction, bidder, requestDTO.getBidAmount(), requestDTO.getMaxAutoBidAmount()
        );

        // 3. Xử lý Soft-close Anti-sniping
        LocalDateTime now = LocalDateTime.now();
        boolean timeExtended = false;
        if (auction.getEndTime().minusMinutes(ANTI_SNIPING_WINDOW_MINUTES).isBefore(now)) {
            auction.setEndTime(auction.getEndTime().plusMinutes(EXTENSION_MINUTES));
            timeExtended = true;
        }

        // 4. Cập nhật currentPrice & Lưu 100% Audit Trail Bids
        auction.setCurrentPrice(proxyResult.newCurrentPrice());
        auctionRepository.save(auction);
        bidRepository.saveAll(proxyResult.bidsToSave());

        // 5. GỌI HELPER TRẢ VỀ RESPONSE
        return bidResponseHelper.buildResponse(auction, proxyResult.winningBid(), timeExtended);
    }

    // 2. XEM LỊCH SỬ BID CÔNG KHAI (Ẩn danh tên)
    @Transactional(readOnly = true)
    public List<BidHistoryResponseDTO> getAuctionBidHistory(Long auctionId) {
        if (!auctionRepository.existsById(auctionId)) {
            throw new ApplicationException(ErrorCode.AUCTION_NOT_FOUND);
        }
        List<Bid> bids = bidRepository.findByAuctionIdOrderByCreatedAtDesc(auctionId);
        return bidMapper.toHistoryDTOList(bids);
    }

    @Transactional
    public BidResponseDTO executeBuyNow(Long bidderId, Long auctionId) {
        User bidder = userRepository.findById(bidderId)
                .orElseThrow(() -> new ApplicationException(ErrorCode.USER_NOT_FOUND));

        Auction auction = auctionRepository.findById(auctionId)
                .orElseThrow(() -> new ApplicationException(ErrorCode.AUCTION_NOT_FOUND));

        // 1. Validate quy tắc luồng Mua Ngay
        bidValidator.validateBuyNow(bidder, auction);

        // 2. Cập nhật giá và nguời chiến thắng & Chốt đơn sang ENDED
        BigDecimal actualPrice = auction.getBuyNowPrice();
        auction.setCurrentPrice(actualPrice);
        auction.setWinner(bidder);
        auction.setStatus(AuctionStatus.ENDED);

        // 3. Lưu bản ghi mua hàng thành công trực tiếp trong Service (y hệt phong cách placeBid)
        Bid winningBid = new Bid();
        winningBid.setAuction(auction);
        winningBid.setBidder(bidder);
        winningBid.setBidAmount(actualPrice);
        winningBid.setAutoBid(false);

        Bid savedBid = bidRepository.save(winningBid);
        return bidResponseHelper.buildResponse(auction, savedBid, false);
    }

}
