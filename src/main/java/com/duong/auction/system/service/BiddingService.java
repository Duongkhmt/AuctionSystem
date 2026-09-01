package com.duong.auction.system.service;

import com.duong.auction.system.aspect.RateLimit;
import com.duong.auction.system.dto.request.BidRequestDTO;
import com.duong.auction.system.dto.response.BidHistoryResponseDTO;
import com.duong.auction.system.dto.response.BidResponseDTO;
import com.duong.auction.system.entity.Auction;
import com.duong.auction.system.entity.Bid;
import com.duong.auction.system.entity.Order;
import com.duong.auction.system.entity.User;
import com.duong.auction.system.enums.AuctionStatus;
import com.duong.auction.system.exception.ApplicationException;
import com.duong.auction.system.exception.ErrorCode;
import com.duong.auction.system.mapper.BidMapper;
import com.duong.auction.system.mapper.OrderMapper;
import com.duong.auction.system.repository.AuctionRepository;
import com.duong.auction.system.repository.BidRepository;
import com.duong.auction.system.repository.OrderRepository;
import com.duong.auction.system.repository.UserRepository;
import com.duong.auction.system.service.helper.BidResponseHelper;
import com.duong.auction.system.service.helper.ProxyBiddingEngineHelper;
import com.duong.auction.system.validator.BidValidator;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;


import com.duong.auction.system.service.engine.RedisAtomicBiddingEngine;

@Service
@RequiredArgsConstructor
public class BiddingService {

    private final AuctionRepository auctionRepository;
    private final BidRepository bidRepository;
    private final UserRepository userRepository;
    private final BidValidator bidValidator;
    private final BidMapper bidMapper;
    private final OrderRepository orderRepository;
    private final OrderMapper orderMapper;
    private final Clock clock;

    private final ProxyBiddingEngineHelper proxyBiddingEngineHelper;
    private final BidResponseHelper bidResponseHelper;
    private final RedisAtomicBiddingEngine redisEngine;

    private User getAuthenticatedUser() {
        org.springframework.security.core.Authentication auth = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof com.duong.auction.system.security.UserCustomDetails userCustomDetails) {
            return userCustomDetails.getUser();
        }
        String email = (auth != null) ? auth.getName() : null;
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ApplicationException(ErrorCode.USER_NOT_FOUND));
    }

    // =========================================================================
    // 1. NGHIỆP VỤ ĐẶT GIÁ (BID) BẰNG REDIS ATOMIC LUA SCRIPT (ĐƠN GIẢN & TỐI ƯU SIÊU TỐC)
    // =========================================================================
    @RateLimit(maxRequests = 5, timeWindowSeconds = 10)
    @CacheEvict(value = "bid_history", key = "#auctionId")
    @Transactional
    public BidResponseDTO placeBid(Long auctionId, BidRequestDTO requestDTO) {
        // 1. Lấy thông tin người đặt giá đang đăng nhập từ SecurityContext
        User bidder = getAuthenticatedUser();
        Long bidderId = bidder.getId();

        Auction auction = auctionRepository.findById(auctionId)
                .or(() -> auctionRepository.findByProduct_Id(auctionId))
                .orElseThrow(() -> new ApplicationException(ErrorCode.AUCTION_NOT_FOUND));

        if (auction.getStatus() != com.duong.auction.system.enums.AuctionStatus.RUNNING) {
            throw new ApplicationException(ErrorCode.AUCTION_NOT_RUNNING);
        }

        // 2. REDIS ATOMIC (LUA SCRIPT): Gộp Đọc -> Kiểm tra -> Cập nhật giá thành 1 thao tác nguyên tử duy nhất trên RAM
        boolean success = redisEngine.processBidAtomic(
                auction.getId(),
                bidderId,
                requestDTO.getBidAmount(),
                auction.getBidStep(),
                auction.getCurrentPrice()
        );

        // Request nào trả giá thấp hơn sẽ nhận kết quả "Thua giá" lập tức, 0 bị từ chối do xung đột hệ thống!
        if (!success) {
            throw new ApplicationException(ErrorCode.BID_AMOUNT_TOO_LOW);
        }

        // 3. Nếu thắng giá -> Lưu ngay lượt Bid mới và cập nhật giá phiên đấu giá vào Database
        Bid bid = new Bid();
        bid.setAuction(auction);
        bid.setBidder(bidder);
        bid.setBidAmount(requestDTO.getBidAmount());
        bid.setAutoBid(false);
        bidRepository.save(bid);

        auction.setCurrentPrice(requestDTO.getBidAmount());
        auctionRepository.save(auction);

        // 4. Trả phản hồi đặt giá thành công cho Client
        return BidResponseDTO.builder()
                .auctionId(auction.getId())
                .bidAmount(requestDTO.getBidAmount())
                .newCurrentPrice(requestDTO.getBidAmount())
                .build();
    }

    // =========================================================================
    // 2. NGHIỆP VỤ XEM LỊCH SỬ ĐẤU GIÁ CÔNG KHAI (ẨN DANH TÊN)
    // =========================================================================
    @Cacheable(value = "bid_history", key = "#auctionId")
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
    // Khi mua ngay thành công -> Tự động xé bỏ cả cache lịch sử bid lẫn cache chi tiết sản phẩm!
    @CacheEvict(value = {"bid_history", "auctions"}, key = "#auctionId")
    @Transactional
    public BidResponseDTO executeBuyNow(Long auctionId) {
        // 1. Lấy thông tin Người Mua đang đăng nhập từ SecurityContext
        User bidder = getAuthenticatedUser();

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

        // 6. Lưu bản ghi Bid thắng cuộc duy nhất
        Bid winningBid = new Bid();
        winningBid.setAuction(auction);
        winningBid.setBidder(bidder);
        winningBid.setBidAmount(actualPrice);
        winningBid.setAutoBid(false);

        // 7. Lưu bản ghi Bid thắng cuộc xuống Database
        Bid savedBid = bidRepository.save(winningBid);

        // 8. TỰ ĐỘNG SINH ĐƠN HÀNG HẬU MUA NGAY (TRẠNG THÁI UNPAID))!
        if (!orderRepository.existsByAuction_Id(auction.getId())) {
            Order order = orderMapper.toEntity(auction, bidder, actualPrice);
            order.setPaymentDeadline(LocalDateTime.now(clock).plusHours(48));
            orderRepository.save(order);
        }

        // 9. Gọi Helper đóng gói dữ liệu phản hồi trả về cho Frontend
        return bidResponseHelper.buildResponse(auction, savedBid, false);
    }

}
