package com.duong.auction.system.security;

import com.duong.auction.system.config.SecurityConstants;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Filter đánh chặn mọi HTTP Request để xác thực JWT Token và nạp Authentication vào SecurityContext.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenProvider tokenProvider;
    private final CustomUserDetailsService customUserDetailsService;
    private final StringRedisTemplate redisTemplate;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        try {


            // 1. Trích xuất Bearer Token từ HTTP Header Authorization
            String jwt = getJwtFromRequest(request);

            // 2. Kiểm tra chuỗi Token có tồn tại và hợp lệ (chữ ký chuẩn, còn hạn 10 phút)
            if (StringUtils.hasText(jwt) && tokenProvider.validateToken(jwt)) {

                // Kiểm tra Redis Blacklist
                Boolean isBlacklisted = redisTemplate.hasKey("blacklist_token:" + jwt);
                if (Boolean.TRUE.equals(isBlacklisted)) {
                    log.warn("⚠️ Token này đã bị thu hồi do người dùng Đăng xuất (Blacklisted)!");
                    filterChain.doFilter(request, response);
                    return;
                }

                // Trích xuất Email từ JWT Token
                String email = tokenProvider.getUsernameFromToken(jwt);

                // Nạp thông tin UserDetails từ CSDL theo Email
                UserDetails userDetails = customUserDetailsService.loadUserByUsername(email);

                // Tạo đối tượng Authentication chuẩn của Spring Security
                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());

                authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

                // 3. Nạp Authentication vào SecurityContextHolder của luồng (ThreadLocal) hiện tại
                SecurityContextHolder.getContext().setAuthentication(authentication);
            }
        } catch (UsernameNotFoundException ex) {
            // 🟡 Người dùng bị xóa khỏi CSDL trong khi Token chưa hết hạn: Log Warn nhẹ nhàng
            log.warn("Không tìm thấy người dùng từ JWT Token: {}", ex.getMessage());
        } catch (Exception ex) {
            // 🔴 Lỗi hạ tầng hoặc hệ thống không xác định khác
            log.error("Không thể thiết lập xác thực người dùng trong Security Context", ex);
        }

        // 4. Chuyển Request đi tiếp tới các Filter tiếp theo hoặc Controller
        filterChain.doFilter(request, response);
    }

    // Helper trích xuất chuỗi JWT bỏ đi tiền tố "Bearer "
    private String getJwtFromRequest(HttpServletRequest request) {
        String bearerToken = request.getHeader(SecurityConstants.HEADER_AUTHORIZATION);
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith(SecurityConstants.TOKEN_PREFIX)) {
            return bearerToken.substring(SecurityConstants.TOKEN_PREFIX.length());
        }
        return null;
    }
}
