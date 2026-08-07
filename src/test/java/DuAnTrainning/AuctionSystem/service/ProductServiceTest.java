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

/**
 * Class Unit Test kiểm thử 100% logic nghiệp vụ của ProductService.
 * Bao phủ các tính năng: Đăng bán sản phẩm, kiểm duyệt bài đăng (Approve/Reject), truy vấn công khai, hủy & đăng lại bài.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Unit Test Cho Class ProductService")
class ProductServiceTest {

    // ===== KHAI BÁO CÁC ĐỐI TƯỢNG ĐÓNG THẾ (MOCK OBJECTS) =====
    @Mock
    private ProductRepository productRepository; // Giả lập lưu và tìm kiếm sản phẩm

    @Mock
    private ProductImageRepository productImageRepository; // Giả lập lưu ảnh sản phẩm

    @Mock
    private AuctionRepository auctionRepository; // Giả lập phiên đấu giá

    @Mock
    private UserRepository userRepository; // Giả lập kiểm tra Người bán

    @Mock
    private CategoryRepository categoryRepository; // Giả lập kiểm tra Danh mục

    @Mock
    private ProductMapper productMapper; // Giả lập Map DTO <-> Product Entity

    @Mock
    private AuctionValidator auctionValidator; // Giả lập validate cấu hình phiên thầu

    @Mock
    private ProductImageValidator productImageValidator; // Giả lập validate số lượng/định dạng ảnh

    @Mock
    private CloudinaryService cloudinaryService; // Giả lập Service upload ảnh Cloudinary

    @Mock
    private ProductResponseHelper productResponseHelper; // Giả lập helper đóng gói DTO sản phẩm

    @Mock
    private AuctionMapper auctionMapper; // Giả lập Map DTO <-> Auction Entity

    @Mock
    private ProductImageMapper productImageMapper; // Giả lập Map UploadedImage sang ProductImage Entity

    @Mock
    private ProductAuctionLookupHelper productAuctionLookupHelper; // Giả lập helper tìm kiếm cặp Product + Auction

    // ===== ĐỐI TƯỢNG CẦN KIỂM THỬ THẬT =====
    @InjectMocks
    private ProductService productService; // Instance ProductService thật được tiêm các Mock trên

    private User sampleSeller;
    private Category sampleCategory;
    private Product sampleProduct;
    private Auction sampleAuction;

    /**
     * Khởi tạo dữ liệu mẫu trước mỗi test method.
     */
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

    /**
     * Dọn dẹp TransactionSynchronizationManager sau mỗi bài test để không ảnh hưởng bài sau.
     */
    @AfterEach
    void tearDown() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    // =========================================================================
    // 1. UNIT TEST CHO PHƯƠNG THỨC createProduct() - TẠO SẢN PHẨM MỚI
    // =========================================================================
    @Nested
    @DisplayName("Nghiệp vụ Tạo Sản Phẩm (createProduct)")
    class CreateProductTests {

        @Test
        @DisplayName("Tạo sản phẩm thất bại - Người bán không tồn tại")
        void createProduct_UserNotFound_ShouldThrowException() {
            // 1. GIVEN: Seller ID 99 không tồn tại trong DB
            Long sellerId = 99L;
            ProductRequestDTO requestDTO = new ProductRequestDTO();
            requestDTO.setCategoryId(5L);

            given(userRepository.findById(sellerId)).willReturn(Optional.empty());

            // 2. WHEN & THEN: Bắt lỗi USER_NOT_FOUND
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
            // 1. GIVEN: Category ID 999 không tồn tại
            Long sellerId = 10L;
            ProductRequestDTO requestDTO = new ProductRequestDTO();
            requestDTO.setCategoryId(999L);

            given(userRepository.findById(sellerId)).willReturn(Optional.of(sampleSeller));
            given(categoryRepository.findById(999L)).willReturn(Optional.empty());

            // 2. WHEN & THEN: Bắt lỗi CATEGORY_NOT_FOUND
            assertThatThrownBy(() -> productService.createProduct(sellerId, requestDTO))
                    .isInstanceOf(ApplicationException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.CATEGORY_NOT_FOUND);

            then(productImageValidator).should(never()).validate(any());
        }

        @Test
        @DisplayName("Tạo sản phẩm thất bại - Danh mục không còn hoạt động (Category Inactive)")
        void createProduct_CategoryInactive_ShouldThrowException() {
            // 1. GIVEN: Danh mục tồn tại nhưng đang bị vô hiệu hóa (active = false)
            Long sellerId = 10L;
            ProductRequestDTO requestDTO = new ProductRequestDTO();
            requestDTO.setCategoryId(5L);

            Category inactiveCategory = new Category();
            inactiveCategory.setId(5L);
            inactiveCategory.setActive(false);

            given(userRepository.findById(sellerId)).willReturn(Optional.of(sampleSeller));
            given(categoryRepository.findById(5L)).willReturn(Optional.of(inactiveCategory));

            // 2. WHEN & THEN: Bắt lỗi CATEGORY_INACTIVE
            assertThatThrownBy(() -> productService.createProduct(sellerId, requestDTO))
                    .isInstanceOf(ApplicationException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.CATEGORY_INACTIVE);

            then(productImageValidator).should(never()).validate(any());
        }

        @Test
        @DisplayName("Tạo sản phẩm thành công - Lưu DB và trả về DTO")
        void createProduct_Success_ShouldReturnProductResponseDTO() {
            // 1. GIVEN: Kích hoạt TransactionSynchronization giả lập trong Unit Test
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

            // 2. WHEN: Gọi hàm createProduct
            ProductResponseDTO actualResponse = productService.createProduct(sellerId, requestDTO);

            // 3. THEN: Kiểm tra bài đăng ở trạng thái PENDING và trả về DTO đúng
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
            // 1. GIVEN: Seller ID 99 không tồn tại
            Long sellerId = 99L;
            given(userRepository.existsById(sellerId)).willReturn(false);

            // 2. WHEN & THEN: Bắt lỗi USER_NOT_FOUND
            assertThatThrownBy(() -> productService.getProductsBySellerId(sellerId))
                    .isInstanceOf(ApplicationException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.USER_NOT_FOUND);

            then(productRepository).should(never()).findBySeller_IdOrderByCreatedAtDesc(any());
        }

        @Test
        @DisplayName("Lấy danh sách sản phẩm theo Người bán thành công")
        void getProductsBySellerId_Success_ShouldReturnDTOList() {
            // 1. GIVEN: Seller ID 10 tồn tại và sở hữu 1 bài đăng
            Long sellerId = 10L;
            given(userRepository.existsById(sellerId)).willReturn(true);
            given(productRepository.findBySeller_IdOrderByCreatedAtDesc(sellerId)).willReturn(List.of(sampleProduct));

            ProductResponseDTO dto = mock(ProductResponseDTO.class);
            given(productResponseHelper.buildAll(List.of(sampleProduct))).willReturn(List.of(dto));

            // 2. WHEN: Gọi hàm lấy bài đăng của Người bán
            List<ProductResponseDTO> result = productService.getProductsBySellerId(sellerId);

            // 3. THEN: Kiểm tra danh sách trả về đúng 1 phần tử
            assertThat(result).hasSize(1);
            then(productRepository).should(times(1)).findBySeller_IdOrderByCreatedAtDesc(sellerId);
        }

        @Test
        @DisplayName("Lấy chi tiết sản phẩm thất bại - Product không tồn tại")
        void getProductWithAuctionById_NotFound_ShouldThrowException() {
            // 1. GIVEN: Product ID 999 không tìm thấy
            Long productId = 999L;
            given(productRepository.findById(productId)).willReturn(Optional.empty());

            // 2. WHEN & THEN: Bắt lỗi PRODUCT_NOT_FOUND
            assertThatThrownBy(() -> productService.getProductWithAuctionById(productId))
                    .isInstanceOf(ApplicationException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.PRODUCT_NOT_FOUND);

            then(productResponseHelper).should(never()).build(any());
        }

        @Test
        @DisplayName("Lấy chi tiết sản phẩm thành công")
        void getProductWithAuctionById_Success_ShouldReturnDTO() {
            // 1. GIVEN: Product ID 100 có trong DB
            Long productId = 100L;
            given(productRepository.findById(productId)).willReturn(Optional.of(sampleProduct));

            ProductResponseDTO dto = mock(ProductResponseDTO.class);
            given(productResponseHelper.build(sampleProduct)).willReturn(dto);

            // 2. WHEN: Gọi hàm lấy chi tiết sản phẩm
            ProductResponseDTO result = productService.getProductWithAuctionById(productId);

            // 3. THEN: Kiểm tra DTO trả về chính xác
            assertThat(result).isEqualTo(dto);
        }

        @Test
        @DisplayName("Lấy danh sách sản phẩm công khai trên sàn (APPROVED)")
        void getPublicProducts_Success_ShouldReturnApprovedProducts() {
            // 1. GIVEN: Sản phẩm đã được Admin duyệt (status = APPROVED)
            sampleProduct.setStatus(ProductStatus.APPROVED);
            given(productRepository.findByStatusOrderByCreatedAtDesc(ProductStatus.APPROVED))
                    .willReturn(List.of(sampleProduct));

            ProductResponseDTO dto = mock(ProductResponseDTO.class);
            given(productResponseHelper.buildAll(List.of(sampleProduct))).willReturn(List.of(dto));

            // 2. WHEN: Gọi hàm lấy sản phẩm công khai trên sàn
            List<ProductResponseDTO> result = productService.getPublicProducts();

            // 3. THEN: Trả về danh sách DTO hợp lệ
            assertThat(result).hasSize(1);
            then(productRepository).should(times(1)).findByStatusOrderByCreatedAtDesc(ProductStatus.APPROVED);
        }
    }

    // =========================================================================
    // 3. UNIT TEST CHO KIỂM DUYỆT BÀI ĐĂNG CỦA ADMIN (approve & reject)
    // =========================================================================
    @Nested
    @DisplayName("Nghiệp vụ Admin Kiểm Duyệt Bài Đăng (approve & reject)")
    class AdminModerationTests {

        @Test
        @DisplayName("Admin lấy danh sách bài đăng chờ duyệt (PENDING)")
        void getPendingProducts_Success_ShouldReturnPendingList() {
            // 1. GIVEN: Có 1 bài đăng PENDING trong DB
            given(productRepository.findByStatusOrderByCreatedAtDesc(ProductStatus.PENDING))
                    .willReturn(List.of(sampleProduct));

            ProductResponseDTO dto = mock(ProductResponseDTO.class);
            given(productResponseHelper.buildAll(List.of(sampleProduct))).willReturn(List.of(dto));

            // 2. WHEN: Admin truy vấn danh sách chờ duyệt
            List<ProductResponseDTO> result = productService.getPendingProducts();

            // 3. THEN: Kiểm tra kết quả trả về đúng bài PENDING
            assertThat(result).hasSize(1);
        }

        @Test
        @DisplayName("Duyệt bài thất bại - Thời gian kết thúc đã trôi qua trước khi Admin kịp duyệt")
        void approveProduct_ExpiredBeforeApproval_ShouldThrowException() {
            // 1. GIVEN: Bài thầu có endTime nằm ở quá khứ (Admin chậm trễ chưa duyệt)
            Long productId = 100L;
            sampleAuction.setEndTime(LocalDateTime.now().minusMinutes(5));

            PendingProductAuctionHolder holder = new PendingProductAuctionHolder(sampleProduct, sampleAuction);
            given(productAuctionLookupHelper.findPendingProductAndAuction(productId)).willReturn(holder);

            // 2. WHEN & THEN: Ném ra lỗi AUCTION_EXPIRED_BEFORE_APPROVAL
            assertThatThrownBy(() -> productService.approveProduct(productId))
                    .isInstanceOf(ApplicationException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.AUCTION_EXPIRED_BEFORE_APPROVAL);
        }

        @Test
        @DisplayName("Duyệt bài thành công - Thời gian bắt đầu trong quá khứ -> Kích hoạt RUNNING ngay")
        void approveProduct_Success_SetsRunningStatusWhenStartTimeInPast() {
            // 1. GIVEN: startTime ở quá khứ (đã đến lúc đấu giá)
            Long productId = 100L;
            sampleAuction.setStartTime(LocalDateTime.now().minusHours(1));
            sampleAuction.setEndTime(LocalDateTime.now().plusDays(1));

            PendingProductAuctionHolder holder = new PendingProductAuctionHolder(sampleProduct, sampleAuction);
            given(productAuctionLookupHelper.findPendingProductAndAuction(productId)).willReturn(holder);

            given(productImageRepository.findByProductIdOrderByDisplayOrderAsc(productId))
                    .willReturn(List.of());

            ProductResponseDTO dto = mock(ProductResponseDTO.class);
            given(productResponseHelper.build(eq(sampleProduct), eq(sampleAuction), any())).willReturn(dto);

            // 2. WHEN: Admin bấm Chấp Thuận duyệt bài
            ProductResponseDTO result = productService.approveProduct(productId);

            // 3. THEN: Sản phẩm đổi sang APPROVED và phiên thầu chuyển trực tiếp sang RUNNING
            assertThat(result).isEqualTo(dto);
            assertThat(sampleProduct.getStatus()).isEqualTo(ProductStatus.APPROVED);
            assertThat(sampleAuction.getStatus()).isEqualTo(AuctionStatus.RUNNING);
        }

        @Test
        @DisplayName("Duyệt bài thành công - Thời gian bắt đầu trong tương lai -> Chuyển trạng thái SCHEDULED")
        void approveProduct_Success_SetsScheduledStatusWhenStartTimeInFuture() {
            // 1. GIVEN: startTime ở tương lai (còn 5 tiếng nữa mới đến giờ thầu)
            Long productId = 100L;
            sampleAuction.setStartTime(LocalDateTime.now().plusHours(5));
            sampleAuction.setEndTime(LocalDateTime.now().plusDays(2));

            PendingProductAuctionHolder holder = new PendingProductAuctionHolder(sampleProduct, sampleAuction);
            given(productAuctionLookupHelper.findPendingProductAndAuction(productId)).willReturn(holder);

            given(productImageRepository.findByProductIdOrderByDisplayOrderAsc(productId))
                    .willReturn(List.of());

            ProductResponseDTO dto = mock(ProductResponseDTO.class);
            given(productResponseHelper.build(eq(sampleProduct), eq(sampleAuction), any())).willReturn(dto);

            // 2. WHEN: Admin bấm Chấp Thuận duyệt bài
            ProductResponseDTO result = productService.approveProduct(productId);

            // 3. THEN: Sản phẩm đổi sang APPROVED nhưng phiên thầu hẹn giờ ở trạng thái SCHEDULED
            assertThat(result).isEqualTo(dto);
            assertThat(sampleProduct.getStatus()).isEqualTo(ProductStatus.APPROVED);
            assertThat(sampleAuction.getStatus()).isEqualTo(AuctionStatus.SCHEDULED);
        }

        @Test
        @DisplayName("Từ chối duyệt bài đăng thành công - Đổi trạng thái REJECTED và CANCELLED")
        void rejectProduct_Success_ShouldSetRejectedAndCancelled() {
            // 1. GIVEN: Lý do từ chối bài đăng
            Long productId = 100L;
            ProductRejectRequestDTO rejectDTO = new ProductRejectRequestDTO();
            rejectDTO.setRejectionReason("Hình ảnh mờ, sai danh mục");

            PendingProductAuctionHolder holder = new PendingProductAuctionHolder(sampleProduct, sampleAuction);
            given(productAuctionLookupHelper.findPendingProductAndAuction(productId)).willReturn(holder);

            given(productImageRepository.findByProductIdOrderByDisplayOrderAsc(productId))
                    .willReturn(List.of());

            ProductResponseDTO dto = mock(ProductResponseDTO.class);
            given(productResponseHelper.build(eq(sampleProduct), eq(sampleAuction), any())).willReturn(dto);

            // 2. WHEN: Admin bấm Từ Chối bài đăng
            ProductResponseDTO result = productService.rejectProduct(productId, rejectDTO);

            // 3. THEN: Sản phẩm chuyển REJECTED kèm lý do, Auction chuyển CANCELLED
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
            // 1. GIVEN: Sản phẩm thuộc sở hữu đúng Người bán ID 10
            Long sellerId = 10L;
            Long productId = 100L;

            given(productRepository.findById(productId)).willReturn(Optional.of(sampleProduct));
            given(auctionRepository.findByProduct_Id(productId)).willReturn(Optional.of(sampleAuction));

            ProductResponseDTO dto = mock(ProductResponseDTO.class);
            given(productResponseHelper.build(sampleProduct)).willReturn(dto);

            // 2. WHEN: Người bán bấm Hủy bài
            ProductResponseDTO result = productService.cancelAuction(sellerId, productId);

            // 3. THEN: Trạng thái phiên thầu chuyển sang CANCELLED
            assertThat(result).isEqualTo(dto);
            assertThat(sampleAuction.getStatus()).isEqualTo(AuctionStatus.CANCELLED);
            then(auctionRepository).should(times(1)).save(sampleAuction);
        }

        @Test
        @DisplayName("Đăng lại (Relist) bài thầu đã hết hạn thành công - Reset 30 ngày hiển thị và chuyển sang RUNNING")
        void relistAuction_Success_ShouldResetAuctionPeriodAndSetRunning() {
            // 1. GIVEN: Bài thầu đang ở trạng thái EXPIRED (Đã hết hạn 30 ngày)
            Long sellerId = 10L;
            Long auctionId = 50L;
            sampleAuction.setStatus(AuctionStatus.EXPIRED);

            given(auctionRepository.findById(auctionId)).willReturn(Optional.of(sampleAuction));

            ProductResponseDTO dto = mock(ProductResponseDTO.class);
            given(productResponseHelper.build(sampleProduct)).willReturn(dto);

            // 2. WHEN: Người bán bấm Đăng Lại (Relist)
            ProductResponseDTO result = productService.relistAuction(sellerId, auctionId);

            // 3. THEN: Trạng thái khôi phục RUNNING và thời gian kết thúc tự động gia hạn thêm 30 ngày từ hiện tại
            assertThat(result).isEqualTo(dto);
            assertThat(sampleAuction.getStatus()).isEqualTo(AuctionStatus.RUNNING);
            assertThat(sampleAuction.getStartTime()).isNotNull();
            assertThat(sampleAuction.getEndTime()).isAfter(sampleAuction.getStartTime());

            then(auctionValidator).should(times(1)).validateRelist(sellerId, sampleAuction);
        }
    }
}
