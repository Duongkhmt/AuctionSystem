package DuAnTrainning.AuctionSystem.controller;

import DuAnTrainning.AuctionSystem.dto.request.ProductRejectRequestDTO;
import DuAnTrainning.AuctionSystem.dto.response.ProductResponseDTO;
import DuAnTrainning.AuctionSystem.service.ProductService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/v1/admin/products")
@RequiredArgsConstructor
public class AdminProductController {

    private final ProductService productService;

    // 1. Xem danh sách bài đăng đang chờ Admin duyệt (ProductStatus = PENDING)
    @GetMapping("/pending")
    public ResponseEntity<List<ProductResponseDTO>> getPendingProducts() {
        return ResponseEntity.ok(productService.getPendingProducts());
    }

    // 2. Chấp thuận duyệt bài đăng (APPROVED)
    @PutMapping("/{id}/approve")
    public ResponseEntity<ProductResponseDTO> approveProduct(@PathVariable Long id) {
        ProductResponseDTO response = productService.approveProduct(id);
        return ResponseEntity.ok(response);
    }

    // 3. Từ chối bài đăng kèm lý do (REJECTED)
    @PutMapping("/{id}/reject")
    public ResponseEntity<ProductResponseDTO> rejectProduct(
            @PathVariable Long id,
            @Valid @RequestBody ProductRejectRequestDTO rejectDTO) {
        ProductResponseDTO response = productService.rejectProduct(id, rejectDTO);
        return ResponseEntity.ok(response);
    }
}
