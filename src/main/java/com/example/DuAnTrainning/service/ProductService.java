package com.example.DuAnTrainning.service;

import com.example.DuAnTrainning.dto.request.ProductRequestDTO;
import com.example.DuAnTrainning.dto.request.ProductUpdateRequestDTO;
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
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Objects;
import java.util.Set;
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
                .toEntities(savedProduct, uploadedImages);

        productImageRepository.saveAll(images);

        Auction auction = auctionMapper.toEntity(savedProduct, requestDTO);
        Auction savedAuction = auctionRepository.save(auction);

        return productResponseHelper.build(
                savedProduct,
                savedAuction,
                imageUrls
        );
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

    //Khách vãng lai hay người mua có thể xem được các sản phẩm mà người bán đăng bán (chỉ khi ở trạng thái đc duyệt)
    @Transactional(readOnly = true)
    public List<ProductResponseDTO> getPublicProducts() {
        List<Product> products = productRepository
                .findByStatusOrderByCreatedAtDesc(ProductStatus.APPROVED);

        return productResponseHelper.buildAll(products);
    }

    //Chỉnh sửa
    @Transactional
    public ProductResponseDTO updateProduct(Long sellerId, Long productId, ProductUpdateRequestDTO requestDTO) {
        // 1. Kiểm tra tồn tại & Quyền sở hữu (Bỏ existsById dư thừa)
        Product product = findProductAndValidatePermission(sellerId, productId);
        Auction auction = findAuctionAndValidateStatus(productId);

        // 2. Cập nhật Category & Product
        updateCategoryIfChanged(product, requestDTO.getCategoryId());
        productMapper.updateProductFromDto(requestDTO, product);

        // 3. Cập nhật & Validate Auction
        auctionMapper.updateAuctionFromDto(requestDTO, auction);
        auctionValidator.validateAuctionEntity(auction);

        // 4. Xử lý Ảnh
        handleImageUpdates(product, requestDTO);

        // 5. Lưu DB & Trả về Response DTO
        productRepository.save(product);
        auctionRepository.save(auction);
        return productResponseHelper.build(product);
    }

    private Product findProductAndValidatePermission(Long sellerId, Long productId) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ApplicationException(ErrorCode.PRODUCT_NOT_FOUND));

        if (!product.getSeller().getId().equals(sellerId)) {
            throw new ApplicationException(ErrorCode.UNAUTHORIZED_ACCESS);
        }
        return product;
    }

    private Auction findAuctionAndValidateStatus(Long productId) {
        Auction auction = auctionRepository.findByProduct_Id(productId)
                .orElseThrow(() -> new ApplicationException(ErrorCode.AUCTION_NOT_FOUND));

        if (auction.getStatus() != AuctionStatus.PENDING_APPROVAL && auction.getStatus() != AuctionStatus.SCHEDULED ) {
            throw new ApplicationException(ErrorCode.AUCTION_ALREADY_STARTED);
        }
        return auction;
    }

    private void updateCategoryIfChanged(Product product, Long newCategoryId) {
        if (newCategoryId != null && !newCategoryId.equals(product.getCategory().getId())) {
            Category category = categoryRepository.findById(newCategoryId)
                    .orElseThrow(() -> new ApplicationException(ErrorCode.CATEGORY_NOT_FOUND));
            if (!category.isActive()) {
                throw new ApplicationException(ErrorCode.CATEGORY_INACTIVE);
            }
            product.setCategory(category);
        }
    }

// --- XỬ LÝ ẢNH ĐƯỢC TÁCH GỌN GÀNG ---
    private void handleImageUpdates(Product product, ProductUpdateRequestDTO requestDTO) {
        List<ProductImage> existingImages = productImageRepository.findByProductIdOrderByDisplayOrderAsc(product.getId());

        // Loại bỏ phần tử trùng lặp bằng Set (Xử lý case deleteIds = [1, 1, 1])
        Set<Long> deleteIds = (requestDTO.getDeleteImageIds() != null)
                ? Set.copyOf(requestDTO.getDeleteImageIds())
                : Set.of();

        List<MultipartFile> newFiles = (requestDTO.getNewImages() != null)
                ? requestDTO.getNewImages()
                : List.of();

        validateImageCountBounds(existingImages.size(), deleteIds.size(), newFiles.size());

        if (!deleteIds.isEmpty()) {
            deleteOldImages(product.getId(), deleteIds);
        }

        if (!newFiles.isEmpty()) {
            uploadAndSaveNewImages(product, newFiles);
        }

        reindexImageDisplayOrders(product.getId());
    }

    private void validateImageCountBounds(int existingCount, int deleteCount, int newCount) {
        int finalCount = existingCount - deleteCount + newCount;
        if (finalCount < 1) {
            throw new ApplicationException(ErrorCode.IMAGE_REQUIRED);
        }
        if (finalCount > 20) {
            throw new ApplicationException(ErrorCode.TOO_MANY_IMAGES);
        }
    }

    private void deleteOldImages(Long productId, Set<Long> deleteIds) {
        List<ProductImage> imagesToDelete = productImageRepository.findByIdInAndProductId(deleteIds, productId);

        // Kiểm tra số lượng ảnh tìm thấy trong DB có khớp với số lượng ID yêu cầu xóa hay không
        if (imagesToDelete.size() != deleteIds.size()) {
            throw new ApplicationException(ErrorCode.INVALID_IMAGE_FILE);
        }

        List<String> publicIdsToDelete = imagesToDelete.stream()
                .map(ProductImage::getPublicId)
                .filter(Objects::nonNull)
                .toList();

        deleteCloudinaryImagesAfterCommit(publicIdsToDelete);
        productImageRepository.deleteAll(imagesToDelete);
    }

    private void uploadAndSaveNewImages(Product product, List<MultipartFile> newFiles) {
        productImageValidator.validate(newFiles);
        List<CloudinaryService.UploadedImage> uploadedNewImages = cloudinaryService.uploadAll(newFiles);
        deleteCloudinaryImagesWhenTransactionRollsBack(uploadedNewImages);

        List<ProductImage> newProductImages = productImageMapper.toEntities(product, uploadedNewImages);
        productImageRepository.saveAll(newProductImages);
    }

    private void reindexImageDisplayOrders(Long productId) {
        List<ProductImage> remainingImages = productImageRepository.findByProductIdOrderByDisplayOrderAsc(productId);
        for (int i = 0; i < remainingImages.size(); i++) {
            remainingImages.get(i).setDisplayOrder(i);
        }
        productImageRepository.saveAll(remainingImages);
    }

    private void deleteCloudinaryImagesAfterCommit(List<String> publicIds) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                for (String publicId : publicIds) {
                    cloudinaryService.deleteByPublicId(publicId);
                }
            }
        });
    }
    //Xóa sản phẩm
    @Transactional
    public void deleteProduct(Long sellerId, Long productId) {
        Product product = findProductAndValidatePermission(sellerId, productId);

        findAuctionAndValidateStatusForDelete(productId);
        List<ProductImage> images = productImageRepository.findByProductIdOrderByDisplayOrderAsc(productId);
        List<String> publicIdsToDelete = images.stream()
                .map(ProductImage::getPublicId)
                .filter(Objects::nonNull)
                .toList();

        deleteCloudinaryImagesAfterCommit(publicIdsToDelete);
        productImageRepository.deleteByProductId(productId);
        auctionRepository.deleteByProduct_Id(productId);
        productRepository.delete(product);
    }

    //  kiểm tra điều kiện xóa
    private void findAuctionAndValidateStatusForDelete(Long productId) {
        Auction auction = auctionRepository.findByProduct_Id(productId)
                .orElseThrow(() -> new ApplicationException(ErrorCode.AUCTION_NOT_FOUND));

        if (auction.getStatus() == AuctionStatus.RUNNING || auction.getStatus() == AuctionStatus.ENDED) {
            throw new ApplicationException(ErrorCode.CANNOT_DELETE_ACTIVE_AUCTION);
        }
    }
    //Hủy đăng sản phẩm
    @Transactional
    public ProductResponseDTO cancelAuction(Long sellerId, Long productId) {
        Product product = findProductAndValidatePermission(sellerId, productId);
        Auction auction = findAuctionAndValidateStatus(productId);

        auction.setStatus(AuctionStatus.CANCELLED);
        auctionRepository.save(auction);

        // 4. Trả về DTO
        return productResponseHelper.build(product);
    }

}
