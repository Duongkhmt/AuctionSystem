package DuAnTrainning.AuctionSystem.controller;

import DuAnTrainning.AuctionSystem.dto.request.BidRequestDTO;
import DuAnTrainning.AuctionSystem.dto.response.BidHistoryResponseDTO;
import DuAnTrainning.AuctionSystem.dto.response.BidResponseDTO;
import DuAnTrainning.AuctionSystem.service.BiddingService;
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

    private final BiddingService biddingService;

    // =========================================================================
    // 1. API ĐẶT GIÁ CẠNH TRANH (BID) - DÀNH CHO LOẠI HÌNH ENGLISH & RESERVE
    // POST /v1/auctions/{auctionId}/bids?bidderId=1
    // =========================================================================
    @PostMapping
    public ResponseEntity<BidResponseDTO> placeBid(
            @RequestParam Long bidderId,
            @PathVariable Long auctionId,
            @Valid @RequestBody BidRequestDTO requestDTO
    ) {
        // 1. Gọi Service thực hiện kiểm tra quy tắc, chạy Proxy Bidding và gia hạn Anti-sniping (nếu có)
        BidResponseDTO response = biddingService.placeBid(bidderId, auctionId, requestDTO);
        // 2. Trả về kết quả đặt giá với HTTP Status Code 201 CREATED
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    // =========================================================================
    // 2. API MUA NGAY GIÁ CỐ ĐỊNH (BUY-NOW) - FIRST COME, FIRST SERVED
    // POST /v1/auctions/{auctionId}/bids/buy-now?bidderId=2
    // =========================================================================
    @PostMapping("/buy-now")
    public ResponseEntity<BidResponseDTO> buyNow(
            @RequestParam Long bidderId,
            @PathVariable Long auctionId
    ) {
        // 1. Gọi Service thực hiện chốt đơn Mua Ngay giá cố định buyNowPrice và gán Winner
        BidResponseDTO response = biddingService.executeBuyNow(bidderId, auctionId);
        // 2. Trả về kết quả chốt đơn thành công với HTTP Status Code 200 OK
        return ResponseEntity.ok(response);
    }

    // =========================================================================
    // 3. API XEM LỊCH SỬ ĐẶT GIÁ CÔNG KHAI (ẢN DANH TÊN BIDDER)
    // GET /v1/auctions/{auctionId}/bids
    // =========================================================================
    @GetMapping
    public ResponseEntity<List<BidHistoryResponseDTO>> getBidHistory(
            @PathVariable Long auctionId
    ) {
        // 1. Gọi Service lấy danh sách lịch sử bid kèm mã hóa tên (d***g)
        List<BidHistoryResponseDTO> history = biddingService.getAuctionBidHistory(auctionId);
        // 2. Trả về danh sách lịch sử đấu giá với HTTP Status Code 200 OK
        return ResponseEntity.ok(history);
    }
}
