package DuAnTrainning.AuctionSystem.controller;

import DuAnTrainning.AuctionSystem.dto.response.ProductResponseDTO;
import DuAnTrainning.AuctionSystem.service.ProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/v1/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;

    @GetMapping
    public ResponseEntity<List<ProductResponseDTO>> getPublicProducts() {
        return ResponseEntity.ok(
                productService.getPublicProducts()
        );
    }

    @GetMapping("/{id}")
    public ResponseEntity<ProductResponseDTO> getProductById(@PathVariable Long id) {
        ProductResponseDTO response = productService.getProductWithAuctionById(id);
        return ResponseEntity.ok(response);
    }
}
