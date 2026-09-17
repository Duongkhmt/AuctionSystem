package com.duong.auction.system.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * 📧 SERVICE GỬI EMAIL CHÚC MỪNG THẮNG THẦU
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmailService {

    private final JavaMailSender mailSender;

    @Value("${app.mail.from}")
    private String fromEmail;

    public void sendAuctionWinnerEmail(
            String toEmail,
            String winnerName,
            String productTitle,
            BigDecimal finalPrice,
            Long orderId,
            LocalDateTime paymentDeadline
    ) {
        try {
            String formattedPrice = NumberFormat.getCurrencyInstance(new Locale("vi", "VN")).format(finalPrice);
            String formattedDeadline = paymentDeadline.format(DateTimeFormatter.ofPattern("HH:mm - dd/MM/yyyy"));

            String emailBody = String.format("""
                Chào %s,

                Chúc mừng bạn đã trở thành người chiến thắng phiên đấu giá sản phẩm: %s!

                THÔNG TIN ĐƠN HÀNG THẮNG THẦU:
                - Mã Đơn Hàng: #%d
                - Giá Thắng Thầu: %s
                - Hạn Chót Thanh Toán: %s (Trong vòng 48 tiếng)

                Vui lòng đăng nhập vào hệ thống Sàn Đấu Giá để kiểm tra đơn hàng và tiến hành thanh toán.

                Lưu ý: Nếu quá hạn 48 tiếng bạn không thanh toán, đơn hàng sẽ tự động bị hủy theo quy định.

                Trân trọng,
                Auction System Team
                """,
                    winnerName != null ? winnerName : toEmail,
                    productTitle,
                    orderId,
                    formattedPrice,
                    formattedDeadline
            );

            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(fromEmail);
            message.setTo(toEmail);
            message.setSubject("🏆 Chúc mừng bạn đã thắng thầu sản phẩm: " + productTitle);
            message.setText(emailBody);

            mailSender.send(message);

            log.info(" [Email SUCCESS] Đã gửi thành công Email thông báo trúng thầu cho OrderId: #{}, tới Email: {}",
                    orderId, toEmail);

        } catch (Exception ex) {
            log.error(" [Email ERROR] Lỗi khi gửi Email cho OrderId: #{}, Email: {}. Detail: {}",
                    orderId, toEmail, ex.getMessage(), ex);
        }
    }
}
