package com.example.DuAnTrainning.service;

import com.example.DuAnTrainning.dto.request.ProductRequestDTO;
import com.example.DuAnTrainning.dto.response.ProductResponseDTO;
import com.example.DuAnTrainning.entity.*;
import com.example.DuAnTrainning.enums.AuctionStatus;
import com.example.DuAnTrainning.enums.ProductStatus;
import com.example.DuAnTrainning.exception.ApplicationException;
import com.example.DuAnTrainning.exception.ErrorCode;
import com.example.DuAnTrainning.mapper.AuctionMapper;
import com.example.DuAnTrainning.mapper.ProductImageMapper;
import com.example.DuAnTrainning.mapper.ProductMapper;
import com.example.DuAnTrainning.repository.*;
import com.example.DuAnTrainning.service.helper.ProductResponseHelper;
import com.example.DuAnTrainning.validator.AuctionValidator;
import com.example.DuAnTrainning.validator.ProductImageValidator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

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
    private final CloudinaryService cloudinaryService;
    private final ProductResponseHelper productResponseHelper;
    private final AuctionMapper auctionMapper;
    private final ProductImageMapper productImageMapper;

    //Người bán tạo sản phẩm đăng bán đấu giá

    @Transactional
    public ProductResponseDTO createProduct(Long sellerId,ProductRequestDTO requestDTO) {
        User seller = userRepository.findById(sellerId)
                .orElseThrow(() -> new ApplicationException(ErrorCode.USER_NOT_FOUND));
        Category category = categoryRepository.findById(requestDTO.getCategoryId())
                .orElseThrow(() -> new ApplicationException(ErrorCode.CATEGORY_NOT_FOUND));

        if (!category.isActive()) {
            throw new ApplicationException(ErrorCode.CATEGORY_INACTIVE);
        }

        productImageValidator.validate(requestDTO.getImages());
        auctionValidator.validate(requestDTO);

        Product product = productMapper.toEntity(requestDTO);
        product.setSeller(seller);
        product.setCategory(category);
        product.setStatus(ProductStatus.PENDING);

        List<CloudinaryService.UploadedImage> uploadedImages = cloudinaryService.uploadAll(requestDTO.getImages());
        deleteCloudinaryImagesWhenTransactionRollsBack(uploadedImages);

        Product savedProduct = productRepository.save(product);

        List<String> imageUrls = uploadedImages.stream()
                .map(CloudinaryService.UploadedImage::secureUrl)
                .collect(Collectors.toList());
        List<ProductImage> images = productImageMapper
                .toEntities(savedProduct, imageUrls);

        productImageRepository.saveAll(images);

        Auction auction = auctionMapper.toEntity(savedProduct, requestDTO);
        Auction savedAuction = auctionRepository.save(auction);

        return productResponseHelper.build(
                savedProduct,
                savedAuction,
                imageUrls
        );
    }

    @Transactional(readOnly = true)
    public List<ProductResponseDTO> getProductsBySellerId(Long sellerId) {
        if (!userRepository.existsById(sellerId)) {
            throw new ApplicationException(ErrorCode.USER_NOT_FOUND);
        }

        List<Product> products = productRepository
                .findBySeller_IdOrderByCreatedAtDesc(sellerId);

        return productResponseHelper.buildAll(products);
    }

    @Transactional(readOnly = true)
    public ProductResponseDTO getProductWithAuctionById(Long productId) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() ->
                        new ApplicationException(ErrorCode.PRODUCT_NOT_FOUND)
                );

        return productResponseHelper.build(product);
    }

    private void deleteCloudinaryImagesWhenTransactionRollsBack(
            List<CloudinaryService.UploadedImage> uploadedImages) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status != STATUS_COMMITTED) {
                    cloudinaryService.deleteAll(uploadedImages);
                }
            }
        });
    }


    //Khách vãng lai hay người mua có thể xem được các sản phẩm mà người bán đăng bán (chỉ khi ở trạng thái đc duyệt)
    @Transactional(readOnly = true)
    public List<ProductResponseDTO> getPublicProducts() {
        List<Product> products = productRepository
                .findByStatusOrderByCreatedAtDesc(ProductStatus.APPROVED);

        return productResponseHelper.buildAll(products);
    }
}
