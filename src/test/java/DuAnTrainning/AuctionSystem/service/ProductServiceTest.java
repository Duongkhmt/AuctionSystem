package DuAnTrainning.AuctionSystem.service;

import DuAnTrainning.AuctionSystem.dto.request.ProductRejectRequestDTO;
import DuAnTrainning.AuctionSystem.dto.request.ProductRequestDTO;
import DuAnTrainning.AuctionSystem.dto.response.ProductResponseDTO;
import DuAnTrainning.AuctionSystem.entity.*;
import DuAnTrainning.AuctionSystem.enums.AuctionStatus;
import DuAnTrainning.AuctionSystem.enums.AuctionType;
import DuAnTrainning.AuctionSystem.enums.ProductStatus;
import DuAnTrainning.AuctionSystem.exception.ApplicationException;
import DuAnTrainning.AuctionSystem.exception.ErrorCode;
import DuAnTrainning.AuctionSystem.mapper.AuctionMapper;
import DuAnTrainning.AuctionSystem.mapper.ProductImageMapper;
import DuAnTrainning.AuctionSystem.mapper.ProductMapper;
import DuAnTrainning.AuctionSystem.repository.*;
import DuAnTrainning.AuctionSystem.service.helper.ProductAuctionLookupHelper;
import DuAnTrainning.AuctionSystem.service.helper.ProductAuctionLookupHelper.PendingProductAuctionHolder;
import DuAnTrainning.AuctionSystem.service.helper.ProductResponseHelper;
import DuAnTrainning.AuctionSystem.validator.AuctionValidator;
import DuAnTrainning.AuctionSystem.validator.ProductImageValidator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Unit Test Cho Class ProductService")
class ProductServiceTest {

    @Mock
    private ProductRepository productRepository;

    @Mock
    private ProductImageRepository productImageRepository;

    @Mock
    private AuctionRepository auctionRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private ProductMapper productMapper;

    @Mock
    private AuctionValidator auctionValidator;

    @Mock
    private ProductImageValidator productImageValidator;

    @Mock
    private CloudinaryService cloudinaryService;

    @Mock
    private ProductResponseHelper productResponseHelper;

    @Mock
    private AuctionMapper auctionMapper;

    @Mock
    private ProductImageMapper productImageMapper;

    @Mock
    private ProductAuctionLookupHelper productAuctionLookupHelper;

    @InjectMocks
    private ProductService productService;

    private User sampleSeller;
    private Category sampleCategory;
    private Product sampleProduct;
    private Auction sampleAuction;

    @BeforeEach
    void setUp() {
        sampleSeller = new User();
        sampleSeller.setId(10L);
        sampleSeller.setEmail("seller@example.com");

        sampleCategory = new Category();
        sampleCategory.setId(5L);
        sampleCategory.setName("Điện thoại");
        sampleCategory.setActive(true);

        sampleProduct = new Product();
        sampleProduct.setId(100L);
        sampleProduct.setTitle("iPhone 15 Pro Max");
        sampleProduct.setSeller(sampleSeller);
        sampleProduct.setCategory(sampleCategory);
        sampleProduct.setStatus(ProductStatus.PENDING);

        sampleAuction = new Auction();
        sampleAuction.setId(50L);
        sampleAuction.setProduct(sampleProduct);
        sampleAuction.setStatus(AuctionStatus.PENDING_APPROVAL);
        sampleAuction.setStartTime(LocalDateTime.now().minusHours(1));
        sampleAuction.setEndTime(LocalDateTime.now().plusDays(3));
    }

    @AfterEach
    void tearDown() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    // =========================================================================
    // 1. UNIT TEST CHO PHƯƠNG THỨC createProduct()
    // =========================================================================
    @Nested
    @DisplayName("Nghiệp vụ Tạo Sản Phẩm (createProduct)")
    class CreateProductTests {

        @Test
        @DisplayName("Tạo sản phẩm thất bại - Người bán không tồn tại")
        void createProduct_UserNotFound_ShouldThrowException() {
            // Given
            Long sellerId = 99L;
            ProductRequestDTO requestDTO = new ProductRequestDTO();
            requestDTO.setCategoryId(5L);

            given(userRepository.findById(sellerId)).willReturn(Optional.empty());

            // When & Then
            assertThatThrownBy(() -> productService.createProduct(sellerId, requestDTO))
                    .isInstanceOf(ApplicationException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.USER_NOT_FOUND);

            then(categoryRepository).should(never()).findById(any());
            then(cloudinaryService).should(never()).uploadAll(any());
        }

        @Test
        @DisplayName("Tạo sản phẩm thất bại - Danh mục không tồn tại")
        void createProduct_CategoryNotFound_ShouldThrowException() {
            // Given
            Long sellerId = 10L;
            ProductRequestDTO requestDTO = new ProductRequestDTO();
            requestDTO.setCategoryId(999L);

            given(userRepository.findById(sellerId)).willReturn(Optional.of(sampleSeller));
            given(categoryRepository.findById(999L)).willReturn(Optional.empty());

            // When & Then
            assertThatThrownBy(() -> productService.createProduct(sellerId, requestDTO))
                    .isInstanceOf(ApplicationException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.CATEGORY_NOT_FOUND);

            then(productImageValidator).should(never()).validate(any());
        }

        @Test
        @DisplayName("Tạo sản phẩm thất bại - Danh mục không còn hoạt động (Category Inactive)")
        void createProduct_CategoryInactive_ShouldThrowException() {
            // Given
            Long sellerId = 10L;
            ProductRequestDTO requestDTO = new ProductRequestDTO();
            requestDTO.setCategoryId(5L);

            Category inactiveCategory = new Category();
            inactiveCategory.setId(5L);
            inactiveCategory.setActive(false);

            given(userRepository.findById(sellerId)).willReturn(Optional.of(sampleSeller));
            given(categoryRepository.findById(5L)).willReturn(Optional.of(inactiveCategory));

            // When & Then
            assertThatThrownBy(() -> productService.createProduct(sellerId, requestDTO))
                    .isInstanceOf(ApplicationException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.CATEGORY_INACTIVE);

            then(productImageValidator).should(never()).validate(any());
        }

        @Test
        @DisplayName("Tạo sản phẩm thành công - Lưu DB và trả về DTO")
        void createProduct_Success_ShouldReturnProductResponseDTO() {
            // Given
            TransactionSynchronizationManager.initSynchronization();

            Long sellerId = 10L;
            ProductRequestDTO requestDTO = new ProductRequestDTO();
            requestDTO.setCategoryId(5L);
            requestDTO.setTitle("iPhone 15 Pro Max");
            requestDTO.setAuctionType(AuctionType.ENGLISH);
            requestDTO.setStartPrice(BigDecimal.valueOf(1000000));
            requestDTO.setBidStep(BigDecimal.valueOf(100000));
            requestDTO.setStartTime(LocalDateTime.now().plusHours(1));
            requestDTO.setEndTime(LocalDateTime.now().plusDays(2));

            MultipartFile mockFile = mock(MultipartFile.class);
            requestDTO.setImages(List.of(mockFile));

            given(userRepository.findById(sellerId)).willReturn(Optional.of(sampleSeller));
            given(categoryRepository.findById(5L)).willReturn(Optional.of(sampleCategory));

            given(productMapper.toEntity(requestDTO)).willReturn(sampleProduct);
            given(productRepository.save(sampleProduct)).willReturn(sampleProduct);

            CloudinaryService.UploadedImage uploadedImage = new CloudinaryService.UploadedImage("http://image.url", "public_id");
            given(cloudinaryService.uploadAll(requestDTO.getImages())).willReturn(List.of(uploadedImage));

            ProductImage productImage = new ProductImage();
            given(productImageMapper.toEntities(sampleProduct, List.of(uploadedImage))).willReturn(List.of(productImage));
            given(productImageRepository.saveAll(any())).willReturn(List.of(productImage));

            given(auctionMapper.toEntity(sampleProduct, requestDTO)).willReturn(sampleAuction);
            given(auctionRepository.save(sampleAuction)).willReturn(sampleAuction);

            ProductResponseDTO expectedResponse = mock(ProductResponseDTO.class);
            given(productResponseHelper.build(sampleProduct, sampleAuction, List.of(productImage)))
                    .willReturn(expectedResponse);

            // When
            ProductResponseDTO actualResponse = productService.createProduct(sellerId, requestDTO);

            // Then
            assertThat(actualResponse).isEqualTo(expectedResponse);
            assertThat(sampleProduct.getStatus()).isEqualTo(ProductStatus.PENDING);

            then(productImageValidator).should(times(1)).validate(requestDTO.getImages());
            then(auctionValidator).should(times(1)).validate(requestDTO);
            then(productRepository).should(times(1)).save(sampleProduct);
            then(auctionRepository).should(times(1)).save(sampleAuction);
        }
    }

    // =========================================================================
    // 2. UNIT TEST CHO CÁC PHƯƠNG THỨC TRUY VẤN SẢN PHẨM
    // =========================================================================
    @Nested
    @DisplayName("Nghiệp vụ Truy Vấn Sản Phẩm (getProducts)")
    class GetProductTests {

        @Test
        @DisplayName("Lấy danh sách sản phẩm theo Người bán thất bại - User không tồn tại")
        void getProductsBySellerId_UserNotFound_ShouldThrowException() {
            // Given
            Long sellerId = 99L;
            given(userRepository.existsById(sellerId)).willReturn(false);

            // When & Then
            assertThatThrownBy(() -> productService.getProductsBySellerId(sellerId))
                    .isInstanceOf(ApplicationException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.USER_NOT_FOUND);

            then(productRepository).should(never()).findBySeller_IdOrderByCreatedAtDesc(any());
        }

        @Test
        @DisplayName("Lấy danh sách sản phẩm theo Người bán thành công")
        void getProductsBySellerId_Success_ShouldReturnDTOList() {
            // Given
            Long sellerId = 10L;
            given(userRepository.existsById(sellerId)).willReturn(true);
            given(productRepository.findBySeller_IdOrderByCreatedAtDesc(sellerId)).willReturn(List.of(sampleProduct));

            ProductResponseDTO dto = mock(ProductResponseDTO.class);
            given(productResponseHelper.buildAll(List.of(sampleProduct))).willReturn(List.of(dto));

            // When
            List<ProductResponseDTO> result = productService.getProductsBySellerId(sellerId);

            // Then
            assertThat(result).hasSize(1);
            then(productRepository).should(times(1)).findBySeller_IdOrderByCreatedAtDesc(sellerId);
        }

        @Test
        @DisplayName("Lấy chi tiết sản phẩm thất bại - Product không tồn tại")
        void getProductWithAuctionById_NotFound_ShouldThrowException() {
            // Given
            Long productId = 999L;
            given(productRepository.findById(productId)).willReturn(Optional.empty());

            // When & Then
            assertThatThrownBy(() -> productService.getProductWithAuctionById(productId))
                    .isInstanceOf(ApplicationException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.PRODUCT_NOT_FOUND);

            then(productResponseHelper).should(never()).build(any());
        }

        @Test
        @DisplayName("Lấy chi tiết sản phẩm thành công")
        void getProductWithAuctionById_Success_ShouldReturnDTO() {
            // Given
            Long productId = 100L;
            given(productRepository.findById(productId)).willReturn(Optional.of(sampleProduct));

            ProductResponseDTO dto = mock(ProductResponseDTO.class);
            given(productResponseHelper.build(sampleProduct)).willReturn(dto);

            // When
            ProductResponseDTO result = productService.getProductWithAuctionById(productId);

            // Then
            assertThat(result).isEqualTo(dto);
        }

        @Test
        @DisplayName("Lấy danh sách sản phẩm công khai trên sàn (APPROVED)")
        void getPublicProducts_Success_ShouldReturnApprovedProducts() {
            // Given
            sampleProduct.setStatus(ProductStatus.APPROVED);
            given(productRepository.findByStatusOrderByCreatedAtDesc(ProductStatus.APPROVED))
                    .willReturn(List.of(sampleProduct));

            ProductResponseDTO dto = mock(ProductResponseDTO.class);
            given(productResponseHelper.buildAll(List.of(sampleProduct))).willReturn(List.of(dto));

            // When
            List<ProductResponseDTO> result = productService.getPublicProducts();

            // Then
            assertThat(result).hasSize(1);
            then(productRepository).should(times(1)).findByStatusOrderByCreatedAtDesc(ProductStatus.APPROVED);
        }
    }

    // =========================================================================
    // 3. UNIT TEST CHO KIỂM DUYỆT BÀI ĐĂNG CỦA ADMIN
    // =========================================================================
    @Nested
    @DisplayName("Nghiệp vụ Admin Kiểm Duyệt Bài Đăng (approve & reject)")
    class AdminModerationTests {

        @Test
        @DisplayName("Admin lấy danh sách bài đăng chờ duyệt (PENDING)")
        void getPendingProducts_Success_ShouldReturnPendingList() {
            // Given
            given(productRepository.findByStatusOrderByCreatedAtDesc(ProductStatus.PENDING))
                    .willReturn(List.of(sampleProduct));

            ProductResponseDTO dto = mock(ProductResponseDTO.class);
            given(productResponseHelper.buildAll(List.of(sampleProduct))).willReturn(List.of(dto));

            // When
            List<ProductResponseDTO> result = productService.getPendingProducts();

            // Then
            assertThat(result).hasSize(1);
        }

        @Test
        @DisplayName("Duyệt bài thất bại - Thời gian kết thúc đã trôi qua trước khi Admin kịp duyệt")
        void approveProduct_ExpiredBeforeApproval_ShouldThrowException() {
            // Given
            Long productId = 100L;
            sampleAuction.setEndTime(LocalDateTime.now().minusMinutes(5)); // Đã hết hạn trong quá khứ

            PendingProductAuctionHolder holder = new PendingProductAuctionHolder(sampleProduct, sampleAuction);
            given(productAuctionLookupHelper.findPendingProductAndAuction(productId)).willReturn(holder);

            // When & Then
            assertThatThrownBy(() -> productService.approveProduct(productId))
                    .isInstanceOf(ApplicationException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.AUCTION_EXPIRED_BEFORE_APPROVAL);
        }

        @Test
        @DisplayName("Duyệt bài thành công - Thời gian bắt đầu trong quá khứ -> Kích hoạt RUNNING ngay")
        void approveProduct_Success_SetsRunningStatusWhenStartTimeInPast() {
            // Given
            Long productId = 100L;
            sampleAuction.setStartTime(LocalDateTime.now().minusHours(1)); // Bắt đầu từ 1 tiếng trước
            sampleAuction.setEndTime(LocalDateTime.now().plusDays(1));

            PendingProductAuctionHolder holder = new PendingProductAuctionHolder(sampleProduct, sampleAuction);
            given(productAuctionLookupHelper.findPendingProductAndAuction(productId)).willReturn(holder);

            given(productImageRepository.findByProductIdOrderByDisplayOrderAsc(productId))
                    .willReturn(List.of());

            ProductResponseDTO dto = mock(ProductResponseDTO.class);
            given(productResponseHelper.build(eq(sampleProduct), eq(sampleAuction), any())).willReturn(dto);

            // When
            ProductResponseDTO result = productService.approveProduct(productId);

            // Then
            assertThat(result).isEqualTo(dto);
            assertThat(sampleProduct.getStatus()).isEqualTo(ProductStatus.APPROVED);
            assertThat(sampleAuction.getStatus()).isEqualTo(AuctionStatus.RUNNING);
        }

        @Test
        @DisplayName("Duyệt bài thành công - Thời gian bắt đầu trong tương lai -> Chuyển trạng thái SCHEDULED")
        void approveProduct_Success_SetsScheduledStatusWhenStartTimeInFuture() {
            // Given
            Long productId = 100L;
            sampleAuction.setStartTime(LocalDateTime.now().plusHours(5)); // 5 tiếng nữa mới bắt đầu
            sampleAuction.setEndTime(LocalDateTime.now().plusDays(2));

            PendingProductAuctionHolder holder = new PendingProductAuctionHolder(sampleProduct, sampleAuction);
            given(productAuctionLookupHelper.findPendingProductAndAuction(productId)).willReturn(holder);

            given(productImageRepository.findByProductIdOrderByDisplayOrderAsc(productId))
                    .willReturn(List.of());

            ProductResponseDTO dto = mock(ProductResponseDTO.class);
            given(productResponseHelper.build(eq(sampleProduct), eq(sampleAuction), any())).willReturn(dto);

            // When
            ProductResponseDTO result = productService.approveProduct(productId);

            // Then
            assertThat(result).isEqualTo(dto);
            assertThat(sampleProduct.getStatus()).isEqualTo(ProductStatus.APPROVED);
            assertThat(sampleAuction.getStatus()).isEqualTo(AuctionStatus.SCHEDULED);
        }

        @Test
        @DisplayName("Từ chối duyệt bài đăng thành công - Đổi trạng thái REJECTED và CANCELLED")
        void rejectProduct_Success_ShouldSetRejectedAndCancelled() {
            // Given
            Long productId = 100L;
            ProductRejectRequestDTO rejectDTO = new ProductRejectRequestDTO();
            rejectDTO.setRejectionReason("Hình ảnh mờ, sai danh mục");

            PendingProductAuctionHolder holder = new PendingProductAuctionHolder(sampleProduct, sampleAuction);
            given(productAuctionLookupHelper.findPendingProductAndAuction(productId)).willReturn(holder);

            given(productImageRepository.findByProductIdOrderByDisplayOrderAsc(productId))
                    .willReturn(List.of());

            ProductResponseDTO dto = mock(ProductResponseDTO.class);
            given(productResponseHelper.build(eq(sampleProduct), eq(sampleAuction), any())).willReturn(dto);

            // When
            ProductResponseDTO result = productService.rejectProduct(productId, rejectDTO);

            // Then
            assertThat(result).isEqualTo(dto);
            assertThat(sampleProduct.getStatus()).isEqualTo(ProductStatus.REJECTED);
            assertThat(sampleProduct.getRejectionReason()).isEqualTo("Hình ảnh mờ, sai danh mục");
            assertThat(sampleAuction.getStatus()).isEqualTo(AuctionStatus.CANCELLED);
        }
    }

    // =========================================================================
    // 4. UNIT TEST CHO HỦY VÀ ĐĂNG LẠI SẢN PHẨM
    // =========================================================================
    @Nested
    @DisplayName("Nghiệp vụ Hủy và Đăng lại Sản Phẩm (cancel & relist)")
    class CancelAndRelistTests {

        @Test
        @DisplayName("Hủy phiên đấu giá thành công - Đổi trạng thái Auction sang CANCELLED")
        void cancelAuction_Success_ShouldSetStatusToCancelled() {
            // Given
            Long sellerId = 10L;
            Long productId = 100L;

            given(productRepository.findById(productId)).willReturn(Optional.of(sampleProduct));
            given(auctionRepository.findByProduct_Id(productId)).willReturn(Optional.of(sampleAuction));

            ProductResponseDTO dto = mock(ProductResponseDTO.class);
            given(productResponseHelper.build(sampleProduct)).willReturn(dto);

            // When
            ProductResponseDTO result = productService.cancelAuction(sellerId, productId);

            // Then
            assertThat(result).isEqualTo(dto);
            assertThat(sampleAuction.getStatus()).isEqualTo(AuctionStatus.CANCELLED);
            then(auctionRepository).should(times(1)).save(sampleAuction);
        }

        @Test
        @DisplayName("Đăng lại (Relist) bài thầu đã hết hạn thành công - Reset 30 ngày hiển thị và chuyển sang RUNNING")
        void relistAuction_Success_ShouldResetAuctionPeriodAndSetRunning() {
            // Given
            Long sellerId = 10L;
            Long auctionId = 50L;
            sampleAuction.setStatus(AuctionStatus.EXPIRED);

            given(auctionRepository.findById(auctionId)).willReturn(Optional.of(sampleAuction));

            ProductResponseDTO dto = mock(ProductResponseDTO.class);
            given(productResponseHelper.build(sampleProduct)).willReturn(dto);

            // When
            ProductResponseDTO result = productService.relistAuction(sellerId, auctionId);

            // Then
            assertThat(result).isEqualTo(dto);
            assertThat(sampleAuction.getStatus()).isEqualTo(AuctionStatus.RUNNING);
            assertThat(sampleAuction.getStartTime()).isNotNull();
            assertThat(sampleAuction.getEndTime()).isAfter(sampleAuction.getStartTime());

            then(auctionValidator).should(times(1)).validateRelist(sellerId, sampleAuction);
        }
    }
}
