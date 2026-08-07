package DuAnTrainning.AuctionSystem.service;

import DuAnTrainning.AuctionSystem.dto.request.CheckoutRequestDTO;
import DuAnTrainning.AuctionSystem.dto.request.ShipOrderRequestDTO;
import DuAnTrainning.AuctionSystem.dto.response.CheckoutResponseDTO;
import DuAnTrainning.AuctionSystem.dto.response.SellerOrderResponseDTO;
import DuAnTrainning.AuctionSystem.dto.response.WonAuctionResponseDTO;
import DuAnTrainning.AuctionSystem.entity.Order;
import DuAnTrainning.AuctionSystem.entity.Payment;
import DuAnTrainning.AuctionSystem.enums.OrderStatus;
import DuAnTrainning.AuctionSystem.enums.PaymentStatus;
import DuAnTrainning.AuctionSystem.exception.ApplicationException;
import DuAnTrainning.AuctionSystem.exception.ErrorCode;
import DuAnTrainning.AuctionSystem.mapper.OrderMapper;
import DuAnTrainning.AuctionSystem.repository.OrderRepository;
import DuAnTrainning.AuctionSystem.repository.PaymentRepository;
import DuAnTrainning.AuctionSystem.repository.UserRepository;
import DuAnTrainning.AuctionSystem.service.helper.OrderResponseHelper;
import DuAnTrainning.AuctionSystem.validator.OrderValidator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;
    private final PaymentRepository paymentRepository;
    private final UserRepository userRepository;
    private final OrderResponseHelper orderResponseHelper;
    private final OrderValidator orderValidator;
    private final OrderMapper orderMapper;


    // 1. NGƯỜI MUA TRUY VẤN DANH SÁCH SẢN PHẨM ĐÃ TRÚNG THẦU
    @Transactional(readOnly = true)
    public List<WonAuctionResponseDTO> getWonAuctions(Long bidderId) {
        // 1. Kiểm tra sự tồn tại của Người Mua trong hệ thống
        if (!userRepository.existsById(bidderId)) {
            throw new ApplicationException(ErrorCode.USER_NOT_FOUND);
        }
        // 2. Truy vấn danh sách các đơn hàng trúng thầu mà buyer_id = bidderId (mới nhất xếp trên)
        List<Order> orders = orderRepository.findByBuyer_IdOrderByCreatedAtDesc(bidderId);
        // 3. Đưa danh sách Entity cho Helper đóng gói DTO kèm lấy ảnh đại diện Thumbnail trả về
        return orderResponseHelper.buildWonAuctionDTOList(orders);
    }

    // 2. NGƯỜI MUA CHỐT ĐỊA CHỈ & THANH TOÁN (CHECKOUT)
    @Transactional
    public CheckoutResponseDTO checkout(Long orderId, Long buyerId, CheckoutRequestDTO requestDTO) {
        // 1. Tìm thông tin đơn hàng theo orderId
        Order order = orderRepository.findById(orderId).orElseThrow(() -> new ApplicationException(ErrorCode.ORDER_NOT_FOUND));

        // 2. Gọi OrderValidator kiểm tra quy tắc: Bắt buộc chính chủ Người Mua và status đang là UNPAID
        orderValidator.validateCheckout(buyerId, order);

        // 3. Cập nhật thông tin nhận hàng (địa chỉ, SĐT) và chuyển trạng thái đơn sang PAID
        order.setShippingAddress(requestDTO.getShippingAddress());
        order.setPhoneNumber(requestDTO.getPhoneNumber());
        order.setStatus(OrderStatus.PAID);

        // 4. Sinh mã giao dịch duy nhất (TXN-XXXXX) và khởi tạo bản ghi thanh toán Payment
        String transactionCode = "TXN-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        Payment payment = new Payment();
        payment.setOrder(order);
        payment.setAmount(order.getWinningPrice());
        payment.setPaymentMethod(requestDTO.getPaymentMethod());
        payment.setTransactionCode(transactionCode);
        payment.setStatus(PaymentStatus.SUCCESS);
        paymentRepository.save(payment);

        // 5. Lưu thông tin đơn hàng đã cập nhật xuống Database
        orderRepository.save(order);

        // 6. Đóng gói DTO phản hồi kết quả thanh toán Checkout thành công trả về cho Frontend
        return orderResponseHelper.buildCheckoutDTO(order, transactionCode);
    }

    // API 3: NGƯỜI BÁN (SELLER) TRUY VẤN DANH SÁCH ĐƠN HÀNG BÁN ĐƯỢC
    // =========================================================================
    @Transactional(readOnly = true)
    public List<SellerOrderResponseDTO> getSellerOrders(Long sellerId, OrderStatus status) {
        // 1. Kiểm tra sự tồn tại của Người Bán trong hệ thống
        if (!userRepository.existsById(sellerId)) {
            throw new ApplicationException(ErrorCode.USER_NOT_FOUND);
        }
        // 2. Lấy danh sách đơn hàng do sellerId sở hữu (Hỗ trợ lọc tùy chọn theo tham số status)
        List<Order> orders = (status != null)
                ? orderRepository.findBySeller_IdAndStatusOrderByCreatedAtDesc(sellerId, status)
                : orderRepository.findBySeller_IdOrderByCreatedAtDesc(sellerId);
        // 3. Đưa danh sách Entity cho Helper đóng gói sang SellerOrderResponseDTO
        return orderResponseHelper.buildSellerOrderDTOList(orders);
    }

    // API 4: NGƯỜI BÁN BẤM NÚT XUẤT HÀNG / GIAO HÀNG (PAID -> SHIPPING)
    // =========================================================================
    @Transactional
    public SellerOrderResponseDTO shipOrder(Long orderId, Long sellerId, ShipOrderRequestDTO requestDTO) {
        // 1. Tìm thông tin đơn hàng theo orderId
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ApplicationException(ErrorCode.ORDER_NOT_FOUND));
        // 2. Gọi OrderValidator kiểm tra quy tắc: Bắt buộc chính chủ Người Bán và status BẮT BUỘC phải là PAID
        orderValidator.validateShipOrder(sellerId, order);
        // 3. Cập nhật tên đơn vị vận chuyển (courierName) và mã vận đơn (trackingNumber) do Seller nhập
        order.setCourierName(requestDTO.getCourierName());
        order.setTrackingNumber(requestDTO.getTrackingNumber());
        // 4. Chuyển trạng thái đơn sang SHIPPING
        order.setStatus(OrderStatus.SHIPPING);
        orderRepository.save(order);
        // 5. Gọi trực tiếp OrderMapper chuyển đổi đơn hàng sang DTO trả về cho Seller
        return orderMapper.toSellerOrderDTO(order);
    }


    // API 5: NGƯỜI MUA XÁC NHẬN "ĐÃ NHẬN HÀNG THÀNH CÔNG" (SHIPPING -> COMPLETED)
    // =========================================================================
    @Transactional
    public WonAuctionResponseDTO confirmReceived(Long orderId, Long buyerId) {
        // 1. Tìm thông tin đơn hàng theo orderId
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ApplicationException(ErrorCode.ORDER_NOT_FOUND));
        // 2. Gọi OrderValidator kiểm tra quy tắc: Bắt buộc đúng Người Mua và status BẮT BUỘC phải là SHIPPING
        orderValidator.validateConfirmReceived(buyerId, order);
        // 3. Đổi trạng thái đơn sang COMPLETED (Hoàn tất chu trình giao dịch & Giải ngân cho Seller)
        order.setStatus(OrderStatus.COMPLETED);
        orderRepository.save(order);
        // 4. Đóng gói DTO trả về thông báo hoàn tất thành công cho Người Mua
        return orderResponseHelper.buildWonAuctionDTO(order);
    }

}
