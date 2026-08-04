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

@RestController
@RequestMapping("/v1/auctions/{auctionId}/bids")
@RequiredArgsConstructor
public class AuctionBiddingController {

    private final BiddingService biddingService;

    // 1. Thực hiện Đặt Giá (Bid) mới
    // POST /v1/auctions/{auctionId}/bids?bidderId=1
    @PostMapping
    public ResponseEntity<BidResponseDTO> placeBid(
            @RequestParam Long bidderId,
            @PathVariable Long auctionId,
            @Valid @RequestBody BidRequestDTO requestDTO
    ) {
        BidResponseDTO response = biddingService.placeBid(bidderId, auctionId, requestDTO);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/buy-now")
    public ResponseEntity<BidResponseDTO> buyNow(
            @RequestParam Long bidderId,
            @PathVariable Long auctionId
    ) {
        BidResponseDTO response = biddingService.executeBuyNow(bidderId, auctionId);
        return ResponseEntity.ok(response);
    }

    // 2. Xem lịch sử đặt giá công khai (Ẩn danh tên)
    // GET /v1/auctions/{auctionId}/bids
    @GetMapping
    public ResponseEntity<List<BidHistoryResponseDTO>> getBidHistory(
            @PathVariable Long auctionId
    ) {
        List<BidHistoryResponseDTO> history = biddingService.getAuctionBidHistory(auctionId);
        return ResponseEntity.ok(history);
    }
}
