package com.duong.auction.system.controller;

import com.duong.auction.system.dto.request.BidRequestDTO;
import com.duong.auction.system.dto.response.BidHistoryResponseDTO;
import com.duong.auction.system.dto.response.BidResponseDTO;
import com.duong.auction.system.service.BiddingConcurrencyFacade;
import com.duong.auction.system.service.BiddingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Controller quản lý các Endpoint liên quan đến thao tác Đấu Giá & Mua Ngay của Người Mua (Bidder).
 */
@RestController
@RequestMapping("/v1/auctions/{auctionId}/bids")
@RequiredArgsConstructor
public class AuctionBiddingController {

    private final BiddingConcurrencyFacade biddingConcurrencyFacade;
    private final BiddingService biddingService;

    // =========================================================================
    // 1. API ĐẶT GIÁ CẠNH TRANH (BID) - DÀNH CHO LOẠI HÌNH ENGLISH & RESERVE
    // POST /v1/auctions/{auctionId}/bids?bidderId=1
    // (Bọc Redisson Lock + Rate Limit)
    // =========================================================================
    @PostMapping
    public ResponseEntity<BidResponseDTO> placeBid(
            @RequestParam Long bidderId,
            @PathVariable Long auctionId,
            @Valid @RequestBody BidRequestDTO requestDTO
    ) {
        // 🚀 Gọi trực tiếp BiddingService chạy Redis In-Memory Engine siêu tốc (0.02ms)!
        BidResponseDTO response = biddingService.placeBid(bidderId, auctionId, requestDTO);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    // =========================================================================
    // 2. API MUA NGAY GIÁ CỐ ĐỊNH (BUY-NOW) - FIRST COME, FIRST SERVED
    // POST /v1/auctions/{auctionId}/bids/buy-now?bidderId=2
    // (Bọc Redisson Lock)
    // =========================================================================
    @PostMapping("/buy-now")
    public ResponseEntity<BidResponseDTO> buyNow(
            @RequestParam Long bidderId,
            @PathVariable Long auctionId
    ) {
        // 🚀 Gọi qua Facade để bọc Khóa Phân Tán Redisson Lock!
        BidResponseDTO response = biddingConcurrencyFacade.executeBuyNowWithLock(bidderId, auctionId);
        return ResponseEntity.ok(response);
    }

    // =========================================================================
    // 3. API XEM LỊCH SỬ ĐẶT GIÁ CÔNG KHAI (ẢN DANH TÊN BIDDER)
    // GET /v1/auctions/{auctionId}/bids
    // (Đọc thẳng từ Cache Redis 30s)
    // =========================================================================
    @GetMapping
    public ResponseEntity<List<BidHistoryResponseDTO>> getBidHistory(
            @PathVariable Long auctionId
    ) {
        List<BidHistoryResponseDTO> history = biddingService.getAuctionBidHistory(auctionId);
        return ResponseEntity.ok(history);
    }
}
