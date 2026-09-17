package com.duong.auction.system.security;

import com.duong.auction.system.exception.ErrorCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.duong.auction.system.exception.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper;

    @Override
    public void commence(HttpServletRequest request,
                         HttpServletResponse response,
                         AuthenticationException authException) throws IOException {

        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);

        // 🟢 DÙNG 1004 (UNAUTHENTICATED) CHO LỖI CHƯA ĐĂNG NHẬP / TOKEN HẾT HẠN
        ErrorResponse errorResponse = ErrorResponse.builder()
                .code(ErrorCode.UNAUTHENTICATED.getCode())
                .status(HttpServletResponse.SC_UNAUTHORIZED)
                .message(ErrorCode.UNAUTHENTICATED.getMessage())
                .build();

        objectMapper.writeValue(response.getOutputStream(), errorResponse);
    }
}
