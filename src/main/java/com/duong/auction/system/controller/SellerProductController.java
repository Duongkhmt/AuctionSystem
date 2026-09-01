package com.duong.auction.system.controller;

import com.duong.auction.system.dto.request.ProductRequestDTO;
import com.duong.auction.system.dto.request.ProductUpdateRequestDTO;
import com.duong.auction.system.dto.request.ShipOrderRequestDTO;
import com.duong.auction.system.dto.response.ProductResponseDTO;
import com.duong.auction.system.dto.response.SellerOrderResponseDTO;
import com.duong.auction.system.enums.OrderStatus;
import com.duong.auction.system.service.OrderService;
import com.duong.auction.system.service.ProductService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Controller quản lý các Endpoint thao tác bài đăng và phiên đấu giá của Người Bán (Seller).
 * Tự động xác thực danh tính Người Bán qua JWT Token.
 */
@RestController
@RequestMapping("/v1/sellers/me")
@RequiredArgsConstructor
public class SellerProductController {

    private final ProductService productService;
    private final OrderService orderService;

    // =========================================================================
    // 1. NHÓM API SẢN PHẨM & PHIÊN ĐẤU GIÁ (PRODUCTS)

    // =========================================================================
    // 1. API XEM DANH SÁCH SẢN PHẨM CÁ NHÂN CỦA NGƯỜI BÁN ĐANG ĐĂNG NHẬP
    @GetMapping("/products")
    public ResponseEntity<List<ProductResponseDTO>> getSellerProducts() {
        return ResponseEntity.ok(productService.getProductsBySellerId());
    }

    // =========================================================================
    // 2. API NGƯỜI BÁN TẠO BÀI ĐĂNG SẢN PHẨM MỚI (CHỜ ADMIN DUYỆT)
    @PostMapping("/products")
    public ResponseEntity<ProductResponseDTO> createProduct(
            @Valid @ModelAttribute ProductRequestDTO requestDTO
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(productService.createProduct(requestDTO));
    }

    // =========================================================================
    // 3. API NGƯỜI BÁN CHỈNH SỬA SẢN PHẨM (KHI CHƯA CHẠY HOẶC CHƯA BẮT ĐẦU)
    @PutMapping("/products/{id}")
    public ResponseEntity<ProductResponseDTO> updateProduct(
            @PathVariable Long id,
            @Valid @ModelAttribute ProductUpdateRequestDTO requestDTO
    ) {
        return ResponseEntity.ok(productService.updateProduct(id, requestDTO));
    }

    // =========================================================================
    // 4. API NGƯỜI BÁN XÓA VĨNH VIỄN SẢN PHẨM (KHI CHƯA DIỄN RA)
    @DeleteMapping("/products/{id}")
    public ResponseEntity<Void> deleteProduct(@PathVariable Long id) {
        productService.deleteProduct(id);
        return ResponseEntity.noContent().build();
    }

    // =========================================================================
    // 5. API NGƯỜI BÁN CHỦ ĐỘNG HỦY PHIÊN BÀI ĐĂNG (KHI CHƯA CÓ AI BID)
    @PutMapping("/products/{id}/cancel")
    public ResponseEntity<ProductResponseDTO> cancelAuction(@PathVariable Long id) {
        return ResponseEntity.ok(productService.cancelAuction(id));
    }

    // =========================================================================
    // 6. API NGƯỜI BÁN "ĐĂNG LẠI" (RELIST) SẢN PHẨM HẾT HẠN 30 NGÀY (EXPIRED)
    @PostMapping("/products/{auctionId}/relist")
    public ResponseEntity<ProductResponseDTO> relist(@PathVariable Long auctionId) {
        return ResponseEntity.ok(productService.relistAuction(auctionId));
    }

    // =========================================================================
    // 2. NHÓM API ĐƠN HÀNG HẬU ĐẤU GIÁ (ORDERS)

    // =========================================================================
    // API 3: Người Bán xem danh sách đơn hàng đã bán của chính mình
    @GetMapping("/orders")
    public ResponseEntity<List<SellerOrderResponseDTO>> getSellerOrders(
            @RequestParam(required = false) OrderStatus status
    ) {
        return ResponseEntity.ok(orderService.getSellerOrders(status));
    }

    // API 4: Người Bán bấm nút xuất hàng (PAID -> SHIPPING)
    @PutMapping("/orders/{orderId}/ship")
    public ResponseEntity<SellerOrderResponseDTO> shipOrder(
            @PathVariable Long orderId,
            @Valid @RequestBody ShipOrderRequestDTO requestDTO
    ) {
        return ResponseEntity.ok(orderService.shipOrder(orderId, requestDTO));
    }
}