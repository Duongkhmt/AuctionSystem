package DuAnTrainning.AuctionSystem.controller;

import DuAnTrainning.AuctionSystem.dto.request.ProductRequestDTO;
import DuAnTrainning.AuctionSystem.dto.request.ProductUpdateRequestDTO;
import DuAnTrainning.AuctionSystem.dto.response.ProductResponseDTO;
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
@RequestMapping("/v1/sellers/{sellerId}/products")
@RequiredArgsConstructor
public class SellerProductController {

    private final ProductService productService;

    // =========================================================================
    // 1. API XEM DANH SÁCH SẢN PHẨM CÁ NHÂN CỦA NGƯỜI BÁN
    // GET /v1/sellers/{sellerId}/products
    // =========================================================================
    @GetMapping
    public ResponseEntity<List<ProductResponseDTO>> getSellerProducts(
            @PathVariable Long sellerId
    ) {
        // 1. Gọi Service lấy toàn bộ danh sách sản phẩm do chính Seller này đăng bán
        List<ProductResponseDTO> products = productService.getProductsBySellerId(sellerId);
        // 2. Trả về kết quả danh sách với HTTP Status Code 200 OK
        return ResponseEntity.ok(products);
    }

    // =========================================================================
    // 2. API NGƯỜI BÁN TẠO BÀI ĐĂNG SẢN PHẨM MỚI (CHỜ ADMIN DUYỆT)
    // POST /v1/sellers/{sellerId}/products
    // =========================================================================
    @PostMapping
    public ResponseEntity<ProductResponseDTO> createProduct(
            @PathVariable Long sellerId,
            @Valid @ModelAttribute ProductRequestDTO requestDTO
    ) {
        // 1. Gọi Service upload ảnh Cloudinary, tạo Product và Auction ở trạng thái chờ duyệt PENDING
        ProductResponseDTO response = productService.createProduct(sellerId, requestDTO);
        // 2. Trả về thông tin bài vừa tạo với HTTP Status Code 201 CREATED
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    // =========================================================================
    // 3. API NGƯỜI BÁN CHỈNH SỬA SẢN PHẨM (KHI CHƯA CHẠY HOẶC CHƯA BẮT ĐẦU)
    // PUT /v1/sellers/{sellerId}/products/{id}
    // =========================================================================
    @PutMapping("/{id}")
    public ResponseEntity<ProductResponseDTO> updateProduct(
            @PathVariable Long sellerId,
            @PathVariable Long id,
            @Valid @ModelAttribute ProductUpdateRequestDTO requestDTO
    ) {
        // 1. Gọi Service kiểm tra chính chủ Seller, validate cấu hình và cập nhật thông tin bài đăng
        ProductResponseDTO response = productService.updateProduct(sellerId, id, requestDTO);
        // 2. Trả về kết quả sau cập nhật với HTTP Status Code 200 OK
        return ResponseEntity.ok(response);
    }

    // =========================================================================
    // 4. API NGƯỜI BÁN XÓA VĨNH VIỄN SẢN PHẨM (KHI CHƯA DIỄN RA)
    // DELETE /v1/sellers/{sellerId}/products/{id}
    // =========================================================================
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteProduct(
            @PathVariable Long sellerId,
            @PathVariable Long id
    ) {
        // 1. Gọi Service dọn dẹp ảnh trên Cloudinary và xóa dữ liệu Product/Auction trong DB
        productService.deleteProduct(sellerId, id);
        // 2. Trả về phản hồi rỗng với HTTP Status Code 24 NO CONTENT
        return ResponseEntity.noContent().build();
    }

    // =========================================================================
    // 5. API NGƯỜI BÁN CHỦ ĐỘNG HỦY PHIÊN BÀI ĐĂNG (KHI CHƯA CÓ AI BID)
    // PUT /v1/sellers/{sellerId}/products/{id}/cancel
    // =========================================================================
    @PutMapping("/{id}/cancel")
    public ResponseEntity<ProductResponseDTO> cancelAuction(
            @PathVariable Long sellerId,
            @PathVariable Long id
    ) {
        // 1. Gọi Service kiểm tra chính chủ và chuyển trạng thái phiên sang CANCELLED
        ProductResponseDTO response = productService.cancelAuction(sellerId, id);
        // 2. Trả về kết quả hủy thành công với HTTP Status Code 200 OK
        return ResponseEntity.ok(response);
    }

    // =========================================================================
    // 6. API NGƯỜI BÁN "ĐĂNG LẠI" (RELIST) SẢN PHẨM HẾT HẠN 30 NGÀY (EXPIRED)
    // POST /v1/sellers/{sellerId}/products/{auctionId}/relist
    // =========================================================================
    @PostMapping("/{auctionId}/relist")
    public ResponseEntity<ProductResponseDTO> relist(
            @PathVariable Long sellerId,
            @PathVariable Long auctionId
    ) {
        // 1. Gọi Service kiểm tra trạng thái EXPIRED và khôi phục RUNNING công khai 30 ngày mới
        ProductResponseDTO response = productService.relistAuction(sellerId, auctionId);
        // 2. Trả về kết quả đăng lại thành công với HTTP Status Code 200 OK
        return ResponseEntity.ok(response);
    }
}