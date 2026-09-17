package com.duong.auction.system.controller;

import com.duong.auction.system.dto.response.ProductResponseDTO;
import com.duong.auction.system.service.ProductService;
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
