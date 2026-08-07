package DuAnTrainning.AuctionSystem.service;

import DuAnTrainning.AuctionSystem.dto.request.CheckoutRequestDTO;
import DuAnTrainning.AuctionSystem.dto.request.ShipOrderRequestDTO;
import DuAnTrainning.AuctionSystem.dto.response.CheckoutResponseDTO;
import DuAnTrainning.AuctionSystem.dto.response.SellerOrderResponseDTO;
import DuAnTrainning.AuctionSystem.dto.response.WonAuctionResponseDTO;
import DuAnTrainning.AuctionSystem.entity.Order;
import DuAnTrainning.AuctionSystem.entity.Payment;
import DuAnTrainning.AuctionSystem.enums.OrderStatus;
import DuAnTrainning.AuctionSystem.enums.PaymentMethod;
import DuAnTrainning.AuctionSystem.exception.ApplicationException;
import DuAnTrainning.AuctionSystem.exception.ErrorCode;
import DuAnTrainning.AuctionSystem.mapper.OrderMapper;
import DuAnTrainning.AuctionSystem.repository.OrderRepository;
import DuAnTrainning.AuctionSystem.repository.PaymentRepository;
import DuAnTrainning.AuctionSystem.repository.UserRepository;
import DuAnTrainning.AuctionSystem.service.helper.OrderResponseHelper;
import DuAnTrainning.AuctionSystem.validator.OrderValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
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
 * Class Unit Test dành riêng cho OrderService.
 * Kiểm thử toàn bộ quy trình hậu đấu giá (Post-auction Settlement): Trúng thầu -> Checkout -> Giao hàng -> Nhận hàng.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Unit Test Cho Class OrderService")
class OrderServiceTest {

    // ===== KHAI BÁO CÁC ĐỐI TƯỢNG ĐÓNG THẾ (MOCK OBJECTS) =====
    @Mock
    private OrderRepository orderRepository; // Giả lập truy vấn và lưu đơn hàng

    @Mock
    private PaymentRepository paymentRepository; // Giả lập lưu bản ghi giao dịch thanh toán

    @Mock
    private UserRepository userRepository; // Giả lập xác thực sự tồn tại của Người mua/Người bán

    @Mock
    private OrderResponseHelper orderResponseHelper; // Giả lập đóng gói DTO đơn hàng kèm ảnh đại diện

    @Mock
    private OrderValidator orderValidator; // Giả lập bộ kiểm tra quy tắc trạng thái đơn (PAID, SHIPPING...)

    @Mock
    private OrderMapper orderMapper; // Giả lập Mapper sang DTO cho Người bán

    // ===== ĐỐI TƯỢNG CẦN KIỂM THỬ THẬT =====
    @InjectMocks
    private OrderService orderService; // Instance OrderService thật được tiêm các Mock ở trên

    private Order sampleOrder;

    /**
     * Dữ liệu đơn hàng mẫu ở trạng thái UNPAID (Chờ thanh toán) khởi tạo trước mỗi bài test.
     */
    @BeforeEach
    void setUp() {
        sampleOrder = new Order();
        sampleOrder.setId(100L);
        sampleOrder.setStatus(OrderStatus.UNPAID);
        sampleOrder.setWinningPrice(BigDecimal.valueOf(2000000));
    }

    // =========================================================================
    // 1. NGHIỆP VỤ XEM DANH SÁCH TRÚNG THẦU (getWonAuctions)
    // =========================================================================
    @Nested
    @DisplayName("Nghiệp vụ Xem Danh Sách Trúng Thầu (getWonAuctions)")
    class GetWonAuctionsTests {

        @Test
        @DisplayName("Lấy danh sách trúng thầu thất bại - Người mua không tồn tại")
        void getWonAuctions_UserNotFound_ShouldThrowException() {
            // 1. GIVEN: Bidder ID 99 không có trong hệ thống
            Long bidderId = 99L;
            given(userRepository.existsById(bidderId)).willReturn(false);

            // 2. WHEN & THEN: Bắt ngoại lệ USER_NOT_FOUND
            assertThatThrownBy(() -> orderService.getWonAuctions(bidderId))
                    .isInstanceOf(ApplicationException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.USER_NOT_FOUND);

            // 3. VERIFY: Khẳng định không gọi xuống database tìm đơn hàng
            then(orderRepository).should(never()).findByBuyer_IdOrderByCreatedAtDesc(any());
        }

        @Test
        @DisplayName("Lấy danh sách trúng thầu thành công - Trả về DTO list")
        void getWonAuctions_Success_ShouldReturnDTOList() {
            // 1. GIVEN: Bidder ID 10 tồn tại và có 1 đơn thầu thắng
            Long bidderId = 10L;
            given(userRepository.existsById(bidderId)).willReturn(true);
            given(orderRepository.findByBuyer_IdOrderByCreatedAtDesc(bidderId)).willReturn(List.of(sampleOrder));

            WonAuctionResponseDTO dto = mock(WonAuctionResponseDTO.class);
            given(orderResponseHelper.buildWonAuctionDTOList(List.of(sampleOrder))).willReturn(List.of(dto));

            // 2. WHEN: Thực thi hàm getWonAuctions
            List<WonAuctionResponseDTO> result = orderService.getWonAuctions(bidderId);

            // 3. THEN: Kiểm tra kết quả trả về đúng 1 phần tử DTO
            assertThat(result).hasSize(1);
            then(orderRepository).should(times(1)).findByBuyer_IdOrderByCreatedAtDesc(bidderId);
        }
    }

    // =========================================================================
    // 2. NGHIỆP VỤ THANH TOÁN ĐƠN HÀNG (checkout)
    // =========================================================================
    @Nested
    @DisplayName("Nghiệp vụ Thanh Toán Đơn Hàng (checkout)")
    class CheckoutTests {

        @Test
        @DisplayName("Thanh toán thất bại - Đơn hàng không tồn tại")
        void checkout_OrderNotFound_ShouldThrowException() {
            // 1. GIVEN: Order ID 999 không tìm thấy trong DB
            Long orderId = 999L;
            Long buyerId = 10L;
            CheckoutRequestDTO requestDTO = new CheckoutRequestDTO();

            given(orderRepository.findById(orderId)).willReturn(Optional.empty());

            // 2. WHEN & THEN: Bắt lỗi ORDER_NOT_FOUND
            assertThatThrownBy(() -> orderService.checkout(orderId, buyerId, requestDTO))
                    .isInstanceOf(ApplicationException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.ORDER_NOT_FOUND);

            then(orderValidator).should(never()).validateCheckout(any(), any());
        }

        @Test
        @DisplayName("Thanh toán thành công - Cập nhật địa chỉ, SĐT, chuyển trạng thái PAID và tạo Payment")
        void checkout_Success_ShouldUpdateOrderAndCreatePayment() {
            // 1. GIVEN: Đơn hàng UNPAID và thông tin giao dịch thanh toán chuyển khoản
            Long orderId = 100L;
            Long buyerId = 10L;
            CheckoutRequestDTO requestDTO = new CheckoutRequestDTO();
            requestDTO.setShippingAddress("123 Nguyễn Huệ, Q1, TP.HCM");
            requestDTO.setPhoneNumber("0901234567");
            requestDTO.setPaymentMethod(PaymentMethod.BANK_TRANSFER);

            given(orderRepository.findById(orderId)).willReturn(Optional.of(sampleOrder));

            CheckoutResponseDTO expectedResponse = mock(CheckoutResponseDTO.class);
            given(orderResponseHelper.buildCheckoutDTO(eq(sampleOrder), anyString())).willReturn(expectedResponse);

            // 2. WHEN: Gọi hàm checkout
            CheckoutResponseDTO actualResponse = orderService.checkout(orderId, buyerId, requestDTO);

            // 3. THEN: Kiểm tra đơn hàng được đổi trạng thái PAID, địa chỉ và SĐT đúng
            assertThat(actualResponse).isEqualTo(expectedResponse);
            assertThat(sampleOrder.getStatus()).isEqualTo(OrderStatus.PAID);
            assertThat(sampleOrder.getShippingAddress()).isEqualTo("123 Nguyễn Huệ, Q1, TP.HCM");
            assertThat(sampleOrder.getPhoneNumber()).isEqualTo("0901234567");

            // Xác minh đã thực hiện validate, tạo bản ghi Payment và lưu đơn hàng
            then(orderValidator).should(times(1)).validateCheckout(buyerId, sampleOrder);
            then(paymentRepository).should(times(1)).save(any(Payment.class));
            then(orderRepository).should(times(1)).save(sampleOrder);
        }
    }

    // =========================================================================
    // 3. NGHIỆP VỤ NGƯỜI BÁN GIAO HÀNG (shipOrder)
    // =========================================================================
    @Nested
    @DisplayName("Nghiệp vụ Người Bán Quản Lý Đơn Hàng (getSellerOrders & shipOrder)")
    class SellerOrderTests {

        @Test
        @DisplayName("Giao hàng (shipOrder) thất bại - Đơn hàng không tồn tại")
        void shipOrder_OrderNotFound_ShouldThrowException() {
            // 1. GIVEN: Order ID 999 không tồn tại
            Long orderId = 999L;
            Long sellerId = 5L;
            ShipOrderRequestDTO requestDTO = new ShipOrderRequestDTO();

            given(orderRepository.findById(orderId)).willReturn(Optional.empty());

            // 2. WHEN & THEN: Bắt lỗi ORDER_NOT_FOUND
            assertThatThrownBy(() -> orderService.shipOrder(orderId, sellerId, requestDTO))
                    .isInstanceOf(ApplicationException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.ORDER_NOT_FOUND);

            then(orderValidator).should(never()).validateShipOrder(any(), any());
        }

        @Test
        @DisplayName("Giao hàng (shipOrder) thành công - Cập nhật mã vận đơn và chuyển sang SHIPPING")
        void shipOrder_Success_ShouldSetShippingStatusAndTrackingNumber() {
            // 1. GIVEN: Đơn hàng ở trạng thái PAID (Đã thanh toán)
            Long orderId = 100L;
            Long sellerId = 5L;
            sampleOrder.setStatus(OrderStatus.PAID);

            ShipOrderRequestDTO requestDTO = new ShipOrderRequestDTO();
            requestDTO.setCourierName("Giao Hàng Nhanh");
            requestDTO.setTrackingNumber("GHN123456");

            given(orderRepository.findById(orderId)).willReturn(Optional.of(sampleOrder));

            SellerOrderResponseDTO expectedDTO = mock(SellerOrderResponseDTO.class);
            given(orderMapper.toSellerOrderDTO(sampleOrder)).willReturn(expectedDTO);

            // 2. WHEN: Gọi hàm shipOrder
            SellerOrderResponseDTO actualDTO = orderService.shipOrder(orderId, sellerId, requestDTO);

            // 3. THEN: Kiểm tra đơn chuyển sang SHIPPING, gán đơn vị vận chuyển và mã tra cứu
            assertThat(actualDTO).isEqualTo(expectedDTO);
            assertThat(sampleOrder.getStatus()).isEqualTo(OrderStatus.SHIPPING);
            assertThat(sampleOrder.getCourierName()).isEqualTo("Giao Hàng Nhanh");
            assertThat(sampleOrder.getTrackingNumber()).isEqualTo("GHN123456");

            then(orderValidator).should(times(1)).validateShipOrder(sellerId, sampleOrder);
            then(orderRepository).should(times(1)).save(sampleOrder);
        }
    }

    // =========================================================================
    // 4. NGHIỆP VỤ XÁC NHẬN ĐÃ NHẬN HÀNG (confirmReceived)
    // =========================================================================
    @Nested
    @DisplayName("Nghiệp vụ Xác Nhận Đã Nhận Hàng (confirmReceived)")
    class ConfirmReceivedTests {

        @Test
        @DisplayName("Xác nhận nhận hàng thành công - Chuyển trạng thái sang COMPLETED")
        void confirmReceived_Success_ShouldSetStatusToCompleted() {
            // 1. GIVEN: Đơn hàng đang ở trạng thái SHIPPING (Đang giao)
            Long orderId = 100L;
            Long buyerId = 10L;
            sampleOrder.setStatus(OrderStatus.SHIPPING);

            given(orderRepository.findById(orderId)).willReturn(Optional.of(sampleOrder));

            WonAuctionResponseDTO expectedDTO = mock(WonAuctionResponseDTO.class);
            given(orderResponseHelper.buildWonAuctionDTO(sampleOrder)).willReturn(expectedDTO);

            // 2. WHEN: Người mua bấm xác nhận đã nhận hàng
            WonAuctionResponseDTO actualDTO = orderService.confirmReceived(orderId, buyerId);

            // 3. THEN: Kiểm tra đơn hàng hoàn tất vòng đời chuyển sang COMPLETED
            assertThat(actualDTO).isEqualTo(expectedDTO);
            assertThat(sampleOrder.getStatus()).isEqualTo(OrderStatus.COMPLETED);

            then(orderValidator).should(times(1)).validateConfirmReceived(buyerId, sampleOrder);
            then(orderRepository).should(times(1)).save(sampleOrder);
        }
    }
}
