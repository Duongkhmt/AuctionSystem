package com.duong.auction.system.service.helper;

import com.duong.auction.system.dto.request.CheckoutRequestDTO;
import com.duong.auction.system.dto.response.CheckoutResponseDTO;
import com.duong.auction.system.dto.response.PaymentResponseDTO;
import com.duong.auction.system.entity.Order;
import com.duong.auction.system.entity.Payment;
import com.duong.auction.system.entity.User;
import com.duong.auction.system.enums.OrderStatus;
import com.duong.auction.system.enums.PaymentMethod;
import com.duong.auction.system.enums.PaymentStatus;
import com.duong.auction.system.exception.ApplicationException;
import com.duong.auction.system.exception.ErrorCode;
import com.duong.auction.system.repository.OrderRepository;
import com.duong.auction.system.repository.PaymentRepository;
import com.duong.auction.system.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class OrderPaymentTxHelper {

    private final OrderRepository orderRepository;
    private final PaymentRepository paymentRepository;
    private final UserRepository userRepository;
    private final OrderResponseHelper orderResponseHelper;
    private final Clock clock;

    /**
     * Hàm mở Database Transaction thực sự
     */
    @Transactional
    public CheckoutResponseDTO updateOrderStatusAndSavePayment(Order order, CheckoutRequestDTO requestDTO, PaymentResponseDTO paymentResponse) {
        OrderStatus previousStatus = order.getStatus();
        // ROOT LEVEL GUARD: Chỉ xử lý thanh toán bình thường nếu đơn ĐANG CHỜ THANH TOÁN (UNPAID hoặc PAYMENT_PENDING_RETRY)
        if (previousStatus != OrderStatus.UNPAID && previousStatus != OrderStatus.PAYMENT_PENDING_RETRY) {
            log.warn("Order ID {} đang ở trạng thái kết thúc/hoạt động [{}]. Bỏ qua toàn bộ cập nhật thanh toán.",
                    order.getId(), previousStatus);
            // ĐẶC BIỆT: Nếu đơn đã CANCELLED nhưng tiền lỡ bị trừ (SUCCESS) -> Bắn Cảnh báo Cấp Đỏ để HOÀN TIỀN (REFUND)
            if (previousStatus == OrderStatus.CANCELLED && paymentResponse.getStatus() == PaymentStatus.SUCCESS) {
                log.error("CRITICAL ERROR: Đơn hàng ID {} đã bị HỦY (CANCELLED) nhưng Payment-Service lại trừ tiền THÀNH CÔNG! Cần kích hoạt quy trình HOÀN TIỀN (REFUND)!", order.getId());
            }
            Payment existingPayment = paymentRepository.findByOrder_Id(order.getId()).orElse(null);
            String txnCode = existingPayment != null ? existingPayment.getTransactionCode() : "N/A";
            return orderResponseHelper.buildCheckoutDTO(order, txnCode);
        }
        // 1. Cập nhật thông tin nhận hàng (cho đơn hợp lệ)
        applyShippingInfo(order, requestDTO);
        // 2. Cập nhật trạng thái đơn hàng
        updateOrderStatus(order, paymentResponse, previousStatus);
        // 3. Cập nhật/Lưu bản ghi Payment
        Payment payment = savePaymentRecord(order, requestDTO, paymentResponse);
        // 4. Lưu Order xuống CSDL trong cùng 1 Transaction
        orderRepository.save(order);
        return orderResponseHelper.buildCheckoutDTO(order, payment.getTransactionCode());
    }


    /**
     * 🟢 HÀM MỚI: Hủy đơn quá hạn & Phạt gậy người mua trong 1 Transaction NGUYÊN TỬ (Atomic)
     */
    @Transactional
    public void cancelExpiredOrderAndPenalizeBuyer(Order order, OrderStatus oldStatus, LocalDateTime now) {
        order.setStatus(OrderStatus.CANCELLED);
        orderRepository.save(order);
        User buyer = order.getBuyer();
        // CHỈ PHẠT STRIKE NẾU ĐƠN LÀ UNPAID (Khách cố tình bùng quá 48h)
        // Đơn PAYMENT_PENDING_RETRY do lỗi Payment Service sập -> MIỄN PHẠT STRIKE!
        if (buyer != null && oldStatus == OrderStatus.UNPAID) {
            int strikes = (buyer.getUnpaidStrikeCount() != null ? buyer.getUnpaidStrikeCount() : 0) + 1;
            buyer.setUnpaidStrikeCount(strikes);
            if (strikes >= 3) {
                buyer.setBannedUntil(now.plusDays(90));
            }
            userRepository.save(buyer);
            log.warn("Phạt +1 Gậy cho người mua ID {} do bùng đơn UNPAID quá 48h (Tổng gậy: {})", buyer.getId(), strikes);
        } else if (oldStatus == OrderStatus.PAYMENT_PENDING_RETRY) {
            log.info("Hủy đơn ID {} do quá hạn 24h gia hạn. Miễn phạt gậy cho người mua ID {} (Do lỗi hệ thống Payment-Service)",
                    order.getId(), buyer != null ? buyer.getId() : "N/A");
        }
    }

    // Cập nhật địa chỉ nhận hàng nếu người dùng gửi lên
    private void applyShippingInfo(Order order, CheckoutRequestDTO requestDTO) {
        if (requestDTO != null) {
            order.setShippingAddress(requestDTO.getShippingAddress());
            order.setPhoneNumber(requestDTO.getPhoneNumber());
        }
    }

    private void updateOrderStatus(Order order, PaymentResponseDTO paymentResponse, OrderStatus previousStatus) {
        if (paymentResponse.getStatus() == PaymentStatus.SUCCESS) {
            order.setStatus(OrderStatus.PAID);
        } else if (paymentResponse.getStatus() == PaymentStatus.INSUFFICIENT_BALANCE) {
            throw new ApplicationException(ErrorCode.INSUFFICIENT_WALLET_BALANCE);
        } else if (paymentResponse.getStatus() == PaymentStatus.PENDING_RETRY) {
            order.setStatus(OrderStatus.PAYMENT_PENDING_RETRY);
            if (order.getPaymentDeadline() == null || previousStatus != OrderStatus.PAYMENT_PENDING_RETRY) {
                order.setPaymentDeadline(LocalDateTime.now(clock).plusDays(1));
                log.info("Đơn hàng ID: {} được gia hạn thêm 24h (Deadline mới: {})", order.getId(), order.getPaymentDeadline());
            }
        } else {
            log.error("Unexpected payment status: {} for order ID: {}", paymentResponse.getStatus(), order.getId());
            throw new ApplicationException(ErrorCode.PAYMENT_UNEXPECTED_RESPONSE);
        }
    }

    // Lưu hoặc cập nhật bản ghi Payment
    private Payment savePaymentRecord(Order order, CheckoutRequestDTO requestDTO, PaymentResponseDTO paymentResponse) {
        Payment payment = paymentRepository.findByOrder_Id(order.getId())
                .orElseGet(Payment::new);

        payment.setOrder(order);
        payment.setAmount(order.getWinningPrice());
        payment.setPaymentMethod(requestDTO != null && requestDTO.getPaymentMethod() != null ? requestDTO.getPaymentMethod() : PaymentMethod.WALLET);

        if (payment.getTransactionCode() == null || payment.getTransactionCode().isBlank()) {
            payment.setTransactionCode("TXN-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        }

        // Bảo vệ State Machine Payment: Nếu đã SUCCESS thì giữ nguyên
        if (payment.getStatus() != PaymentStatus.SUCCESS) {
            payment.setStatus(paymentResponse.getStatus());
        }

        return paymentRepository.save(payment);
    }
}
