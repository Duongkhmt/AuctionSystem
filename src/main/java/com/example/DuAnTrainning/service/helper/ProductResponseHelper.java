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

        List<String> imageUrls = productImageRepository
                .findByProductIdOrderByDisplayOrderAsc(product.getId())
                .stream()
                .map(ProductImage::getImageUrl)
                .toList();

        ProductResponseDTO response = productMapper.toDTO(product, auction);
        response.setImageUrls(imageUrls);

        return response;
    }
    // Dùng cho API create: đã có đủ dữ liệu, không query lại database
    public ProductResponseDTO build(
            Product product,
            Auction auction,
            List<String> imageUrls
    ) {
        ProductResponseDTO response = productMapper.toDTO(product, auction);
        response.setImageUrls(imageUrls);
        return response;
    }

    // Dùng cho API list: chỉ chạy 2 query cho toàn bộ auction và toàn bộ ảnh
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

        Map<Long, List<String>> imageUrlsByProductId = productImageRepository
                .findByProductIdInOrderByProductIdAscDisplayOrderAsc(productIds)
                .stream()
                .collect(Collectors.groupingBy(
                        image -> image.getProduct().getId(),
                        Collectors.mapping(
                                ProductImage::getImageUrl,
                                Collectors.toList()
                        )
                ));

        return products.stream()
                .map(product -> {
                    Auction auction = auctionByProductId.get(product.getId());

                    if (auction == null) {
                        throw new ApplicationException(
                                ErrorCode.AUCTION_NOT_FOUND
                        );
                    }

                    List<String> imageUrls = imageUrlsByProductId
                            .getOrDefault(product.getId(), List.of());

                    return build(product, auction, imageUrls);
                })
                .toList();
    }
}
