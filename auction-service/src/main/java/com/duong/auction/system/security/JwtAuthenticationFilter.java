package com.duong.auction.system.security;

import com.duong.auction.system.config.DateTimeConfig;
import com.duong.auction.system.config.SecurityConstants;
import com.duong.auction.system.entity.User;
import com.duong.auction.system.exception.ErrorCode;
import com.duong.auction.system.exception.ErrorResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * Filter đánh chặn mọi HTTP Request gửi lên Server để kiểm tra JWT Token,
 * đánh giá trạng thái tài khoản (Active/Banned) và mốc thời gian Đăng xuất (Logout).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenProvider tokenProvider;
    private final CustomUserDetailsService customUserDetailsService;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        try {
            // 🟢 Bước 1: Trích xuất chuỗi Bearer JWT Token từ HTTP Header Authorization
            String jwt = getJwtFromRequest(request);

            // 🟢 Bước 2: Kiểm tra Token có tồn tại và hợp lệ về chữ ký HMAC-SHA256 & hết hạn 10m chưa
            if (StringUtils.hasText(jwt) && tokenProvider.validateToken(jwt)) {

                // 🟢 Trích xuất Email người dùng sở hữu Token
                String email = tokenProvider.getUsernameFromToken(jwt);

                // 🔴 Check 1: Kiểm tra nick bị Admin khóa (BANNED) từ Redis Cache (Chặn siêu tốc 0ms SQL)
                if (isUserBanned(email)) {
                    log.warn("⚠️ [Redis Check] Tài khoản Email: {} đã bị ngưng hoạt động bởi Admin!", email);
                    sendErrorResponse(response, HttpServletResponse.SC_FORBIDDEN, ErrorCode.USER_BANNED_FROM_BIDDING);
                    return;
                }

                // 🟢 Bước 3: Nạp thông tin UserDetails từ CSDL PostgreSQL (Nguồn sự thật chính)
                UserDetails userDetails = customUserDetailsService.loadUserByUsername(email);

                // 🔴 Check 1.5 (DB Fallback cho Banned User): Nếu Redis bị sập/cache miss ➔ Đọc trực tiếp status BANNED từ CSDL PostgreSQL
                if (!userDetails.isAccountNonLocked() || !userDetails.isEnabled()) {
                    log.warn("⚠️ [DB Check] Tài khoản Email: {} đã bị ngưng hoạt động bởi Admin (CSDL PostgreSQL)!", email);
                    sendErrorResponse(response, HttpServletResponse.SC_FORBIDDEN, ErrorCode.USER_BANNED_FROM_BIDDING);
                    return;
                }

                // 🔴 Check 2: Kiểm tra Token có được cấp trước mốc Logout không (Kết hợp Redis Cache + Fallback CSDL)
                if (isTokenRevokedByLogout(email, jwt, userDetails)) {
                    log.warn("⚠️ Token của Email: {} được cấp trước mốc thời gian Logout!", email);
                    sendErrorResponse(response, HttpServletResponse.SC_UNAUTHORIZED, ErrorCode.UNAUTHENTICATED);
                    return;
                }

                // 🟢 Bước 4: Tạo đối tượng Authentication chuẩn của Spring Security và nạp vào SecurityContextHolder
                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());

                authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

                SecurityContextHolder.getContext().setAuthentication(authentication);
            }
        } catch (UsernameNotFoundException ex) {
            // 🟡 Người dùng bị xóa khỏi CSDL trong khi Token chưa hết hạn: Log cảnh báo
            log.warn("Không tìm thấy người dùng từ JWT Token: {}", ex.getMessage());
        } catch (Exception ex) {
            // 🔴 Lỗi hệ thống hoặc hạ tầng không xác định
            log.error("Không thể thiết lập xác thực người dùng trong Security Context", ex);
        }

        // 🟢 Chuyển Request đi tiếp tới các Filter hoặc Controller tiếp theo
        filterChain.doFilter(request, response);
    }

    /**
     * 🟢 Helper 1: Đọc trạng thái tài khoản từ Redis Cache (user:status:{email}).
     * Nếu là "BANNED" ➔ Chặn ngay lập tức từ RAM không cần chọc DB.
     */
    private boolean isUserBanned(String email) {
        String userStatus = redisTemplate.opsForValue().get(SecurityConstants.USER_STATUS_KEY_PREFIX + email);
        return "BANNED".equals(userStatus);
    }

    /**
     * 🟢 Helper 2: Đánh giá Token có bị hủy do Logout không (Đa tầng Redis + Fallback CSDL PostgreSQL).
     * @param email Email người dùng.
     * @param jwt Chuỗi Token gửi lên.
     * @param userDetails Đối tượng UserDetails đã nạp từ CSDL.
     * @return true nếu Token sinh ra trước thời điểm Logout (cần hủy Token).
     */
    private boolean isTokenRevokedByLogout(String email, String jwt, UserDetails userDetails) {
        Date tokenIssuedAt = tokenProvider.getIssuedAtFromToken(jwt);
        long clockSkewBufferMs = 1000L; // Buffer 1s triệt tiêu lỗi lệch ms giữa các luồng

        // 1. Kiểm tra mốc Logout từ Redis Cache tốc độ cao (user:logout_at:{email})
        String lastLogoutStr = redisTemplate.opsForValue().get(SecurityConstants.USER_LOGOUT_AT_KEY_PREFIX + email);
        if (lastLogoutStr != null) {
            long lastLogoutAt = Long.parseLong(lastLogoutStr);
            return tokenIssuedAt == null || tokenIssuedAt.getTime() < (lastLogoutAt - clockSkewBufferMs);
        }

        // 2. Fallback CSDL PostgreSQL (Khi Redis bị sập / Cache Miss): Kiểm tra cột lastLogoutAt trong DB
        if (userDetails instanceof UserCustomDetails customDetails && customDetails.getUser().getLastLogoutAt() != null) {
            long dbLastLogoutAt = customDetails.getUser().getLastLogoutAt()
                    .atZone(DateTimeConfig.DEFAULT_ZONE)
                    .toInstant()
                    .toEpochMilli();
            return tokenIssuedAt == null || tokenIssuedAt.getTime() < (dbLastLogoutAt - clockSkewBufferMs);
        }

        return false; // Token hoàn toàn hợp lệ
    }

    /**
     * 🟢 Helper 3: Đóng gói và ghi ErrorResponse chuẩn định dạng JSON của dự án ra Response Stream.
     * Tái sử dụng MediaType.APPLICATION_JSON_VALUE để xóa sạch lỗi SonarQube lặp hằng số.
     */
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

    /**
     * Helper trích xuất chuỗi JWT bỏ đi tiền tố "Bearer " từ Header Authorization.
     */
    private String getJwtFromRequest(HttpServletRequest request) {
        String bearerToken = request.getHeader(SecurityConstants.HEADER_AUTHORIZATION);
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith(SecurityConstants.TOKEN_PREFIX)) {
            return bearerToken.substring(SecurityConstants.TOKEN_PREFIX.length());
        }
        return null;
    }
}
