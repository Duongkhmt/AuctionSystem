package com.duong.auction.payment.config;

import com.duong.auction.payment.security.InternalServiceSecurityFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Cấu hình Bảo mật duy nhất cho PAYMENT-SERVICE (Server-to-Server qua X-Internal-Service-Key).
 * Vì PAYMENT-SERVICE chỉ phục vụ các cuộc gọi nội bộ từ auction-service (không tiếp nhận trực tiếp từ trình duyệt),
 * nên không cần cấu hình CORS (Cross-Origin Resource Sharing).
 */
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class PaymentSecurityConfig {

    private final InternalServiceSecurityFilter internalServiceSecurityFilter;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/**", "/error").permitAll()
                        .anyRequest().permitAll() // Đã kiểm tra X-Internal-Service-Key bởi InternalServiceSecurityFilter
                )
                .addFilterBefore(internalServiceSecurityFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    /**
     * Tắt tự động đăng ký InternalServiceSecurityFilter làm Servlet Filter toàn cục (/*).
     * Chỉ cho phép filter này chạy trong Spring Security FilterChain.
     */
    @Bean
    public org.springframework.boot.web.servlet.FilterRegistrationBean<InternalServiceSecurityFilter> internalServiceSecurityFilterRegistration(InternalServiceSecurityFilter filter) {
        org.springframework.boot.web.servlet.FilterRegistrationBean<InternalServiceSecurityFilter> registration = new org.springframework.boot.web.servlet.FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }
}
