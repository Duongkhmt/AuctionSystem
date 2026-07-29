package com.example.DuAnTrainning.controller;

import com.example.DuAnTrainning.dto.request.ProductRequestDTO;
import com.example.DuAnTrainning.dto.response.ProductResponseDTO;
import com.example.DuAnTrainning.service.ProductService;
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

    @GetMapping
    public ResponseEntity<List<ProductResponseDTO>> getSellerProducts(
            @PathVariable Long sellerId
    ) {
        return ResponseEntity.ok(
                productService.getProductsBySellerId(sellerId)
        );
    }
}