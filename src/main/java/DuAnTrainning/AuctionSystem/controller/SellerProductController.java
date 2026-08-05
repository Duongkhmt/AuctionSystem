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

@RestController
@RequestMapping("/v1/sellers/{sellerId}/products")
@RequiredArgsConstructor
public class SellerProductController {

    private final ProductService productService;

    @GetMapping
    public ResponseEntity<List<ProductResponseDTO>> getSellerProducts(
            @PathVariable Long sellerId
    ) {
        return ResponseEntity.ok(
                productService.getProductsBySellerId(sellerId)
        );
    }

    @PostMapping
    public ResponseEntity<ProductResponseDTO> createProduct(
            @PathVariable Long sellerId,
            @Valid @ModelAttribute ProductRequestDTO requestDTO
    ) {
        ProductResponseDTO response =
                productService.createProduct(sellerId, requestDTO);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);
    }

    @PutMapping("/{id}")
    public ResponseEntity<ProductResponseDTO> updateProduct(
            @PathVariable Long sellerId,
            @PathVariable Long id,
            @Valid @ModelAttribute ProductUpdateRequestDTO requestDTO
    ) {
        ProductResponseDTO response =
                productService.updateProduct(sellerId, id, requestDTO);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteProduct(
            @PathVariable Long sellerId,
            @PathVariable Long id
    ) {
        productService.deleteProduct(sellerId, id);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{id}/cancel")
    public ResponseEntity<ProductResponseDTO> cancelAuction(
            @PathVariable Long sellerId,
            @PathVariable Long id
    ) {
        ProductResponseDTO response = productService.cancelAuction(sellerId, id);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{auctionId}/relist")
    public ResponseEntity<ProductResponseDTO> relist(
            @PathVariable Long sellerId,
            @PathVariable Long auctionId
    ) {
        ProductResponseDTO response = productService.relistAuction(sellerId, auctionId);
        return ResponseEntity.ok(response);
    }

}