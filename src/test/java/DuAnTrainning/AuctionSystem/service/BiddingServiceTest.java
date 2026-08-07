package DuAnTrainning.AuctionSystem.service;

import DuAnTrainning.AuctionSystem.dto.request.BidRequestDTO;
import DuAnTrainning.AuctionSystem.dto.response.BidHistoryResponseDTO;
import DuAnTrainning.AuctionSystem.dto.response.BidResponseDTO;
import DuAnTrainning.AuctionSystem.entity.Auction;
import DuAnTrainning.AuctionSystem.entity.Bid;
import DuAnTrainning.AuctionSystem.entity.User;
import DuAnTrainning.AuctionSystem.enums.AuctionStatus;
import DuAnTrainning.AuctionSystem.exception.ApplicationException;
import DuAnTrainning.AuctionSystem.exception.ErrorCode;
import DuAnTrainning.AuctionSystem.mapper.BidMapper;
import DuAnTrainning.AuctionSystem.mapper.OrderMapper;
import DuAnTrainning.AuctionSystem.repository.AuctionRepository;
import DuAnTrainning.AuctionSystem.repository.BidRepository;
import DuAnTrainning.AuctionSystem.repository.OrderRepository;
import DuAnTrainning.AuctionSystem.repository.UserRepository;
import DuAnTrainning.AuctionSystem.service.helper.BidResponseHelper;
import DuAnTrainning.AuctionSystem.service.helper.ProxyBiddingEngineHelper;
import DuAnTrainning.AuctionSystem.service.helper.ProxyBiddingEngineHelper.ProxyBiddingResult;
import DuAnTrainning.AuctionSystem.validator.BidValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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
 * Class kiểm thử tự động (Unit Test) dành riêng cho BiddingService.
 * Sử dụng JUnit 5 kết hợp Mockito extension để cô lập 100% các lớp phụ thuộc bên ngoài (Repositories, Helpers, Validators).
 */
@ExtendWith(MockitoExtension.class) // Tự động khởi tạo và inject các đối tượng Mock trong JUnit 5 mà không cần load Spring Context
@DisplayName("Unit Test Cho Class BiddingService")
class BiddingServiceTest {

    // ===== KHAI BÁO CÁC ĐỐI TƯỢNG ĐÓNG THẾ (MOCK OBJECTS) =====
    @Mock
    private AuctionRepository auctionRepository; // Giả lập truy vấn dữ liệu phiên đấu giá

    @Mock
    private BidRepository bidRepository; // Giả lập truy vấn và lưu bản ghi lịch sử thầu

    @Mock
    private UserRepository userRepository; // Giả lập truy vấn người dùng (Bidder/Seller)

    @Mock
    private BidValidator bidValidator; // Giả lập bộ kiểm tra quy tắc ràng buộc giá thầu

    @Mock
    private BidMapper bidMapper; // Giả lập Mapper chuyển đổi Entity sang DTO

    @Mock
    private OrderRepository orderRepository; // Giả lập lưu đơn hàng trúng thầu

    @Mock
    private OrderMapper orderMapper; // Giả lập Mapper chuyển sang DTO đơn hàng

    @Mock
    private ProxyBiddingEngineHelper proxyBiddingEngineHelper; // Giả lập động cơ tự động nhảy giá Proxy Bidding

    @Mock
    private BidResponseHelper bidResponseHelper; // Giả lập đóng gói DTO phản hồi

    // ===== ĐỐI TƯỢNG CẦN KIỂM THỬ THẬT (CLASS UNDER TEST) =====
    @InjectMocks
    private BiddingService biddingService; // Khởi tạo BiddingService thật và tự động tiêm các đối tượng @Mock ở trên vào constructor

    // Dữ liệu mẫu dùng chung cho các test case
    private User sampleBidder;
    private Auction sampleAuction;

    /**
     * Hàm chuẩn bị dữ liệu giả lập được chạy tự động TRƯỚC MỖI test method.
     */
    @BeforeEach
    void setUp() {
        // Tạo đối tượng Người đấu giá giả lập (User ID = 100)
        sampleBidder = new User();
        sampleBidder.setId(100L);
        sampleBidder.setEmail("bidder@example.com");

        // Tạo đối tượng Phiên đấu giá giả lập (Auction ID = 1, giá hiện tại = 100k, giá mua ngay = 500k, thời gian còn 2 tiếng)
        sampleAuction = new Auction();
        sampleAuction.setId(1L);
        sampleAuction.setStatus(AuctionStatus.RUNNING);
        sampleAuction.setCurrentPrice(BigDecimal.valueOf(100000));
        sampleAuction.setBuyNowPrice(BigDecimal.valueOf(500000));
        sampleAuction.setEndTime(LocalDateTime.now().plusHours(2));
    }

    // =========================================================================
    // 1. UNIT TEST CHO PHƯƠNG THỨC placeBid() - ĐẶT GIÁ THẦU
    // =========================================================================
    @Nested
    @DisplayName("Nghiệp vụ Đặt Giá Thầu (placeBid)")
    class PlaceBidTests {

        @Test
        @DisplayName("Đặt thầu thất bại - Người dùng không tồn tại trong hệ thống")
        void placeBid_UserNotFound_ShouldThrowException() {
            // 1. GIVEN (Kịch bản giả lập): Người dùng ID 99 không có trong Database
            Long bidderId = 99L;
            Long auctionId = 1L;
            BidRequestDTO requestDTO = new BidRequestDTO();
            requestDTO.setBidAmount(BigDecimal.valueOf(150000));

            // Định nghĩa hành vi: Khi gọi userRepository.findById(99L) -> Trả về Optional rỗng
            given(userRepository.findById(bidderId)).willReturn(Optional.empty());

            // 2. WHEN & THEN (Thực thi & Kiểm tra): Gọi hàm placeBid và bắt Exception ném ra
            assertThatThrownBy(() -> biddingService.placeBid(bidderId, auctionId, requestDTO))
                    .isInstanceOf(ApplicationException.class) // Kiểm tra ngoại lệ đúng loại ApplicationException
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.USER_NOT_FOUND); // Kiểm tra mã lỗi chính xác là USER_NOT_FOUND

            // 3. VERIFY (Xác minh tương tác): Khẳng định hệ thống dừng lại ngay, không bao giờ gọi tới auctionRepository hay engine
            then(auctionRepository).should(never()).findById(any());
            then(proxyBiddingEngineHelper).should(never()).processProxyBidding(any(), any(), any(), any());
        }

        @Test
        @DisplayName("Đặt thầu thất bại - Phiên đấu giá không tồn tại (Tìm cả Auction ID và Product ID đều rỗng)")
        void placeBid_AuctionNotFound_ShouldThrowException() {
            // 1. GIVEN: Người dùng tồn tại nhưng Phiên đấu giá ID 999 không có trong Database
            Long bidderId = 100L;
            Long auctionId = 999L;
            BidRequestDTO requestDTO = new BidRequestDTO();
            requestDTO.setBidAmount(BigDecimal.valueOf(150000));

            given(userRepository.findById(bidderId)).willReturn(Optional.of(sampleBidder));
            given(auctionRepository.findById(auctionId)).willReturn(Optional.empty());
            given(auctionRepository.findByProduct_Id(auctionId)).willReturn(Optional.empty());

            // 2. WHEN & THEN: Thực thi và xác nhận ném lỗi AUCTION_NOT_FOUND
            assertThatThrownBy(() -> biddingService.placeBid(bidderId, auctionId, requestDTO))
                    .isInstanceOf(ApplicationException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.AUCTION_NOT_FOUND);

            // 3. VERIFY: Khẳng định không thực hiện bước validateBid
            then(bidValidator).should(never()).validateBid(any(), any(), any(), any());
        }

        @Test
        @DisplayName("Đặt thầu thành công bình thường - Không kích hoạt Soft-Close Anti-sniping")
        void placeBid_Success_WithoutAntiSnipingExtension() {
            // 1. GIVEN: Dữ liệu hợp lệ, phiên thầu còn 2 tiếng nữa mới hết hạn (ngoài cửa sổ 3 phút cuối)
            Long bidderId = 100L;
            Long auctionId = 1L;
            BigDecimal bidAmount = BigDecimal.valueOf(150000);
            BidRequestDTO requestDTO = new BidRequestDTO();
            requestDTO.setBidAmount(bidAmount);

            given(userRepository.findById(bidderId)).willReturn(Optional.of(sampleBidder));
            given(auctionRepository.findById(auctionId)).willReturn(Optional.of(sampleAuction));
            given(bidRepository.findTopByAuctionIdOrderByBidAmountDescCreatedAtAsc(auctionId))
                    .willReturn(Optional.empty()); // Chưa có lượt bid nào trước đó

            // Giả lập kết quả trả về từ động cơ Proxy Bidding Engine
            Bid winningBid = new Bid();
            winningBid.setBidAmount(bidAmount);
            ProxyBiddingResult proxyResult = new ProxyBiddingResult(List.of(winningBid), winningBid, bidAmount);

            given(proxyBiddingEngineHelper.processProxyBidding(sampleAuction, sampleBidder, bidAmount, null))
                    .willReturn(proxyResult);

            // Giả lập Helper trả về DTO kết quả
            BidResponseDTO expectedResponse = mock(BidResponseDTO.class);
            given(bidResponseHelper.buildResponse(sampleAuction, winningBid, false))
                    .willReturn(expectedResponse);

            // 2. WHEN: Gọi hàm placeBid thật của BiddingService
            BidResponseDTO actualResponse = biddingService.placeBid(bidderId, auctionId, requestDTO);

            // 3. THEN: Kiểm tra giá mới được cập nhật đúng và trả về DTO như kỳ vọng
            assertThat(actualResponse).isEqualTo(expectedResponse);
            assertThat(sampleAuction.getCurrentPrice()).isEqualTo(bidAmount);

            // Xác minh các thao tác lưu dữ liệu được thực thi đúng 1 lần
            then(bidValidator).should(times(1)).validateBid(sampleBidder, sampleAuction, Optional.empty(), requestDTO);
            then(auctionRepository).should(times(1)).save(sampleAuction);
            then(bidRepository).should(times(1)).saveAll(proxyResult.bidsToSave());
            then(bidResponseHelper).should(times(1)).buildResponse(sampleAuction, winningBid, false);
        }

        @Test
        @DisplayName("Đặt thầu thành công cận giờ kết thúc - Kích hoạt Soft-Close Anti-sniping gia hạn thêm 3 phút")
        void placeBid_Success_WithAntiSnipingExtension() {
            // 1. GIVEN: Thời gian kết thúc nằm trong 3 phút cuối (chỉ còn 2 phút nữa)
            Long bidderId = 100L;
            Long auctionId = 1L;
            BigDecimal bidAmount = BigDecimal.valueOf(150000);
            BidRequestDTO requestDTO = new BidRequestDTO();
            requestDTO.setBidAmount(bidAmount);

            LocalDateTime originalEndTime = LocalDateTime.now().plusMinutes(2); // Còn 2 phút
            sampleAuction.setEndTime(originalEndTime);

            given(userRepository.findById(bidderId)).willReturn(Optional.of(sampleBidder));
            given(auctionRepository.findById(auctionId)).willReturn(Optional.of(sampleAuction));
            given(bidRepository.findTopByAuctionIdOrderByBidAmountDescCreatedAtAsc(auctionId))
                    .willReturn(Optional.empty());

            Bid winningBid = new Bid();
            winningBid.setBidAmount(bidAmount);
            ProxyBiddingResult proxyResult = new ProxyBiddingResult(List.of(winningBid), winningBid, bidAmount);

            given(proxyBiddingEngineHelper.processProxyBidding(sampleAuction, sampleBidder, bidAmount, null))
                    .willReturn(proxyResult);

            BidResponseDTO expectedResponse = mock(BidResponseDTO.class);
            given(bidResponseHelper.buildResponse(eq(sampleAuction), eq(winningBid), eq(true)))
                    .willReturn(expectedResponse);

            // 2. WHEN: Gọi hàm placeBid
            BidResponseDTO actualResponse = biddingService.placeBid(bidderId, auctionId, requestDTO);

            // 3. THEN: Kiểm tra thời gian kết thúc đã tự động được cộng thêm 3 phút (Soft-Close)
            assertThat(actualResponse).isEqualTo(expectedResponse);
            assertThat(sampleAuction.getEndTime()).isAfter(originalEndTime); // Thời gian kết thúc mới phải lớn hơn mốc ban đầu

            then(bidResponseHelper).should(times(1)).buildResponse(sampleAuction, winningBid, true);
        }
    }

    // =========================================================================
    // 2. UNIT TEST CHO PHƯƠNG THỨC getAuctionBidHistory() - XEM LỊCH SỬ THẦU
    // =========================================================================
    @Nested
    @DisplayName("Nghiệp vụ Xem Lịch Sử Đấu Giá (getAuctionBidHistory)")
    class GetAuctionBidHistoryTests {

        @Test
        @DisplayName("Lấy lịch sử thầu thất bại - Phiên đấu giá không tồn tại")
        void getAuctionBidHistory_AuctionNotFound_ShouldThrowException() {
            // 1. GIVEN: Auction ID 999 không tồn tại
            Long auctionId = 999L;
            given(auctionRepository.existsById(auctionId)).willReturn(false);

            // 2. WHEN & THEN: Thực thi và bắt lỗi AUCTION_NOT_FOUND
            assertThatThrownBy(() -> biddingService.getAuctionBidHistory(auctionId))
                    .isInstanceOf(ApplicationException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.AUCTION_NOT_FOUND);

            // 3. VERIFY: Khẳng định không gọi truy vấn tìm lịch sử Bid khi phiên thầu không tồn tại
            then(bidRepository).should(never()).findByAuctionIdOrderByCreatedAtDesc(auctionId);
            then(bidMapper).should(never()).toHistoryDTOList(any());
        }

        @Test
        @DisplayName("Lấy lịch sử thầu thành công - Trả về danh sách DTO")
        void getAuctionBidHistory_Success_ShouldReturnHistoryList() {
            // 1. GIVEN: Auction ID 1 tồn tại và có 1 bản ghi Bid trong DB
            Long auctionId = 1L;
            given(auctionRepository.existsById(auctionId)).willReturn(true);

            Bid mockBid = new Bid();
            mockBid.setId(10L);
            mockBid.setBidAmount(BigDecimal.valueOf(500000));
            mockBid.setCreatedAt(LocalDateTime.now());
            List<Bid> mockBidList = List.of(mockBid);

            given(bidRepository.findByAuctionIdOrderByCreatedAtDesc(auctionId)).willReturn(mockBidList);

            BidHistoryResponseDTO mockDto = mock(BidHistoryResponseDTO.class);
            given(bidMapper.toHistoryDTOList(mockBidList)).willReturn(List.of(mockDto));

            // 2. WHEN: Gọi hàm lấy lịch sử thầu
            List<BidHistoryResponseDTO> result = biddingService.getAuctionBidHistory(auctionId);

            // 3. THEN: Kiểm tra kết quả trả về không null và có đúng 1 phần tử DTO
            assertThat(result).isNotNull().hasSize(1);
            then(auctionRepository).should(times(1)).existsById(auctionId);
            then(bidRepository).should(times(1)).findByAuctionIdOrderByCreatedAtDesc(auctionId);
            then(bidMapper).should(times(1)).toHistoryDTOList(mockBidList);
        }
    }

    // =========================================================================
    // 3. UNIT TEST CHO PHƯƠNG THỨC executeBuyNow() - MUA NGAY GIÁ CỐ ĐỊNH
    // =========================================================================
    @Nested
    @DisplayName("Nghiệp vụ Mua Ngay Giá Cố Định (executeBuyNow)")
    class ExecuteBuyNowTests {

        @Test
        @DisplayName("Mua ngay thất bại - Người dùng không tồn tại")
        void executeBuyNow_UserNotFound_ShouldThrowException() {
            // 1. GIVEN: Bidder ID 99 không tồn tại
            Long bidderId = 99L;
            Long auctionId = 1L;

            given(userRepository.findById(bidderId)).willReturn(Optional.empty());

            // 2. WHEN & THEN: Bắt lỗi USER_NOT_FOUND
            assertThatThrownBy(() -> biddingService.executeBuyNow(bidderId, auctionId))
                    .isInstanceOf(ApplicationException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.USER_NOT_FOUND);

            then(auctionRepository).should(never()).findById(any());
            then(bidValidator).should(never()).validateBuyNow(any(), any());
        }

        @Test
        @DisplayName("Mua ngay thất bại - Phiên đấu giá không tồn tại")
        void executeBuyNow_AuctionNotFound_ShouldThrowException() {
            // 1. GIVEN: Auction ID 999 không tồn tại
            Long bidderId = 100L;
            Long auctionId = 999L;

            given(userRepository.findById(bidderId)).willReturn(Optional.of(sampleBidder));
            given(auctionRepository.findById(auctionId)).willReturn(Optional.empty());

            // 2. WHEN & THEN: Bắt lỗi AUCTION_NOT_FOUND
            assertThatThrownBy(() -> biddingService.executeBuyNow(bidderId, auctionId))
                    .isInstanceOf(ApplicationException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.AUCTION_NOT_FOUND);

            then(bidValidator).should(never()).validateBuyNow(any(), any());
        }

        @Test
        @DisplayName("Mua ngay thành công - Đổi trạng thái sang ENDED và lưu bản ghi chiến thắng")
        void executeBuyNow_Success_ShouldCloseAuctionAndSaveWinningBid() {
            // 1. GIVEN: Thông tin mua ngay hợp lệ với giá buyNowPrice = 500k
            Long bidderId = 100L;
            Long auctionId = 1L;
            BigDecimal buyNowPrice = BigDecimal.valueOf(500000);

            given(userRepository.findById(bidderId)).willReturn(Optional.of(sampleBidder));
            given(auctionRepository.findById(auctionId)).willReturn(Optional.of(sampleAuction));

            Bid savedBid = new Bid();
            savedBid.setId(50L);
            savedBid.setBidAmount(buyNowPrice);
            savedBid.setBidder(sampleBidder);
            savedBid.setAuction(sampleAuction);

            given(bidRepository.save(any(Bid.class))).willReturn(savedBid);

            BidResponseDTO expectedResponse = mock(BidResponseDTO.class);
            given(bidResponseHelper.buildResponse(sampleAuction, savedBid, false))
                    .willReturn(expectedResponse);

            // 2. WHEN: Gọi phương thức mua ngay
            BidResponseDTO actualResponse = biddingService.executeBuyNow(bidderId, auctionId);

            // 3. THEN: Kiểm tra các cập nhật dữ liệu của phiên đấu giá
            assertThat(actualResponse).isEqualTo(expectedResponse);
            assertThat(sampleAuction.getStatus()).isEqualTo(AuctionStatus.ENDED); // Trạng thái chốt sang ENDED
            assertThat(sampleAuction.getWinner()).isEqualTo(sampleBidder); // Gán người mua làm Winner
            assertThat(sampleAuction.getCurrentPrice()).isEqualTo(buyNowPrice); // Giá hiện tại = giá mua ngay

            // 🌟 Sử dụng ArgumentCaptor để trích xuất đối tượng Bid được truyền vào bidRepository.save()
            ArgumentCaptor<Bid> bidCaptor = ArgumentCaptor.forClass(Bid.class);
            then(bidRepository).should(times(1)).save(bidCaptor.capture());

            Bid capturedBid = bidCaptor.getValue();
            assertThat(capturedBid.getBidAmount()).isEqualTo(buyNowPrice);
            assertThat(capturedBid.getBidder()).isEqualTo(sampleBidder);
            assertThat(capturedBid.getAuction()).isEqualTo(sampleAuction);
            assertThat(capturedBid.isAutoBid()).isFalse(); // Mua ngay do người thật thao tác, không phải auto-bid

            then(bidValidator).should(times(1)).validateBuyNow(sampleBidder, sampleAuction);
            then(bidResponseHelper).should(times(1)).buildResponse(sampleAuction, savedBid, false);
        }
    }
}
