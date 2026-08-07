package DuAnTrainning.AuctionSystem.service;

import DuAnTrainning.AuctionSystem.entity.Auction;
import DuAnTrainning.AuctionSystem.entity.Bid;
import DuAnTrainning.AuctionSystem.entity.Order;
import DuAnTrainning.AuctionSystem.entity.User;
import DuAnTrainning.AuctionSystem.enums.AuctionStatus;
import DuAnTrainning.AuctionSystem.enums.AuctionType;
import DuAnTrainning.AuctionSystem.enums.ProductStatus;
import DuAnTrainning.AuctionSystem.mapper.OrderMapper;
import DuAnTrainning.AuctionSystem.repository.AuctionRepository;
import DuAnTrainning.AuctionSystem.repository.BidRepository;
import DuAnTrainning.AuctionSystem.repository.OrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;

/**
 * Class Unit Test dành cho AuctionScheduler.
 * Kiểm thử tự động hóa luồng robot chạy ngầm định kỳ 10s: Tự mở thầu, tự hết hạn và tự chốt Winner/Đơn hàng.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Unit Test Cho Class AuctionScheduler (Tự Động Hóa Vòng Đời Thầu)")
class AuctionSchedulerTest {

    @Mock
    private AuctionRepository auctionRepository; // Giả lập cập nhật trạng thái thầu bằng SQL Bulk Update

    @Mock
    private BidRepository bidRepository; // Giả lập tìm lượt thầu cao nhất của phiên hết hạn

    @Mock
    private OrderRepository orderRepository; // Giả lập kiểm tra và lưu đơn hàng trúng thầu

    @Mock
    private OrderMapper orderMapper; // Giả lập chuyển đổi Auction + Winner sang Order Entity

    @InjectMocks
    private AuctionScheduler auctionScheduler; // Instance Robot Scheduler thật được tiêm @Mock

    private User sampleWinner;
    private Auction sampleEnglishAuction;
    private Auction sampleReserveAuction;

    /**
     * Khởi tạo các mẫu thầu ENGLISH và RESERVE hết giờ để test robot xử lý.
     */
    @BeforeEach
    void setUp() {
        sampleWinner = new User();
        sampleWinner.setId(200L);
        sampleWinner.setEmail("winner@example.com");

        // Phiên thầu truyền thống ENGLISH (quá giờ endTime 5 phút)
        sampleEnglishAuction = new Auction();
        sampleEnglishAuction.setId(10L);
        sampleEnglishAuction.setAuctionType(AuctionType.ENGLISH);
        sampleEnglishAuction.setStatus(AuctionStatus.RUNNING);
        sampleEnglishAuction.setEndTime(LocalDateTime.now().minusMinutes(5));

        // Phiên thầu giá bảo lưu RESERVE (giá sàn 1 Triệu, quá giờ endTime 5 phút)
        sampleReserveAuction = new Auction();
        sampleReserveAuction.setId(20L);
        sampleReserveAuction.setAuctionType(AuctionType.RESERVE);
        sampleReserveAuction.setStatus(AuctionStatus.RUNNING);
        sampleReserveAuction.setReservePrice(BigDecimal.valueOf(1000000));
        sampleReserveAuction.setEndTime(LocalDateTime.now().minusMinutes(5));
    }

    @Nested
    @DisplayName("Nghiệp vụ Tự Động Kết Thúc Đấu Giá & Tạo Đơn Hàng (End Auction Logic)")
    class EndAuctionLogicTests {

        @Test
        @DisplayName("Đấu giá ENGLISH kết thúc có thầu -> Gán Winner và tạo Đơn hàng mới (UNPAID)")
        void processAuctionTransitions_EnglishAuction_WithBid_SetsWinnerAndCreatesOrder() {
            // 1. GIVEN: Tìm thấy 1 phiên ENGLISH đã hết hạn có mức bid cao nhất 500k
            given(auctionRepository.findByStatusAndAuctionTypeNotAndEndTimeLessThanEqual(eq(AuctionStatus.RUNNING), eq(AuctionType.BUY_NOW), any()))
                    .willReturn(List.of(sampleEnglishAuction));

            Bid highestBid = new Bid();
            highestBid.setBidder(sampleWinner);
            highestBid.setBidAmount(BigDecimal.valueOf(500000));

            given(bidRepository.findTopByAuctionIdOrderByBidAmountDescCreatedAtAsc(sampleEnglishAuction.getId()))
                    .willReturn(Optional.of(highestBid));
            given(orderRepository.existsByAuction_Id(sampleEnglishAuction.getId())).willReturn(false);

            Order mockOrder = mock(Order.class);
            given(orderMapper.toEntity(sampleEnglishAuction, sampleWinner, highestBid.getBidAmount())).willReturn(mockOrder);

            given(auctionRepository.findByStatusAndWinnerIsNotNull(AuctionStatus.ENDED)).willReturn(List.of());

            // 2. WHEN: Robot kích hoạt tiến trình quét định kỳ processAuctionStatusTransitions
            auctionScheduler.processAuctionStatusTransitions();

            // 3. THEN: Kiểm tra phiên ENGLISH chuyển sang ENDED, gán Winner và tự động tạo đơn hàng UNPAID
            assertThat(sampleEnglishAuction.getStatus()).isEqualTo(AuctionStatus.ENDED);
            assertThat(sampleEnglishAuction.getWinner()).isEqualTo(sampleWinner);

            then(orderRepository).should(times(1)).save(mockOrder);
            then(auctionRepository).should(times(1)).autoStartAuctions(any(), eq(AuctionStatus.SCHEDULED), eq(AuctionStatus.RUNNING), eq(ProductStatus.APPROVED));
            then(auctionRepository).should(times(1)).autoExpireBuyNowAuctions(any(), eq(AuctionStatus.RUNNING), eq(AuctionStatus.EXPIRED), eq(AuctionType.BUY_NOW));
        }

        @Test
        @DisplayName("Đấu giá RESERVE kết thúc nhưng bid cao nhất < reservePrice -> Không có Winner (ế) và không tạo Order")
        void processAuctionTransitions_ReserveAuction_BidLowerThanReserve_NoWinner() {
            // 1. GIVEN: Phiên RESERVE giá sàn 1M nhưng bid cao nhất chỉ đạt 800k (< reservePrice)
            given(auctionRepository.findByStatusAndAuctionTypeNotAndEndTimeLessThanEqual(eq(AuctionStatus.RUNNING), eq(AuctionType.BUY_NOW), any()))
                    .willReturn(List.of(sampleReserveAuction));

            Bid highestBid = new Bid();
            highestBid.setBidder(sampleWinner);
            highestBid.setBidAmount(BigDecimal.valueOf(800000)); // 800k < 1M

            given(bidRepository.findTopByAuctionIdOrderByBidAmountDescCreatedAtAsc(sampleReserveAuction.getId()))
                    .willReturn(Optional.of(highestBid));

            given(auctionRepository.findByStatusAndWinnerIsNotNull(AuctionStatus.ENDED)).willReturn(List.of());

            // 2. WHEN: Robot thực thi quét
            auctionScheduler.processAuctionStatusTransitions();

            // 3. THEN: Phiên RESERVE chuyển sang ENDED nhưng winner = null (bán không thành công) và không tạo Order
            assertThat(sampleReserveAuction.getStatus()).isEqualTo(AuctionStatus.ENDED);
            assertThat(sampleReserveAuction.getWinner()).isNull();

            then(orderRepository).should(never()).save(any());
        }

        @Test
        @DisplayName("Đấu giá RESERVE kết thúc có bid >= reservePrice -> Gán Winner và tạo Order")
        void processAuctionTransitions_ReserveAuction_BidMeetsReserve_SetsWinnerAndOrder() {
            // 1. GIVEN: Phiên RESERVE giá sàn 1M và mức bid đạt 1.2M (>= reservePrice)
            given(auctionRepository.findByStatusAndAuctionTypeNotAndEndTimeLessThanEqual(eq(AuctionStatus.RUNNING), eq(AuctionType.BUY_NOW), any()))
                    .willReturn(List.of(sampleReserveAuction));

            Bid highestBid = new Bid();
            highestBid.setBidder(sampleWinner);
            highestBid.setBidAmount(BigDecimal.valueOf(1200000)); // 1.2M >= 1M

            given(bidRepository.findTopByAuctionIdOrderByBidAmountDescCreatedAtAsc(sampleReserveAuction.getId()))
                    .willReturn(Optional.of(highestBid));
            given(orderRepository.existsByAuction_Id(sampleReserveAuction.getId())).willReturn(false);

            Order mockOrder = mock(Order.class);
            given(orderMapper.toEntity(sampleReserveAuction, sampleWinner, highestBid.getBidAmount())).willReturn(mockOrder);

            given(auctionRepository.findByStatusAndWinnerIsNotNull(AuctionStatus.ENDED)).willReturn(List.of());

            // 2. WHEN: Robot chạy quét
            auctionScheduler.processAuctionStatusTransitions();

            // 3. THEN: Chốt Winner thành công và tạo Đơn hàng cho Winner
            assertThat(sampleReserveAuction.getStatus()).isEqualTo(AuctionStatus.ENDED);
            assertThat(sampleReserveAuction.getWinner()).isEqualTo(sampleWinner);

            then(orderRepository).should(times(1)).save(mockOrder);
        }
    }
}
