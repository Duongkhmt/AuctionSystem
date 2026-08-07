package DuAnTrainning.AuctionSystem.controller;

import DuAnTrainning.AuctionSystem.dto.request.CheckoutRequestDTO;
import DuAnTrainning.AuctionSystem.dto.response.CheckoutResponseDTO;
import DuAnTrainning.AuctionSystem.dto.response.WonAuctionResponseDTO;
import DuAnTrainning.AuctionSystem.service.OrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Controller quản lý tất cả các Endpoint cổng cá nhân của Người Mua (Bidder Portal).
 */
@RestController
@RequestMapping("/v1/bidders/{bidderId}")
@RequiredArgsConstructor
public class BidderController {

    private final OrderService orderService;

    // 1. API TRUY VẤN DANH SÁCH SẢN PHẨM ĐẤU GIÁ THẮNG CUỘC CỦA TÔI
    @GetMapping("/won-auctions")
    public ResponseEntity<List<WonAuctionResponseDTO>> getWonAuctions(
            @PathVariable Long bidderId
    ) {
        return ResponseEntity.ok(orderService.getWonAuctions(bidderId));
    }

    // 2. API NGƯỜI MUA ĐIỀN ĐỊA CHỈ & THANH TOÁN ĐƠN HÀNG (CHECKOUT)
    @PostMapping("/orders/{orderId}/checkout")
    public ResponseEntity<CheckoutResponseDTO> checkout(
            @PathVariable Long bidderId,
            @PathVariable Long orderId,
            @Valid @RequestBody CheckoutRequestDTO requestDTO
    ) {
        return ResponseEntity.ok(orderService.checkout(orderId, bidderId, requestDTO));
    }

    // 3: API NGƯỜI MUA XÁC NHẬN "ĐÃ NHẬN HÀNG THÀNH CÔNG" (CONFIRM RECEIVED)
    @PutMapping("/orders/{orderId}/confirm-received")
    public ResponseEntity<WonAuctionResponseDTO> confirmReceived(
            @PathVariable Long bidderId,
            @PathVariable Long orderId
    ) {
        return ResponseEntity.ok(orderService.confirmReceived(orderId, bidderId));
    }
}
