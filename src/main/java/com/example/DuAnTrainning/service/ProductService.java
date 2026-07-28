package com.example.DuAnTrainning.service;

import com.example.DuAnTrainning.dto.request.ProductRequestDTO;
import com.example.DuAnTrainning.dto.response.ProductResponseDTO;
import com.example.DuAnTrainning.entity.*;
import com.example.DuAnTrainning.enums.AuctionStatus;
import com.example.DuAnTrainning.enums.ProductStatus;
import com.example.DuAnTrainning.exception.ApplicationException;
import com.example.DuAnTrainning.exception.ErrorCode;
import com.example.DuAnTrainning.mapper.ProductMapper;
import com.example.DuAnTrainning.repository.*;
import com.example.DuAnTrainning.validator.AuctionValidator;
import com.example.DuAnTrainning.validator.ProductImageValidator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductRepository productRepository;
    private final ProductImageRepository productImageRepository;
    private final AuctionRepository auctionRepository;
    private final UserRepository userRepository;
    private final CategoryRepository categoryRepository;
    private final ProductMapper productMapper;
    private final AuctionValidator auctionValidator;
    private final ProductImageValidator productImageValidator;

    @Transactional
    public ProductResponseDTO createProduct(ProductRequestDTO requestDTO) {
        User seller = userRepository.findById(requestDTO.getSellerId())
                .orElseThrow(() -> new ApplicationException(ErrorCode.USER_NOT_FOUND));

        Category category = categoryRepository.findById(requestDTO.getCategoryId())
                .orElseThrow(() -> new ApplicationException(ErrorCode.CATEGORY_NOT_FOUND));

        if (!category.isActive()) {
            throw new ApplicationException(ErrorCode.CATEGORY_INACTIVE);
        }

        productImageValidator.validate(requestDTO.getImageUrls());
        auctionValidator.validate(requestDTO);

        Product product = productMapper.toEntity(requestDTO);
        product.setSeller(seller);
        product.setCategory(category);
        product.setStatus(ProductStatus.PENDING);

        Product savedProduct = productRepository.save(product);

        List<ProductImage> images = buildProductImages(savedProduct, requestDTO.getImageUrls());
        productImageRepository.saveAll(images);

        Auction auction = buildAuction(savedProduct, requestDTO);
        Auction savedAuction = auctionRepository.save(auction);

        List<String> imageUrls = images.stream()
                .map(ProductImage::getImageUrl)
                .collect(Collectors.toList());

        return buildProductResponse(savedProduct, savedAuction, imageUrls);
    }

    @Transactional(readOnly = true)
    public ProductResponseDTO getProductWithAuctionById(Long productId) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ApplicationException(ErrorCode.PRODUCT_NOT_FOUND));

        Auction auction = auctionRepository.findByProduct_Id(productId)
                .orElseThrow(() -> new ApplicationException(ErrorCode.AUCTION_NOT_FOUND));

        List<String> imageUrls = productImageRepository.findByProductIdOrderByDisplayOrderAsc(productId)
                .stream()
                .map(ProductImage::getImageUrl)
                .collect(Collectors.toList());

        return buildProductResponse(product, auction, imageUrls);
    }

    private List<ProductImage> buildProductImages(Product product, List<String> imageUrls) {
        return IntStream.range(0, imageUrls.size())
                .mapToObj(i -> {
                    ProductImage img = new ProductImage();
                    img.setProduct(product);
                    img.setImageUrl(imageUrls.get(i));
                    img.setDisplayOrder(i);
                    return img;
                })
                .collect(Collectors.toList());
    }

    private Auction buildAuction(Product product, ProductRequestDTO dto) {
        Auction auction = new Auction();
        auction.setProduct(product);
        auction.setAuctionType(dto.getAuctionType());
        auction.setStartPrice(dto.getStartPrice());
        auction.setReservePrice(dto.getReservePrice());
        auction.setBidStep(dto.getBidStep());
        auction.setCurrentPrice(dto.getStartPrice());
        auction.setStartTime(dto.getStartTime());
        auction.setEndTime(dto.getEndTime());
        auction.setStatus(AuctionStatus.SCHEDULED);
        return auction;
    }

    private ProductResponseDTO buildProductResponse(Product product, Auction auction, List<String> imageUrls) {
        ProductResponseDTO responseDTO = productMapper.toDTO(product, auction);
        responseDTO.setImageUrls(imageUrls);
        return responseDTO;
    }
}