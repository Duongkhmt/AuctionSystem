package com.duong.auction.system.controller;

import com.duong.auction.system.dto.request.CategoryRequestDTO;
import com.duong.auction.system.dto.request.ProductRejectRequestDTO;
import com.duong.auction.system.dto.response.CategoryResponseDTO;
import com.duong.auction.system.dto.response.ProductResponseDTO;
import com.duong.auction.system.service.CategoryService;
import com.duong.auction.system.service.ProductService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
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
    private final CategoryService categoryService;



    // =========================================================================
    // 1. NHÓM API KIỂM DUYỆT SẢN PHẨM & PHIÊN ĐẤU GIÁ (PRODUCTS MODERATION)
    // =========================================================================

    // =========================================================================
    // 1.1 API ADMIN XEM DANH SÁCH BÀI ĐĂNG ĐANG CHỜ DUYỆT (ProductStatus = PENDING)
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
    // 1.2 API ADMIN CHẤP THUẬN DUYỆT BÀI ĐĂNG (APPROVE)
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
    // 1.3 API ADMIN TỪ CHỐI BÀI ĐĂNG KÈM LÝ DO VI PHẠM (REJECTED)
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

    // =========================================================================
    // 2. NHÓM API QUẢN LÝ DANH MỤC SẢN PHẨM (CATEGORY MANAGEMENT)
    // =========================================================================
    /**
     * API 2.1: Admin xem danh sách tất cả các danh mục (Bao gồm cả danh mục bị ẩn).
     * Endpoint: GET /v1/admin/categories
     */
    @GetMapping("/categories")
    public ResponseEntity<List<CategoryResponseDTO>> getAllCategories() {
        return ResponseEntity.ok(categoryService.getAllCategoriesForAdmin());
    }
    /**
     * API 2.2: Admin tạo mới một danh mục sản phẩm.
     * Endpoint: POST /v1/admin/categories
     */
    @PostMapping("/categories")
    public ResponseEntity<CategoryResponseDTO> createCategory(
            @Valid @RequestBody CategoryRequestDTO requestDTO
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(categoryService.createCategory(requestDTO));
    }
    /**
     * API 2.3: Admin chỉnh sửa thông tin danh mục theo ID.
     * Endpoint: PUT /v1/admin/categories/{id}
     */
    @PutMapping("/categories/{id}")
    public ResponseEntity<CategoryResponseDTO> updateCategory(
            @PathVariable Long id,
            @Valid @RequestBody CategoryRequestDTO requestDTO
    ) {
        return ResponseEntity.ok(categoryService.updateCategory(id, requestDTO));
    }
    /**
     * API 2.4: Admin ẩn / ngưng hoạt động danh mục theo ID (Soft Delete).
     * Endpoint: DELETE /v1/admin/categories/{id}
     */
    @DeleteMapping("/categories/{id}")
    public ResponseEntity<Void> deleteCategory(@PathVariable Long id) {
        categoryService.deleteCategory(id);
        return ResponseEntity.noContent().build();
    }
}
