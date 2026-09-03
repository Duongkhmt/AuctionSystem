package com.duong.auction.system.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.times;

@ExtendWith(MockitoExtension.class)
@DisplayName("Unit Test Cho EmailService (String.format Text Block)")
class EmailServiceTest {

    @Mock
    private JavaMailSender mailSender;

    @InjectMocks
    private EmailService emailService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(emailService, "fromEmail", "noreply@auctionsystem.com");
        ReflectionTestUtils.setField(emailService, "frontendUrlPublic", "http://localhost:4200");
    }

    @Test
    @DisplayName("Gửi email thành công - SimpleMailMessage được khởi tạo và phát đi qua JavaMailSender")
    void sendAuctionWinnerEmail_Success_ShouldSendSimpleMailMessage() {
        emailService.sendAuctionWinnerEmail(
                "winner@example.com",
                "Nguyễn Văn A",
                "iPhone 15 Pro Max",
                BigDecimal.valueOf(15000000),
                88L,
                LocalDateTime.now().plusHours(48)
        );

        then(mailSender).should(times(1)).send(any(SimpleMailMessage.class));
    }
}
