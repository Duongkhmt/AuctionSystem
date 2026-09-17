package com.duong.auction.payment.security;

import com.duong.auction.payment.exception.ErrorCode;
import com.duong.auction.payment.exception.ErrorResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Filter bảo vệ API trừ tiền nội bộ POST /v1/payments/process-order-payment.
 * Bắt buộc request từ auction-service phải chứa Header X-Internal-Service-Key chính xác.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class InternalServiceSecurityFilter extends OncePerRequestFilter {

    public static final String HEADER_INTERNAL_KEY = "X-Internal-Service-Key";

    private final ObjectMapper objectMapper;

    // 🟢 Đã sửa: Đọc trực tiếp từ file config / biến môi trường .env (KHÔNG chứa chuỗi bí mật cứng dự phòng)
    @Value("${app.internal.service-key}")
    private String internalServiceKey;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) throws ServletException {
        String path = request.getRequestURI();
        return path.startsWith("/actuator") || path.startsWith("/error");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String requestKey = request.getHeader(HEADER_INTERNAL_KEY);

        //  Kiểm tra Key bí mật trong Header
        if (!StringUtils.hasText(requestKey) || !internalServiceKey.equals(requestKey)) {
            log.warn("[Forbidden Inter-Service] Request từ IP: {} bị từ chối do sai/thiếu Header bí mật!", request.getRemoteAddr());
            sendErrorResponse(response, HttpServletResponse.SC_FORBIDDEN, ErrorCode.UNAUTHORIZED_ACCESS);
            return;
        }

        // 🟢 Khóa hợp lệ -> Cho qua vào Controller
        filterChain.doFilter(request, response);
    }

    private void sendErrorResponse(HttpServletResponse response, int httpStatus, ErrorCode errorCode) throws IOException {
        response.setStatus(httpStatus);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        objectMapper.writeValue(response.getOutputStream(), new ErrorResponse(
                errorCode.getCode(),
                errorCode.getMessage(),
                httpStatus
        ));
    }
}
