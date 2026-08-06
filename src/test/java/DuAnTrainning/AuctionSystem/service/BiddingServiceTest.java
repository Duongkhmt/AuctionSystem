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
import DuAnTrainning.AuctionSystem.repository.AuctionRepository;
import DuAnTrainning.AuctionSystem.repository.BidRepository;
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

@ExtendWith(MockitoExtension.class)
@DisplayName("Unit Test Cho Class BiddingService")
class BiddingServiceTest {

    @Mock
    private AuctionRepository auctionRepository;

    @Mock
    private BidRepository bidRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private BidValidator bidValidator;

    @Mock
    private BidMapper bidMapper;

    @Mock
    private ProxyBiddingEngineHelper proxyBiddingEngineHelper;

    @Mock
    private BidResponseHelper bidResponseHelper;

    @InjectMocks
    private BiddingService biddingService;

    private User sampleBidder;
    private Auction sampleAuction;

    @BeforeEach
    void setUp() {
        sampleBidder = new User();
        sampleBidder.setId(100L);
        sampleBidder.setEmail("bidder@example.com");

        sampleAuction = new Auction();
        sampleAuction.setId(1L);
        sampleAuction.setStatus(AuctionStatus.RUNNING);
        sampleAuction.setCurrentPrice(BigDecimal.valueOf(100000));
        sampleAuction.setBuyNowPrice(BigDecimal.valueOf(500000));
        sampleAuction.setEndTime(LocalDateTime.now().plusHours(2));
    }

    // =========================================================================
    // 1. UNIT TEST CHO PHƯƠNG THỨC placeBid()
    // =========================================================================
    @Nested
    @DisplayName("Nghiệp vụ Đặt Giá Thầu (placeBid)")
    class PlaceBidTests {

        @Test
        @DisplayName("Đặt thầu thất bại - Người dùng không tồn tại trong hệ thống")
        void placeBid_UserNotFound_ShouldThrowException() {
            // Given
            Long bidderId = 99L;
            Long auctionId = 1L;
            BidRequestDTO requestDTO = new BidRequestDTO();
            requestDTO.setBidAmount(BigDecimal.valueOf(150000));

            given(userRepository.findById(bidderId)).willReturn(Optional.empty());

            // When & Then
            assertThatThrownBy(() -> biddingService.placeBid(bidderId, auctionId, requestDTO))
                    .isInstanceOf(ApplicationException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.USER_NOT_FOUND);

            then(auctionRepository).should(never()).findById(any());
            then(proxyBiddingEngineHelper).should(never()).processProxyBidding(any(), any(), any(), any());
        }

        @Test
        @DisplayName("Đặt thầu thất bại - Phiên đấu giá không tồn tại (Tìm theo cả Auction ID và Product ID đều rỗng)")
        void placeBid_AuctionNotFound_ShouldThrowException() {
            // Given
            Long bidderId = 100L;
            Long auctionId = 999L;
            BidRequestDTO requestDTO = new BidRequestDTO();
            requestDTO.setBidAmount(BigDecimal.valueOf(150000));

            given(userRepository.findById(bidderId)).willReturn(Optional.of(sampleBidder));
            given(auctionRepository.findById(auctionId)).willReturn(Optional.empty());
            given(auctionRepository.findByProduct_Id(auctionId)).willReturn(Optional.empty());

            // When & Then
            assertThatThrownBy(() -> biddingService.placeBid(bidderId, auctionId, requestDTO))
                    .isInstanceOf(ApplicationException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.AUCTION_NOT_FOUND);

            then(bidValidator).should(never()).validateBid(any(), any(), any(), any());
        }

        @Test
        @DisplayName("Đặt thầu thành công bình thường - Không kích hoạt Soft-Close Anti-sniping")
        void placeBid_Success_WithoutAntiSnipingExtension() {
            // Given
            Long bidderId = 100L;
            Long auctionId = 1L;
            BigDecimal bidAmount = BigDecimal.valueOf(150000);
            BidRequestDTO requestDTO = new BidRequestDTO();
            requestDTO.setBidAmount(bidAmount);

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
            given(bidResponseHelper.buildResponse(sampleAuction, winningBid, false))
                    .willReturn(expectedResponse);

            // When
            BidResponseDTO actualResponse = biddingService.placeBid(bidderId, auctionId, requestDTO);

            // Then
            assertThat(actualResponse).isEqualTo(expectedResponse);
            assertThat(sampleAuction.getCurrentPrice()).isEqualTo(bidAmount);

            then(bidValidator).should(times(1)).validateBid(sampleBidder, sampleAuction, Optional.empty(), requestDTO);
            then(auctionRepository).should(times(1)).save(sampleAuction);
            then(bidRepository).should(times(1)).saveAll(proxyResult.bidsToSave());
            then(bidResponseHelper).should(times(1)).buildResponse(sampleAuction, winningBid, false);
        }

        @Test
        @DisplayName("Đặt thầu thành công cận giờ kết thúc - Kích hoạt Soft-Close Anti-sniping gia hạn thêm 3 phút")
        void placeBid_Success_WithAntiSnipingExtension() {
            // Given
            Long bidderId = 100L;
            Long auctionId = 1L;
            BigDecimal bidAmount = BigDecimal.valueOf(150000);
            BidRequestDTO requestDTO = new BidRequestDTO();
            requestDTO.setBidAmount(bidAmount);

            // Thiết lập thời gian kết thúc nằm trong cửa sổ 3 phút cuối
            LocalDateTime originalEndTime = LocalDateTime.now().plusMinutes(2);
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

            // When
            BidResponseDTO actualResponse = biddingService.placeBid(bidderId, auctionId, requestDTO);

            // Then
            assertThat(actualResponse).isEqualTo(expectedResponse);
            // Kiểm tra thời gian kết thúc đã được cộng thêm 3 phút
            assertThat(sampleAuction.getEndTime()).isAfter(originalEndTime);

            then(bidResponseHelper).should(times(1)).buildResponse(sampleAuction, winningBid, true);
        }
    }

    // =========================================================================
    // 2. UNIT TEST CHO PHƯƠNG THỨC getAuctionBidHistory()
    // =========================================================================
    @Nested
    @DisplayName("Nghiệp vụ Xem Lịch Sử Đấu Giá (getAuctionBidHistory)")
    class GetAuctionBidHistoryTests {

        @Test
        @DisplayName("Lấy lịch sử thầu thất bại - Phiên đấu giá không tồn tại")
        void getAuctionBidHistory_AuctionNotFound_ShouldThrowException() {
            // Given
            Long auctionId = 999L;
            given(auctionRepository.existsById(auctionId)).willReturn(false);

            // When & Then
            assertThatThrownBy(() -> biddingService.getAuctionBidHistory(auctionId))
                    .isInstanceOf(ApplicationException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.AUCTION_NOT_FOUND);

            then(bidRepository).should(never()).findByAuctionIdOrderByCreatedAtDesc(auctionId);
            then(bidMapper).should(never()).toHistoryDTOList(any());
        }

        @Test
        @DisplayName("Lấy lịch sử thầu thành công - Trả về danh sách DTO")
        void getAuctionBidHistory_Success_ShouldReturnHistoryList() {
            // Given
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

            // When
            List<BidHistoryResponseDTO> result = biddingService.getAuctionBidHistory(auctionId);

            // Then
            assertThat(result).isNotNull().hasSize(1);
            then(auctionRepository).should(times(1)).existsById(auctionId);
            then(bidRepository).should(times(1)).findByAuctionIdOrderByCreatedAtDesc(auctionId);
            then(bidMapper).should(times(1)).toHistoryDTOList(mockBidList);
        }
    }

    // =========================================================================
    // 3. UNIT TEST CHO PHƯƠNG THỨC executeBuyNow()
    // =========================================================================
    @Nested
    @DisplayName("Nghiệp vụ Mua Ngay Giá Cố Định (executeBuyNow)")
    class ExecuteBuyNowTests {

        @Test
        @DisplayName("Mua ngay thất bại - Người dùng không tồn tại")
        void executeBuyNow_UserNotFound_ShouldThrowException() {
            // Given
            Long bidderId = 99L;
            Long auctionId = 1L;

            given(userRepository.findById(bidderId)).willReturn(Optional.empty());

            // When & Then
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
            // Given
            Long bidderId = 100L;
            Long auctionId = 999L;

            given(userRepository.findById(bidderId)).willReturn(Optional.of(sampleBidder));
            given(auctionRepository.findById(auctionId)).willReturn(Optional.empty());

            // When & Then
            assertThatThrownBy(() -> biddingService.executeBuyNow(bidderId, auctionId))
                    .isInstanceOf(ApplicationException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.AUCTION_NOT_FOUND);

            then(bidValidator).should(never()).validateBuyNow(any(), any());
        }

        @Test
        @DisplayName("Mua ngay thành công - Đổi trạng thái sang ENDED và lưu bản ghi chiến thắng")
        void executeBuyNow_Success_ShouldCloseAuctionAndSaveWinningBid() {
            // Given
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

            // When
            BidResponseDTO actualResponse = biddingService.executeBuyNow(bidderId, auctionId);

            // Then
            assertThat(actualResponse).isEqualTo(expectedResponse);
            assertThat(sampleAuction.getStatus()).isEqualTo(AuctionStatus.ENDED);
            assertThat(sampleAuction.getWinner()).isEqualTo(sampleBidder);
            assertThat(sampleAuction.getCurrentPrice()).isEqualTo(buyNowPrice);

            // ArgumentCaptor kiểm tra thông tin Bid được lưu xuống DB
            ArgumentCaptor<Bid> bidCaptor = ArgumentCaptor.forClass(Bid.class);
            then(bidRepository).should(times(1)).save(bidCaptor.capture());

            Bid capturedBid = bidCaptor.getValue();
            assertThat(capturedBid.getBidAmount()).isEqualTo(buyNowPrice);
            assertThat(capturedBid.getBidder()).isEqualTo(sampleBidder);
            assertThat(capturedBid.getAuction()).isEqualTo(sampleAuction);
            assertThat(capturedBid.isAutoBid()).isFalse();

            then(bidValidator).should(times(1)).validateBuyNow(sampleBidder, sampleAuction);
            then(bidResponseHelper).should(times(1)).buildResponse(sampleAuction, savedBid, false);
        }
    }
}
