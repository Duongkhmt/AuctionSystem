package DuAnTrainning.AuctionSystem.controller;

import DuAnTrainning.AuctionSystem.dto.request.ProductRejectRequestDTO;
import DuAnTrainning.AuctionSystem.dto.response.ProductResponseDTO;
import DuAnTrainning.AuctionSystem.service.ProductService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Controller quản lý các Endpoint kiểm duyệt bài đăng sản phẩm của Ban Quản Trị (Admin).
 */
@RestController
@RequestMapping("/v1/admin/products")
@RequiredArgsConstructor
public class AdminProductController {

    private final ProductService productService;

    // =========================================================================
    // 1. API ADMIN XEM DANH SÁCH BÀI ĐĂNG ĐANG CHỜ DUYỆT (ProductStatus = PENDING)
    // GET /v1/admin/products/pending
    // =========================================================================
    @GetMapping("/pending")
    public ResponseEntity<List<ProductResponseDTO>> getPendingProducts() {
        // 1. Gọi Service truy vấn các bài đăng ở trạng thái PENDING
        List<ProductResponseDTO> pendingProducts = productService.getPendingProducts();
        // 2. Trả về danh sách bài chờ duyệt với HTTP Status Code 200 OK
        return ResponseEntity.ok(pendingProducts);
    }

    // =========================================================================
    // 2. API ADMIN CHẤP THUẬN DUYỆT BÀI ĐĂNG (APPROVE)
    // PUT /v1/admin/products/{id}/approve
    // =========================================================================
    @PutMapping("/{id}/approve")
    public ResponseEntity<ProductResponseDTO> approveProduct(@PathVariable Long id) {
        // 1. Gọi Service đổi ProductStatus = APPROVED và kích hoạt AuctionStatus = RUNNING / SCHEDULED
        ProductResponseDTO response = productService.approveProduct(id);
        // 2. Trả về kết quả duyệt bài thành công với HTTP Status Code 200 OK
        return ResponseEntity.ok(response);
    }

    // =========================================================================
    // 3. API ADMIN TỪ CHỐI BÀI ĐĂNG KÈM LÝ DO VI PHẠM (REJECTED)
    // PUT /v1/admin/products/{id}/reject
    // =========================================================================
    @PutMapping("/{id}/reject")
    public ResponseEntity<ProductResponseDTO> rejectProduct(
            @PathVariable Long id,
            @Valid @RequestBody ProductRejectRequestDTO rejectDTO) {
        // 1. Gọi Service đổi ProductStatus = REJECTED, lưu lý do rejectionReason và hủy phiên AuctionStatus = CANCELLED
        ProductResponseDTO response = productService.rejectProduct(id, rejectDTO);
        // 2. Trả về kết quả từ chối thành công với HTTP Status Code 200 OK
        return ResponseEntity.ok(response);
    }
}
