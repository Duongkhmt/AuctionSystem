package com.duong.auction.system.config;

import com.duong.auction.system.security.CustomAccessDeniedHandler;
import com.duong.auction.system.security.JwtAuthenticationEntryPoint;
import com.duong.auction.system.security.JwtAuthenticationFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * Class cấu hình tổng trung tâm chỉ huy bảo mật Spring Security và phân chia tuyến đường RBAC.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity // 🟢 Bật phân quyền cấp phương thức với @PreAuthorize (chống lỗ hổng BOLA/IDOR)
@RequiredArgsConstructor
public class SecurityConfig {

    // 🟢 Tiêm 2 Handler xử lý lỗi JSON 401 & 403 (Bước 7) và Custom JWT Filter (Bước 6)
    private final JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint;
    private final CustomAccessDeniedHandler customAccessDeniedHandler;
    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    /**
     * 🟢 1. Bean mã hóa mật khẩu chuẩn BCrypt (Salt ngẫu nhiên + Work Factor = 10 chống Brute Force).
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * 🟢 2. Bean quản lý quy trình xác thực đăng nhập phục vụ cho AuthController / AuthService.
     */
    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration authConfig) {
        try {
            return authConfig.getAuthenticationManager();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * 🟢 3. Chuỗi Filter xác thực và phân chia tuyến đường phân quyền Route RBAC.
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) {
        try {
            http
                    // 🟢 Cấu hình CORS cho phép Frontend Angular gọi API
                    .cors(cors -> cors.configurationSource(corsConfigurationSource()))

                    // 🟢 Vô hiệu hóa CSRF vì ứng dụng sử dụng Stateless JWT Header Authorization
                    .csrf(AbstractHttpConfigurer::disable)

                    // 🟢 Chuyển Session sang STATELESS (Không lưu Session trên Server RAM)
                    .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

                    // 🟢 Đăng ký 2 Handler trả về phản hồi lỗi JSON 401 & 403 chuẩn REST API
                    .exceptionHandling(exceptions -> exceptions
                            .authenticationEntryPoint(jwtAuthenticationEntryPoint)
                            .accessDeniedHandler(customAccessDeniedHandler)
                    )

                    // 🟢 Cấu hình Phân quyền Tuyến đường Endpoint (RBAC Routes)
                    .authorizeHttpRequests(auth -> auth
                            // 🟢 1. PUBLIC ENDPOINTS (Cho phép tất cả khách chưa đăng nhập truy cập)
                            .requestMatchers("/v1/auth/**").permitAll()
                            .requestMatchers("/v1/test/**").permitAll()
                            .requestMatchers(HttpMethod.GET, "/v1/products/**").permitAll()
                            .requestMatchers(HttpMethod.GET, "/v1/auctions/**").permitAll()
                            .requestMatchers(HttpMethod.GET, "/v1/categories/**").permitAll()

                            // 🟢 2. USER ENDPOINTS (Tài khoản USER - Vừa Đặt Giá / Mua vừa Đăng Bán Hàng)
                            .requestMatchers(HttpMethod.POST, "/v1/auctions/*/bids").hasRole("USER")
                            .requestMatchers(HttpMethod.POST, "/v1/auctions/*/buy-now").hasRole("USER")
                            .requestMatchers(HttpMethod.POST, "/v1/products").hasRole("USER")
                            .requestMatchers(HttpMethod.PUT, "/v1/products/*").hasRole("USER")
                            .requestMatchers("/v1/bidders/**").hasRole("USER")
                            .requestMatchers("/v1/sellers/**").hasRole("USER")
                            .requestMatchers("/v1/orders/*/checkout").hasRole("USER")

                            // 🟢 3. ADMIN ENDPOINTS (Quản trị viên hệ thống)
                            .requestMatchers("/v1/admin/**").hasRole("ADMIN")
                            .requestMatchers(HttpMethod.POST, "/v1/categories").hasRole("ADMIN")

                            // 🟢 Tất cả các API còn lại chưa khai báo bắt buộc phải đăng nhập
                            .anyRequest().authenticated()
                    )

                    // 🟢 Chèn Custom JwtAuthenticationFilter đứng trước UsernamePasswordAuthenticationFilter
                    .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

            return http.build();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
    /**
     * 🟢 Cấu hình CORS cho phép các domain Frontend (dù chạy ở localhost hay Domain khác) truy cập.
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOriginPatterns(List.of("*"));
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
