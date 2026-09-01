package com.duong.auction.system.service;

import com.duong.auction.system.dto.request.CheckoutRequestDTO;
import com.duong.auction.system.dto.request.ShipOrderRequestDTO;
import com.duong.auction.system.dto.response.CheckoutResponseDTO;
import com.duong.auction.system.dto.response.SellerOrderResponseDTO;
import com.duong.auction.system.dto.response.WonAuctionResponseDTO;
import com.duong.auction.system.entity.Order;
import com.duong.auction.system.entity.Payment;
import com.duong.auction.system.entity.User;
import com.duong.auction.system.enums.OrderStatus;
import com.duong.auction.system.enums.PaymentStatus;
import com.duong.auction.system.exception.ApplicationException;
import com.duong.auction.system.exception.ErrorCode;
import com.duong.auction.system.mapper.OrderMapper;
import com.duong.auction.system.repository.OrderRepository;
import com.duong.auction.system.repository.PaymentRepository;
import com.duong.auction.system.repository.UserRepository;
import com.duong.auction.system.service.helper.OrderResponseHelper;
import com.duong.auction.system.validator.OrderValidator;
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


    private User getAuthenticatedUser() {
        org.springframework.security.core.Authentication auth = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof com.duong.auction.system.security.UserCustomDetails userCustomDetails) {
            return userCustomDetails.getUser();
        }
        String email = (auth != null) ? auth.getName() : null;
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ApplicationException(ErrorCode.USER_NOT_FOUND));
    }

    // 1. NGƯỜI MUA TRUY VẤN DANH SÁCH SẢN PHẨM ĐÃ TRÚNG THẦU CỦA CHÍNH MÌNH
    @Transactional(readOnly = true)
    public List<WonAuctionResponseDTO> getWonAuctions() {
        // 1. Lấy thông tin Người Mua đang đăng nhập từ SecurityContext
        User buyer = getAuthenticatedUser();
        // 2. Truy vấn danh sách các đơn hàng trúng thầu mà buyer_id = buyer.getId()
        List<Order> orders = orderRepository.findByBuyer_IdOrderByCreatedAtDesc(buyer.getId());
        // 3. Đưa danh sách Entity cho Helper đóng gói DTO
        return orderResponseHelper.buildWonAuctionDTOList(orders);
    }

    // 2. NGƯỜI MUA CHỐT ĐỊA CHỈ & THANH TOÁN (CHECKOUT)
    @Transactional
    public CheckoutResponseDTO checkout(Long orderId, CheckoutRequestDTO requestDTO) {
        User buyer = getAuthenticatedUser();
        // 1. Tìm thông tin đơn hàng theo orderId
        Order order = orderRepository.findById(orderId).orElseThrow(() -> new ApplicationException(ErrorCode.ORDER_NOT_FOUND));

        // 2. Gọi OrderValidator kiểm tra quy tắc: Bắt buộc chính chủ Người Mua đang đăng nhập
        orderValidator.validateCheckout(buyer.getId(), order);

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

    // API 3: NGƯỜI BÁN (SELLER) TRUY VẤN DANH SÁCH ĐƠN HÀNG BÁN ĐƯỢC CỦA CHÍNH MÌNH
    // =========================================================================
    @Transactional(readOnly = true)
    public List<SellerOrderResponseDTO> getSellerOrders(OrderStatus status) {
        User seller = getAuthenticatedUser();
        // Lấy danh sách đơn hàng do Seller đang đăng nhập sở hữu
        List<Order> orders = (status != null)
                ? orderRepository.findBySeller_IdAndStatusOrderByCreatedAtDesc(seller.getId(), status)
                : orderRepository.findBySeller_IdOrderByCreatedAtDesc(seller.getId());
        return orderResponseHelper.buildSellerOrderDTOList(orders);
    }

    // API 4: NGƯỜI BÁN BẤM NÚT XUẤT HÀNG / GIAO HÀNG (PAID -> SHIPPING)
    // =========================================================================
    @Transactional
    public SellerOrderResponseDTO shipOrder(Long orderId, ShipOrderRequestDTO requestDTO) {
        User seller = getAuthenticatedUser();
        // 1. Tìm thông tin đơn hàng theo orderId
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ApplicationException(ErrorCode.ORDER_NOT_FOUND));
        // 2. Gọi OrderValidator kiểm tra quy tắc: Bắt buộc chính chủ Người Bán đang đăng nhập
        orderValidator.validateShipOrder(seller.getId(), order);
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
    public WonAuctionResponseDTO confirmReceived(Long orderId) {
        User buyer = getAuthenticatedUser();
        // 1. Tìm thông tin đơn hàng theo orderId
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ApplicationException(ErrorCode.ORDER_NOT_FOUND));
        // 2. Gọi OrderValidator kiểm tra quy tắc: Bắt buộc đúng Người Mua đang đăng nhập
        orderValidator.validateConfirmReceived(buyer.getId(), order);
        // 3. Đổi trạng thái đơn sang COMPLETED (Hoàn tất chu trình giao dịch & Giải ngân cho Seller)
        order.setStatus(OrderStatus.COMPLETED);
        orderRepository.save(order);
        // 4. Đóng gói DTO trả về thông báo hoàn tất thành công cho Người Mua
        return orderResponseHelper.buildWonAuctionDTO(order);
    }

}
