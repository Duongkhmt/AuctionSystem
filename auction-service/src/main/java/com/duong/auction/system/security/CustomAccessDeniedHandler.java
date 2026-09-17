package com.duong.auction.system.security;

import com.duong.auction.system.exception.ErrorCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.duong.auction.system.exception.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Component
@RequiredArgsConstructor
public class CustomAccessDeniedHandler implements AccessDeniedHandler {

    private final ObjectMapper objectMapper;

    @Override
    public void handle(HttpServletRequest request,
                       HttpServletResponse response,
                       AccessDeniedException accessDeniedException) throws IOException {

        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);

        // 🟢 TÁI SỬ DỤNG 1002 (UNAUTHORIZED_ACCESS) CÓ SẴN TRONG DỰ ÁN
        ErrorResponse errorResponse = ErrorResponse.builder()
                .code(ErrorCode.UNAUTHORIZED_ACCESS.getCode())
                .status(HttpServletResponse.SC_FORBIDDEN)
                .message(ErrorCode.UNAUTHORIZED_ACCESS.getMessage())
                .build();

        objectMapper.writeValue(response.getOutputStream(), errorResponse);
    }
}

