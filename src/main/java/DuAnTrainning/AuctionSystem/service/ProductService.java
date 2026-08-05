package DuAnTrainning.AuctionSystem.service;

import DuAnTrainning.AuctionSystem.dto.request.ProductRejectRequestDTO;
import DuAnTrainning.AuctionSystem.dto.request.ProductRequestDTO;
import DuAnTrainning.AuctionSystem.dto.request.ProductUpdateRequestDTO;
import DuAnTrainning.AuctionSystem.dto.response.ProductResponseDTO;
import DuAnTrainning.AuctionSystem.entity.*;
import DuAnTrainning.AuctionSystem.repository.*;
import DuAnTrainning.AuctionSystem.enums.AuctionStatus;
import DuAnTrainning.AuctionSystem.enums.ProductStatus;
import DuAnTrainning.AuctionSystem.exception.ApplicationException;
import DuAnTrainning.AuctionSystem.exception.ErrorCode;
import DuAnTrainning.AuctionSystem.mapper.AuctionMapper;
import DuAnTrainning.AuctionSystem.mapper.ProductImageMapper;
import DuAnTrainning.AuctionSystem.mapper.ProductMapper;
import DuAnTrainning.AuctionSystem.service.helper.ProductAuctionLookupHelper;
import DuAnTrainning.AuctionSystem.service.helper.ProductResponseHelper;
import DuAnTrainning.AuctionSystem.validator.AuctionValidator;
import DuAnTrainning.AuctionSystem.validator.ProductImageValidator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Set;

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
    private final ProductAuctionLookupHelper productAuctionLookupHelper;

    //Người bán tạo sản phẩm đăng bán đấu giá

    @Transactional
    public ProductResponseDTO createProduct(Long sellerId, ProductRequestDTO requestDTO) {
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
        // 1. Upload Cloudinary
        List<CloudinaryService.UploadedImage> uploadedImages = cloudinaryService.uploadAll(requestDTO.getImages());
        deleteCloudinaryImagesWhenTransactionRollsBack(uploadedImages);
        // 2. Lưu Product
        Product savedProduct = productRepository.save(product);
        // 3. Map & Lưu ProductImages (Hứng lấy savedImages có chứa ID từ DB)
        List<ProductImage> images = productImageMapper.toEntities(savedProduct, uploadedImages);
        List<ProductImage> savedImages = productImageRepository.saveAll(images);
        // 4. Map & Lưu Auction
        Auction auction = auctionMapper.toEntity(savedProduct, requestDTO);
        Auction savedAuction = auctionRepository.save(auction);
        // 5. TRUYỀN THẲNG 'savedImages' VÀO HELPER (Bỏ đoạn map imageUrls thủ công)
        return productResponseHelper.build(savedProduct, savedAuction, savedImages);
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

    @Transactional
    public ProductResponseDTO relistAuction(Long sellerId, Long auctionId) {
        Auction auction = auctionRepository.findById(auctionId)
                .orElseThrow(() -> new ApplicationException(ErrorCode.AUCTION_NOT_FOUND));

        auctionValidator.validateRelist(sellerId, auction);

        // 2. Reset 30 ngày RUNNING mới
        LocalDateTime now = LocalDateTime.now();
        auction.setStatus(AuctionStatus.RUNNING);
        auction.setStartTime(now);
        auction.setEndTime(now.plusDays(30));

        return productResponseHelper.build(auction.getProduct());
    }

    //Admin
    // 1. Lấy danh sách tất cả bài đăng đang chờ Admin duyệt (ProductStatus = PENDING)
    @Transactional(readOnly = true)
    public List<ProductResponseDTO> getPendingProducts() {
        List<Product> products = productRepository.findByStatusOrderByCreatedAtDesc(ProductStatus.PENDING);
        return productResponseHelper.buildAll(products);
    }
    // 2. Admin Duyệt Bài Đăng (APPROVED)
    @Transactional
    public ProductResponseDTO approveProduct(Long productId) {
        // Dùng từ khóa var và gọi Helper gộp gọn gàng
        var holder = productAuctionLookupHelper.findPendingProductAndAuction(productId);
        Product product = holder.product();
        Auction auction = holder.auction();
        LocalDateTime now = LocalDateTime.now();
        // Check 1: Nếu thời gian kết thúc đã trôi qua trước khi Admin kịp duyệt
        if (auction.getEndTime().isBefore(now)) {
            throw new ApplicationException(ErrorCode.AUCTION_EXPIRED_BEFORE_APPROVAL);
        }
        // Check 2: Nếu startTime đã trôi qua trong lúc chờ duyệt -> Kích hoạt Running ngay!
        if (auction.getStartTime().isBefore(now) || auction.getStartTime().isEqual(now)) {
            auction.setStatus(AuctionStatus.RUNNING);
        } else {
            auction.setStatus(AuctionStatus.SCHEDULED);
        }
        product.setStatus(ProductStatus.APPROVED);
        List<ProductImage> images = productImageRepository.findByProductIdOrderByDisplayOrderAsc(productId);
        return productResponseHelper.build(product, auction, images);
    }

    // 3. Admin Từ Chối Bài Đăng (REJECTED)
    @Transactional
    public ProductResponseDTO rejectProduct(Long productId, ProductRejectRequestDTO rejectDTO) {
        var holder = productAuctionLookupHelper.findPendingProductAndAuction(productId);
        Product product = holder.product();
        Auction auction = holder.auction();
        product.setStatus(ProductStatus.REJECTED);
        product.setRejectionReason(rejectDTO.getRejectionReason()); // Lưu lý do từ chối
        auction.setStatus(AuctionStatus.CANCELLED);
        List<ProductImage> images = productImageRepository.findByProductIdOrderByDisplayOrderAsc(productId);
        return productResponseHelper.build(product, auction, images);
    }

}
