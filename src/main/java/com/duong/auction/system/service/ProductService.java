package com.duong.auction.system.service;

import com.duong.auction.system.dto.request.ProductRejectRequestDTO;
import com.duong.auction.system.dto.request.ProductRequestDTO;
import com.duong.auction.system.dto.request.ProductUpdateRequestDTO;
import com.duong.auction.system.dto.response.ProductResponseDTO;
import com.duong.auction.system.entity.*;
import com.duong.auction.system.repository.*;
import com.duong.auction.system.enums.AuctionStatus;
import com.duong.auction.system.enums.ProductStatus;
import com.duong.auction.system.exception.ApplicationException;
import com.duong.auction.system.exception.ErrorCode;
import com.duong.auction.system.mapper.AuctionMapper;
import com.duong.auction.system.mapper.ProductImageMapper;
import com.duong.auction.system.mapper.ProductMapper;
import com.duong.auction.system.service.helper.ProductAuctionLookupHelper;
import com.duong.auction.system.service.helper.ProductResponseHelper;
import com.duong.auction.system.validator.AuctionValidator;
import com.duong.auction.system.validator.ProductImageValidator;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.time.Clock;
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
    private final Clock clock;

    private User getAuthenticatedUser() {
        org.springframework.security.core.Authentication auth = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof com.duong.auction.system.security.UserCustomDetails userCustomDetails) {
            return userCustomDetails.getUser();
        }
        String email = (auth != null) ? auth.getName() : null;
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ApplicationException(ErrorCode.USER_NOT_FOUND));
    }

    // =========================================================================
    // 1. NGƯỜI BÁN (SELLER) TẠO SẢN PHẨM MỚI KÈM CẤU HÌNH ĐẤU GIÁ
    // =========================================================================

    @Transactional
    public ProductResponseDTO createProduct(ProductRequestDTO requestDTO) {
        // 1. Lấy thông tin chính chủ Người Bán đang đăng nhập từ SecurityContext
        User seller = getAuthenticatedUser();

        // 2. Kiểm tra Danh Mục sản phẩm (Category) tồn tại và có đang hoạt động (isActive) không
        Category category = categoryRepository.findById(requestDTO.getCategoryId())
                .orElseThrow(() -> new ApplicationException(ErrorCode.CATEGORY_NOT_FOUND));
        if (!category.isActive()) {
            throw new ApplicationException(ErrorCode.CATEGORY_INACTIVE);
        }

        // 3. Validate số lượng file ảnh (1 - 20 ảnh) và quy tắc đấu giá (ENGLISH / RESERVE / BUY_NOW)
        productImageValidator.validate(requestDTO.getImages());
        auctionValidator.validate(requestDTO);

        // 4. Map DTO sang Entity Product, gán chính chủ Seller, Category và đặt trạng thái PENDING (Chờ duyệt)
        Product product = productMapper.toEntity(requestDTO);
        product.setSeller(seller);
        product.setCategory(category);
        product.setStatus(ProductStatus.PENDING);

        // 5. Upload danh sách ảnh lên Cloudinary kèm dọn dẹp tự động nếu DB Transaction bị Rollback
        List<CloudinaryService.UploadedImage> uploadedImages = cloudinaryService.uploadAll(requestDTO.getImages());
        deleteCloudinaryImagesWhenTransactionRollsBack(uploadedImages);

        // 6. Lưu bản ghi Product xuống Database
        Product savedProduct = productRepository.save(product);

        // 7. Map và Lưu danh sách hình ảnh ProductImage (Hứng lấy danh sách có ID từ DB)
        List<ProductImage> images = productImageMapper.toEntities(savedProduct, uploadedImages);
        List<ProductImage> savedImages = productImageRepository.saveAll(images);

        // 8. Map và Lưu cấu hình phiên đấu giá Auction tương ứng
        Auction auction = auctionMapper.toEntity(savedProduct, requestDTO);
        Auction savedAuction = auctionRepository.save(auction);

        // 9. Gọi Helper đóng gói dữ liệu phản hồi DTO trả về cho Người Bán
        return productResponseHelper.build(savedProduct, savedAuction, savedImages);
    }

    // Cơ chế dọn dẹp ảnh rác trên Cloudinary nếu Transaction lưu DB gặp lỗi Rollback
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

    // =========================================================================
    // 2. TRUY VẤN DANH SÁCH VÀ CHI TIẾT SẢN PHẨM
    // =========================================================================

    // Lấy danh sách sản phẩm do chính Người Bán đang đăng nhập tạo ra
    @Transactional(readOnly = true)
    public List<ProductResponseDTO> getProductsBySellerId() {
        User seller = getAuthenticatedUser();
        List<Product> products = productRepository.findProductsBySellerIdSorted(seller.getId());
        return productResponseHelper.buildAll(products);
    }

    // Lấy chi tiết 1 sản phẩm theo productId
    @Cacheable(value = "auctions", key = "#productId")
    @Transactional(readOnly = true)
    public ProductResponseDTO getProductWithAuctionById(Long productId) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ApplicationException(ErrorCode.PRODUCT_NOT_FOUND));
        return productResponseHelper.build(product);
    }

    // Khách hàng / Bidder xem các sản phẩm công khai trên sàn (Chỉ lấy sản phẩm đã được Admin APPROVE, sắp xếp ưu tiên)
    @Transactional(readOnly = true)
    public List<ProductResponseDTO> getPublicProducts() {
        List<Product> products = productRepository.findAllApprovedProductsSorted();
        return productResponseHelper.buildAll(products);
    }

    // =========================================================================
    // 3. CHỈNH SỬA SẢN PHẨM (KHI CHƯA LÊN SÀN HOẶC CHƯA BẮT ĐẦU)
    // =========================================================================
    @CacheEvict(value = "auctions", key = "#productId")
    @Transactional
    public ProductResponseDTO updateProduct(Long productId, ProductUpdateRequestDTO requestDTO) {
        // 1. Kiểm tra sản phẩm tồn tại và chính chủ Người Bán đang đăng nhập
        User seller = getAuthenticatedUser();
        Product product = findProductAndValidatePermission(seller.getId(), productId);
        Auction auction = findAuctionAndValidateStatus(productId);

        // 2. Cập nhật Category (nếu thay đổi) và thông tin cơ bản của Product
        updateCategoryIfChanged(product, requestDTO.getCategoryId());
        productMapper.updateProductFromDto(requestDTO, product);

        // 3. Cập nhật và Validate lại cấu hình phiên Auction
        auctionMapper.updateAuctionFromDto(requestDTO, auction);
        auctionValidator.validateAuctionEntity(auction);

        // 4. Xử lý thêm mới/xóa bớt hình ảnh sản phẩm
        handleImageUpdates(product, requestDTO);

        // 5. Lưu thông tin cập nhật xuống Database
        productRepository.save(product);
        auctionRepository.save(auction);

        // 6. Đóng gói DTO trả về cho Người Bán
        return productResponseHelper.build(product);
    }

    // Helper nội bộ: Tìm sản phẩm và kiểm tra quyền sở hữu của Người Bán
    private Product findProductAndValidatePermission(Long sellerId, Long productId) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ApplicationException(ErrorCode.PRODUCT_NOT_FOUND));
        if (!product.getSeller().getId().equals(sellerId)) {
            throw new ApplicationException(ErrorCode.UNAUTHORIZED_ACCESS);
        }
        return product;
    }

    // Helper nội bộ: Tìm phiên Auction và kiểm tra trạng thái chỉ cho sửa khi đang PENDING_APPROVAL hoặc SCHEDULED
    private Auction findAuctionAndValidateStatus(Long productId) {
        Auction auction = auctionRepository.findByProduct_Id(productId)
                .orElseThrow(() -> new ApplicationException(ErrorCode.AUCTION_NOT_FOUND));
        if (auction.getStatus() != AuctionStatus.PENDING_APPROVAL && auction.getStatus() != AuctionStatus.SCHEDULED) {
            throw new ApplicationException(ErrorCode.AUCTION_ALREADY_STARTED);
        }
        return auction;
    }

    // Helper nội bộ: Cập nhật Danh Mục mới cho sản phẩm nếu có thay đổi
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

    // Helper nội bộ: Xử lý quy trình cập nhật ảnh (Xóa ảnh cũ chọn lọc + Upload ảnh mới bổ sung)
    private void handleImageUpdates(Product product, ProductUpdateRequestDTO requestDTO) {
        // 1. Lấy danh sách ảnh hiện tại của sản phẩm
        List<ProductImage> existingImages = productImageRepository.findByProductIdOrderByDisplayOrderAsc(product.getId());

        // 2. Chuyển danh sách ID ảnh cần xóa thành Set để khử trùng lặp
        Set<Long> deleteIds = (requestDTO.getDeleteImageIds() != null)
                ? Set.copyOf(requestDTO.getDeleteImageIds())
                : Set.of();

        List<MultipartFile> newFiles = (requestDTO.getNewImages() != null)
                ? requestDTO.getNewImages()
                : List.of();

        // 3. Validate tổng số lượng ảnh sau khi sửa phải nằm trong khoảng cho phép [1, 20]
        validateImageCountBounds(existingImages.size(), deleteIds.size(), newFiles.size());

        // 4. Nếu có yêu cầu xóa ảnh -> Thực hiện xóa trong DB và dọn dẹp trên Cloudinary
        if (!deleteIds.isEmpty()) {
            deleteOldImages(product.getId(), deleteIds);
        }

        // 5. Nếu có ảnh mới bổ sung -> Upload lên Cloudinary và lưu vào DB
        if (!newFiles.isEmpty()) {
            uploadAndSaveNewImages(product, newFiles);
        }

        // 6. Đánh lại thứ tự hiển thị displayOrder liên tục từ 0 cho các ảnh còn lại
        reindexImageDisplayOrders(product.getId());
    }

    // Helper nội bộ: Kiểm tra giới hạn số lượng ảnh còn lại (Từ 1 đến 20 ảnh)
    private void validateImageCountBounds(int existingCount, int deleteCount, int newCount) {
        int finalCount = existingCount - deleteCount + newCount;
        if (finalCount < 1) {
            throw new ApplicationException(ErrorCode.IMAGE_REQUIRED);
        }
        if (finalCount > 20) {
            throw new ApplicationException(ErrorCode.TOO_MANY_IMAGES);
        }
    }

    // Helper nội bộ: Xóa các ảnh cũ được chỉ định (Xóa DB trước, xóa Cloudinary sau khi Commit thành công)
    private void deleteOldImages(Long productId, Set<Long> deleteIds) {
        List<ProductImage> imagesToDelete = productImageRepository.findByIdInAndProductId(deleteIds, productId);
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

    // Helper nội bộ: Upload các ảnh mới lên Cloudinary và tạo bản ghi ProductImage
    private void uploadAndSaveNewImages(Product product, List<MultipartFile> newFiles) {
        productImageValidator.validate(newFiles);
        List<CloudinaryService.UploadedImage> uploadedNewImages = cloudinaryService.uploadAll(newFiles);
        deleteCloudinaryImagesWhenTransactionRollsBack(uploadedNewImages);

        List<ProductImage> newProductImages = productImageMapper.toEntities(product, uploadedNewImages);
        productImageRepository.saveAll(newProductImages);
    }

    // Helper nội bộ: Sắp xếp lại thứ tự hiển thị displayOrder tăng dần từ 0
    private void reindexImageDisplayOrders(Long productId) {
        List<ProductImage> remainingImages = productImageRepository.findByProductIdOrderByDisplayOrderAsc(productId);
        for (int i = 0; i < remainingImages.size(); i++) {
            remainingImages.get(i).setDisplayOrder(i);
        }
        productImageRepository.saveAll(remainingImages);
    }

    // Helper nội bộ: Đăng ký xóa file ảnh trên Cloudinary SAU KHI DB Transaction đã Commit thành công
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

    // =========================================================================
    // 4. NGƯỜI BÁN XÓA VÀ HỦY SẢN PHẨM
    // =========================================================================

    // Người bán XÓA vĩnh viễn sản phẩm (Chỉ khi phiên chưa diễn ra hoặc chưa kết thúc thành công)
    @Transactional
    public void deleteProduct(Long productId) {
        // 1. Kiểm tra chính chủ Người Bán đang đăng nhập
        User seller = getAuthenticatedUser();
        Product product = findProductAndValidatePermission(seller.getId(), productId);
        findAuctionAndValidateStatusForDelete(productId);

        // 2. Thu thập tất cả publicId ảnh để dọn dẹp trên Cloudinary
        List<ProductImage> images = productImageRepository.findByProductIdOrderByDisplayOrderAsc(productId);
        List<String> publicIdsToDelete = images.stream()
                .map(ProductImage::getPublicId)
                .filter(Objects::nonNull)
                .toList();

        // 3. Đăng ký xóa Cloudinary sau khi Commit, đồng thời xóa dữ liệu trong DB
        deleteCloudinaryImagesAfterCommit(publicIdsToDelete);
        productImageRepository.deleteByProductId(productId);
        auctionRepository.deleteByProduct_Id(productId);
        productRepository.delete(product);
    }

    // Helper nội bộ: Kiểm tra điều kiện trạng thái không được phép xóa (Không thể xóa khi đang RUNNING hoặc ENDED)
    private void findAuctionAndValidateStatusForDelete(Long productId) {
        Auction auction = auctionRepository.findByProduct_Id(productId)
                .orElseThrow(() -> new ApplicationException(ErrorCode.AUCTION_NOT_FOUND));

        if (auction.getStatus() == AuctionStatus.RUNNING || auction.getStatus() == AuctionStatus.ENDED) {
            throw new ApplicationException(ErrorCode.CANNOT_DELETE_ACTIVE_AUCTION);
        }
    }

    // Người bán CHỦ ĐỘNG HỦY bài đăng (Chỉ khi chưa có ai đặt giá)
    @CacheEvict(value = "auctions", key = "#productId")
    @Transactional
    public ProductResponseDTO cancelAuction(Long productId) {
        // 1. Kiểm tra chính chủ và trạng thái hợp lệ
        User seller = getAuthenticatedUser();
        Product product = findProductAndValidatePermission(seller.getId(), productId);
        Auction auction = findAuctionAndValidateStatus(productId);

        // 2. Chuyển trạng thái phiên sang CANCELLED
        auction.setStatus(AuctionStatus.CANCELLED);
        auctionRepository.save(auction);

        // 3. Trả về DTO
        return productResponseHelper.build(product);
    }

    // =========================================================================
    // 5. NGƯỜI BÁN ĐĂNG LẠI (RELIST) SẢN PHẨM HẾT HẠN 30 NGÀY (EXPIRED)
    // =========================================================================

    @Transactional
    public ProductResponseDTO relistAuction(Long auctionId) {
        User seller = getAuthenticatedUser();
        // 1. Tìm phiên đấu giá bị hết hạn theo auctionId
        Auction auction = auctionRepository.findById(auctionId)
                .orElseThrow(() -> new ApplicationException(ErrorCode.AUCTION_NOT_FOUND));

        // 2. Validate quy tắc Đăng lại (bắt buộc chính chủ Seller và trạng thái phiên phải là EXPIRED)
        auctionValidator.validateRelist(seller.getId(), auction);

        // 3. Khôi phục trạng thái RUNNING công khai và reset 30 ngày hiển thị mới (startTime = NOW(), endTime = NOW() + 30 days)
        LocalDateTime now = LocalDateTime.now(clock);
        auction.setStatus(AuctionStatus.RUNNING);
        auction.setStartTime(now);
        auction.setEndTime(now.plusDays(30));

        // 4. Đóng gói DTO trả về cho Người Bán
        return productResponseHelper.build(auction.getProduct());
    }

    // =========================================================================
    // 6. QUẢN TRỊ VIÊN (ADMIN) KIỂM DUYỆT BÀI ĐĂNG
    // =========================================================================

    // Admin lấy danh sách các bài đăng đang chờ duyệt (ProductStatus = PENDING)
    @Transactional(readOnly = true)
    public List<ProductResponseDTO> getPendingProducts() {
        List<Product> products = productRepository.findByStatusOrderByCreatedAtDesc(ProductStatus.PENDING);
        return productResponseHelper.buildAll(products);
    }

    // Admin Chấp Thuận xuất bản bài đăng (ProductStatus = APPROVED)
    @CacheEvict(value = "auctions", key = "#productId")
    @Transactional
    public ProductResponseDTO approveProduct(Long productId) {
        // 1. Lấy bộ đôi Product và Auction đang ở trạng thái PENDING
        var holder = productAuctionLookupHelper.findPendingProductAndAuction(productId);
        Product product = holder.product();
        Auction auction = holder.auction();
        LocalDateTime now = LocalDateTime.now(clock);
        // 2. Kiểm tra nếu thời gian kết thúc đã trôi qua trước khi Admin kịp duyệt -> Báo lỗi
        if (auction.getEndTime().isBefore(now)) {
            throw new ApplicationException(ErrorCode.AUCTION_EXPIRED_BEFORE_APPROVAL);
        }

        // 3. Phân nhánh trạng thái: Nếu đã đến/qua startTime hoặc loại MUA_NGAY -> Kích hoạt RUNNING ngay! Ngược lại -> SCHEDULED
        if (auction.getStartTime().isBefore(now) || auction.getStartTime().isEqual(now)) {
            auction.setStatus(AuctionStatus.RUNNING);
        } else {
            auction.setStatus(AuctionStatus.SCHEDULED);
        }

        // 4. Chuyển trạng thái sản phẩm sang APPROVED
        product.setStatus(ProductStatus.APPROVED);

        // 5. Lấy hình ảnh sản phẩm và trả về DTO cho Admin
        List<ProductImage> images = productImageRepository.findByProductIdOrderByDisplayOrderAsc(productId);
        return productResponseHelper.build(product, auction, images);
    }

    // Admin Từ Chối xuất bản bài đăng (ProductStatus = REJECTED)
    @CacheEvict(value = "auctions", key = "#productId")
    @Transactional
    public ProductResponseDTO rejectProduct(Long productId, ProductRejectRequestDTO rejectDTO) {
        // 1. Lấy bộ đôi Product và Auction đang PENDING
        var holder = productAuctionLookupHelper.findPendingProductAndAuction(productId);
        Product product = holder.product();
        Auction auction = holder.auction();

        // 2. Đổi ProductStatus sang REJECTED và lưu lý do từ chối
        product.setStatus(ProductStatus.REJECTED);
        product.setRejectionReason(rejectDTO.getRejectionReason());

        // 3. Đổi AuctionStatus sang CANCELLED
        auction.setStatus(AuctionStatus.CANCELLED);

        // 4. Lấy hình ảnh và trả về DTO cho Admin
        List<ProductImage> images = productImageRepository.findByProductIdOrderByDisplayOrderAsc(productId);
        return productResponseHelper.build(product, auction, images);
    }

}
