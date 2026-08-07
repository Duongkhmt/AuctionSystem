package DuAnTrainning.AuctionSystem.controller;

import DuAnTrainning.AuctionSystem.dto.request.ProductRequestDTO;
import DuAnTrainning.AuctionSystem.dto.request.ProductUpdateRequestDTO;
import DuAnTrainning.AuctionSystem.dto.request.ShipOrderRequestDTO;
import DuAnTrainning.AuctionSystem.dto.response.ProductResponseDTO;
import DuAnTrainning.AuctionSystem.dto.response.SellerOrderResponseDTO;
import DuAnTrainning.AuctionSystem.enums.OrderStatus;
import DuAnTrainning.AuctionSystem.service.OrderService;
import DuAnTrainning.AuctionSystem.service.ProductService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Controller quản lý các Endpoint thao tác bài đăng và phiên đấu giá của Người Bán (Seller).
 */
@RestController
@RequestMapping("/v1/sellers/{sellerId}")
@RequiredArgsConstructor
public class SellerProductController {

    private final ProductService productService;
    private final OrderService orderService;

    // =========================================================================
    // 1. NHÓM API SẢN PHẨM & PHIÊN ĐẤU GIÁ (PRODUCTS)


    // =========================================================================
    // 1. API XEM DANH SÁCH SẢN PHẨM CÁ NHÂN CỦA NGƯỜI BÁN
    @GetMapping("/products")
    public ResponseEntity<List<ProductResponseDTO>> getSellerProducts(@PathVariable Long sellerId) {
        return ResponseEntity.ok(productService.getProductsBySellerId(sellerId));
    }

    // =========================================================================
    // 2. API NGƯỜI BÁN TẠO BÀI ĐĂNG SẢN PHẨM MỚI (CHỜ ADMIN DUYỆT)
    @PostMapping("/products")
    public ResponseEntity<ProductResponseDTO> createProduct(
            @PathVariable Long sellerId,
            @Valid @ModelAttribute ProductRequestDTO requestDTO
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(productService.createProduct(sellerId, requestDTO));
    }

    // =========================================================================
    // 3. API NGƯỜI BÁN CHỈNH SỬA SẢN PHẨM (KHI CHƯA CHẠY HOẶC CHƯA BẮT ĐẦU)
    @PutMapping("/products/{id}")
    public ResponseEntity<ProductResponseDTO> updateProduct(
            @PathVariable Long sellerId,
            @PathVariable Long id,
            @Valid @ModelAttribute ProductUpdateRequestDTO requestDTO
    ) {
        return ResponseEntity.ok(productService.updateProduct(sellerId, id, requestDTO));
    }

    // =========================================================================
    // 4. API NGƯỜI BÁN XÓA VĨNH VIỄN SẢN PHẨM (KHI CHƯA DIỄN RA)
    @DeleteMapping("/products/{id}")
    public ResponseEntity<Void> deleteProduct(@PathVariable Long sellerId, @PathVariable Long id) {
        productService.deleteProduct(sellerId, id);
        return ResponseEntity.noContent().build();
    }


    // =========================================================================
    // 5. API NGƯỜI BÁN CHỦ ĐỘNG HỦY PHIÊN BÀI ĐĂNG (KHI CHƯA CÓ AI BID)
    @PutMapping("/products/{id}/cancel")
    public ResponseEntity<ProductResponseDTO> cancelAuction(@PathVariable Long sellerId, @PathVariable Long id) {
        return ResponseEntity.ok(productService.cancelAuction(sellerId, id));
    }


    // =========================================================================
    // 6. API NGƯỜI BÁN "ĐĂNG LẠI" (RELIST) SẢN PHẨM HẾT HẠN 30 NGÀY (EXPIRED)
    @PostMapping("/products/{auctionId}/relist")
    public ResponseEntity<ProductResponseDTO> relist(@PathVariable Long sellerId, @PathVariable Long auctionId) {
        return ResponseEntity.ok(productService.relistAuction(sellerId, auctionId));
    }

    // =========================================================================
    // 2. NHÓM API ĐƠN HÀNG HẬU ĐẤU GIÁ (ORDERS)

    // =========================================================================
    // API 3: Người Bán xem danh sách đơn hàng đã bán
    @GetMapping("/orders")
    public ResponseEntity<List<SellerOrderResponseDTO>> getSellerOrders(
            @PathVariable Long sellerId,
            @RequestParam(required = false) OrderStatus status
    ) {
        return ResponseEntity.ok(orderService.getSellerOrders(sellerId, status));
    }


    // API 4: Người Bán bấm nút xuất hàng (PAID -> SHIPPING)
    @PutMapping("/orders/{orderId}/ship")
    public ResponseEntity<SellerOrderResponseDTO> shipOrder(
            @PathVariable Long sellerId,
            @PathVariable Long orderId,
            @Valid @RequestBody ShipOrderRequestDTO requestDTO
    ) {
        return ResponseEntity.ok(orderService.shipOrder(orderId, sellerId, requestDTO));
    }

}