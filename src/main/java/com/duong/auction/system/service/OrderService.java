package com.duong.auction.system.service;

import com.duong.auction.system.client.PaymentFeignClient;
import com.duong.auction.system.dto.request.CheckoutRequestDTO;
import com.duong.auction.system.dto.request.PaymentRequestDTO;
import com.duong.auction.system.dto.request.ShipOrderRequestDTO;
import com.duong.auction.system.dto.response.CheckoutResponseDTO;
import com.duong.auction.system.dto.response.PaymentResponseDTO;
import com.duong.auction.system.dto.response.SellerOrderResponseDTO;
import com.duong.auction.system.dto.response.WonAuctionResponseDTO;
import com.duong.auction.system.entity.Order;
import com.duong.auction.system.entity.User;
import com.duong.auction.system.enums.OrderStatus;
import com.duong.auction.system.exception.ApplicationException;
import com.duong.auction.system.exception.ErrorCode;
import com.duong.auction.system.mapper.OrderMapper;
import com.duong.auction.system.repository.OrderRepository;
import com.duong.auction.system.repository.PaymentRepository;
import com.duong.auction.system.repository.UserRepository;
import com.duong.auction.system.service.helper.OrderPaymentTxHelper;
import com.duong.auction.system.service.helper.OrderResponseHelper;
import com.duong.auction.system.validator.OrderValidator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrderService {

    private final OrderRepository orderRepository;
    private final UserRepository userRepository;
    private final OrderResponseHelper orderResponseHelper;
    private final OrderValidator orderValidator;
    private final PaymentFeignClient paymentFeignClient;
    private final OrderPaymentTxHelper orderPaymentTxHelper;


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
        User buyer = getAuthenticatedUser();
        List<Order> orders = orderRepository.findByBuyer_IdOrderByCreatedAtDesc(buyer.getId());
        return orderResponseHelper.buildWonAuctionDTOList(orders);
    }

    // 2. NGƯỜI MUA CHỐT ĐỊA CHỈ & THANH TOÁN (CHECKOUT VỚI VÍ TIỀN ẢO VIA FEIGN & EUREKA)
    // 🟢 KHÔNG GẮN @Transactional ĐỂ TRÁNH GIỮ HIKARICP DB CONNECTION POOL TRONG 3S CHỜ MẠNG
    public CheckoutResponseDTO checkout(Long orderId, CheckoutRequestDTO requestDTO) {
        User buyer = getAuthenticatedUser();
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ApplicationException(ErrorCode.ORDER_NOT_FOUND));

        orderValidator.validateCheckout(buyer.getId(), order);

        // Đóng gói Request & Tạo Idempotency-Key
        String idempotencyKey = "PAY_ORDER_" + order.getId() + "_" + buyer.getId();
        PaymentRequestDTO paymentRequest = PaymentRequestDTO.builder()
                .orderId(order.getId())
                .userId(buyer.getId())
                .amount(order.getWinningPrice())
                .build();

        // 🔥 NGUYÊN TẮC VÀNG: GỌI FEIGN HTTP BÊN NGOÀI TRANSACTION
        PaymentResponseDTO paymentResponse = paymentFeignClient.processPayment(idempotencyKey, paymentRequest);

        // 🔥 GỌI QUA SPRING BEAN OrderPaymentTxHelper ĐỂ SPRING AOP PROXY KÍCH HOẠT @Transactional THỰC SỰ
        return orderPaymentTxHelper.updateOrderStatusAndSavePayment(order, requestDTO, paymentResponse);
    }

    // API 3: NGƯỜI BÁN (SELLER) TRUY VẤN DANH SÁCH ĐƠN HÀNG BÁN ĐƯỢC CỦA CHÍNH MÌNH
    @Transactional(readOnly = true)
    public List<SellerOrderResponseDTO> getSellerOrders(OrderStatus status) {
        User seller = getAuthenticatedUser();
        List<Order> orders = (status != null)
                ? orderRepository.findBySeller_IdAndStatusOrderByCreatedAtDesc(seller.getId(), status)
                : orderRepository.findBySeller_IdOrderByCreatedAtDesc(seller.getId());
        return orderResponseHelper.buildSellerOrderDTOList(orders);
    }

    // API 4: NGƯỜI BÁN BẤM NÚT XUẤT HÀNG / GIAO HÀNG (PAID -> SHIPPING)
    @Transactional
    public SellerOrderResponseDTO shipOrder(Long orderId, ShipOrderRequestDTO requestDTO) {
        User seller = getAuthenticatedUser();
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ApplicationException(ErrorCode.ORDER_NOT_FOUND));

        orderValidator.validateShipOrder(seller.getId(), order);

        order.setStatus(OrderStatus.SHIPPING);
        order.setCourierName(requestDTO.getCourierName());
        order.setTrackingNumber(requestDTO.getTrackingNumber());
        orderRepository.save(order);

        return orderResponseHelper.buildSellerOrderDTO(order);
    }

    // API 5: NGƯỜI MUA BẤM NÚT XÁC NHẬN "ĐÃ NHẬN HÀNG THÀNH CÔNG" (SHIPPING -> COMPLETED)
    @Transactional
    public WonAuctionResponseDTO confirmReceived(Long orderId) {
        User buyer = getAuthenticatedUser();
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ApplicationException(ErrorCode.ORDER_NOT_FOUND));

        orderValidator.validateConfirmReceived(buyer.getId(), order);

        order.setStatus(OrderStatus.COMPLETED);
        orderRepository.save(order);

        return orderResponseHelper.buildWonAuctionDTO(order);
    }
}
