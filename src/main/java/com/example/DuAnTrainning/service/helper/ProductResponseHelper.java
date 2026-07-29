package com.example.DuAnTrainning.service.helper;

import com.example.DuAnTrainning.dto.response.ProductResponseDTO;
import com.example.DuAnTrainning.entity.Auction;
import com.example.DuAnTrainning.entity.Product;
import com.example.DuAnTrainning.entity.ProductImage;
import com.example.DuAnTrainning.exception.ApplicationException;
import com.example.DuAnTrainning.exception.ErrorCode;
import com.example.DuAnTrainning.mapper.ProductMapper;
import com.example.DuAnTrainning.repository.AuctionRepository;
import com.example.DuAnTrainning.repository.ProductImageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class ProductResponseHelper {
    private final AuctionRepository auctionRepository;
    private final ProductImageRepository productImageRepository;
    private final ProductMapper productMapper;

    public ProductResponseDTO build(Product product) {
        Auction auction = auctionRepository.findByProduct_Id(product.getId())
                .orElseThrow(() ->
                        new ApplicationException(ErrorCode.AUCTION_NOT_FOUND)
                );

        List<ProductImage> productImages = productImageRepository
                .findByProductIdOrderByDisplayOrderAsc(product.getId());

        List<String> imageUrls = productImages.stream()
                .map(ProductImage::getImageUrl)
                .toList();

        List<com.example.DuAnTrainning.dto.response.ProductImageDTO> images = productImages.stream()
                .map(img -> new com.example.DuAnTrainning.dto.response.ProductImageDTO(img.getId(), img.getImageUrl(), img.getDisplayOrder()))
                .toList();

        ProductResponseDTO response = productMapper.toDTO(product, auction);
        response.setImageUrls(imageUrls);
        response.setImages(images);

        return response;
    }

    public ProductResponseDTO build(
            Product product,
            Auction auction,
            List<String> imageUrls
    ) {
        List<ProductImage> productImages = productImageRepository
                .findByProductIdOrderByDisplayOrderAsc(product.getId());

        List<com.example.DuAnTrainning.dto.response.ProductImageDTO> images = productImages.stream()
                .map(img -> new com.example.DuAnTrainning.dto.response.ProductImageDTO(img.getId(), img.getImageUrl(), img.getDisplayOrder()))
                .toList();

        ProductResponseDTO response = productMapper.toDTO(product, auction);
        response.setImageUrls(imageUrls);
        response.setImages(images);
        return response;
    }

    public List<ProductResponseDTO> buildAll(List<Product> products) {
        if (products.isEmpty()) {
            return List.of();
        }

        List<Long> productIds = products.stream()
                .map(Product::getId)
                .toList();

        Map<Long, Auction> auctionByProductId = auctionRepository
                .findByProduct_IdIn(productIds)
                .stream()
                .collect(Collectors.toMap(
                        auction -> auction.getProduct().getId(),
                        Function.identity()
                ));

        Map<Long, List<ProductImage>> imagesByProductId = productImageRepository
                .findByProductIdInOrderByProductIdAscDisplayOrderAsc(productIds)
                .stream()
                .collect(Collectors.groupingBy(
                        image -> image.getProduct().getId()
                ));

        return products.stream()
                .map(product -> {
                    Auction auction = auctionByProductId.get(product.getId());

                    if (auction == null) {
                        throw new ApplicationException(
                                ErrorCode.AUCTION_NOT_FOUND
                        );
                    }

                    List<ProductImage> productImages = imagesByProductId
                            .getOrDefault(product.getId(), List.of());

                    List<String> imageUrls = productImages.stream()
                            .map(ProductImage::getImageUrl)
                            .toList();

                    List<com.example.DuAnTrainning.dto.response.ProductImageDTO> images = productImages.stream()
                            .map(img -> new com.example.DuAnTrainning.dto.response.ProductImageDTO(img.getId(), img.getImageUrl(), img.getDisplayOrder()))
                            .toList();

                    ProductResponseDTO dto = productMapper.toDTO(product, auction);
                    dto.setImageUrls(imageUrls);
                    dto.setImages(images);
                    return dto;
                })
                .toList();
    }
}
