package com.duong.auction.system.service;

import com.duong.auction.system.config.SecurityConstants;
import com.duong.auction.system.dto.request.LoginRequestDTO;
import com.duong.auction.system.dto.request.RefreshTokenRequestDTO;
import com.duong.auction.system.dto.request.RegisterRequestDTO;
import com.duong.auction.system.dto.response.UserResponseDTO;
import com.duong.auction.system.entity.User;
import com.duong.auction.system.enums.UserStatus;
import com.duong.auction.system.exception.ApplicationException;
import com.duong.auction.system.exception.ErrorCode;
import com.duong.auction.system.mapper.UserMapper;
import com.duong.auction.system.repository.UserRepository;
import com.duong.auction.system.security.CustomUserDetailsService;
import com.duong.auction.system.security.JwtTokenProvider;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Service xử lý toàn bộ nghiệp vụ Xác thực người dùng (Đăng ký, Đăng nhập, Refresh Token, Đăng xuất).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtTokenProvider tokenProvider;
    private final CustomUserDetailsService customUserDetailsService;
    private final StringRedisTemplate redisTemplate;
    private final UserMapper userMapper;

    @Value("${app.jwt.refresh-expiration-days:3}")
    private long refreshExpirationDays;

    /**
     * 🟢 1. ĐĂNG KÝ TÀI KHOẢN MỚI
     */
    @Transactional
    public UserResponseDTO register(RegisterRequestDTO request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new ApplicationException(ErrorCode.EMAIL_ALREADY_EXISTS);
        }
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new ApplicationException(ErrorCode.USERNAME_ALREADY_EXISTS);
        }

        String encodedPassword = passwordEncoder.encode(request.getPassword());
        User user = userMapper.toEntity(request, encodedPassword);
        userRepository.save(user);

        LoginRequestDTO loginRequest = new LoginRequestDTO();
        loginRequest.setEmail(request.getEmail());
        loginRequest.setPassword(request.getPassword());

        return login(loginRequest);
    }

    /**
     * 🟢 2. ĐĂNG NHẬP
     */
    public UserResponseDTO login(LoginRequestDTO request) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getEmail(), request.getPassword())
        );

        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new ApplicationException(ErrorCode.USER_NOT_FOUND));

        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new ApplicationException(ErrorCode.USER_BANNED_FROM_BIDDING);
        }

        String accessToken = tokenProvider.generateToken(authentication);
        String refreshToken = UUID.randomUUID().toString();

        redisTemplate.opsForValue().set(SecurityConstants.REFRESH_TOKEN_KEY_PREFIX + request.getEmail(), refreshToken, refreshExpirationDays, TimeUnit.DAYS);
        redisTemplate.opsForValue().set(SecurityConstants.REFRESH_TOKEN_USER_KEY_PREFIX + refreshToken, request.getEmail(), refreshExpirationDays, TimeUnit.DAYS);

        return userMapper.toUserResponseDTO(user, accessToken, refreshToken);
    }

    /**
     * 🟢 3. REFRESH TOKEN (ROTATION + RESET TTL + CHECK REAL-TIME STATUS)
     */
    public UserResponseDTO refreshToken(RefreshTokenRequestDTO request) {
        String oldRefreshToken = request.getRefreshToken();
        String email = redisTemplate.opsForValue().get(SecurityConstants.REFRESH_TOKEN_USER_KEY_PREFIX + oldRefreshToken);

        if (email == null) {
            throw new ApplicationException(ErrorCode.UNAUTHENTICATED);
        }

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ApplicationException(ErrorCode.USER_NOT_FOUND));

        if (user.getStatus() != UserStatus.ACTIVE) {
            redisTemplate.delete(SecurityConstants.REFRESH_TOKEN_KEY_PREFIX + email);
            redisTemplate.delete(SecurityConstants.REFRESH_TOKEN_USER_KEY_PREFIX + oldRefreshToken);
            throw new ApplicationException(ErrorCode.USER_BANNED_FROM_BIDDING);
        }

        // ROTATION: Xóa Refresh Token cũ
        redisTemplate.delete(SecurityConstants.REFRESH_TOKEN_USER_KEY_PREFIX + oldRefreshToken);

        // Nạp UserDetails và Authorities (ROLE_USER / ROLE_ADMIN) tránh mất quyền
        UserDetails userDetails = customUserDetailsService.loadUserByUsername(email);
        Authentication authentication = new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());

        String newAccessToken = tokenProvider.generateToken(authentication);
        String newRefreshToken = UUID.randomUUID().toString();

        redisTemplate.opsForValue().set(SecurityConstants.REFRESH_TOKEN_KEY_PREFIX + email, newRefreshToken, refreshExpirationDays, TimeUnit.DAYS);
        redisTemplate.opsForValue().set(SecurityConstants.REFRESH_TOKEN_USER_KEY_PREFIX + newRefreshToken, email, refreshExpirationDays, TimeUnit.DAYS);

        return userMapper.toUserResponseDTO(user, newAccessToken, newRefreshToken);
    }

    /**
     * 🟢 4. ĐĂNG XUẤT TÀI KHOẢN (AN TOÀN NULL-CHECK + REDIS BLACKLIST)
     */
    public void logout(HttpServletRequest request) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getName())) {
            throw new ApplicationException(ErrorCode.UNAUTHENTICATED);
        }

        String email = auth.getName();
        String authHeader = request.getHeader(SecurityConstants.HEADER_AUTHORIZATION);

        if (authHeader != null && authHeader.startsWith(SecurityConstants.TOKEN_PREFIX)) {
            String accessToken = authHeader.substring(SecurityConstants.TOKEN_PREFIX.length());

            String refreshToken = redisTemplate.opsForValue().get(SecurityConstants.REFRESH_TOKEN_KEY_PREFIX + email);
            if (refreshToken != null) {
                redisTemplate.delete(SecurityConstants.REFRESH_TOKEN_KEY_PREFIX + email);
                redisTemplate.delete(SecurityConstants.REFRESH_TOKEN_USER_KEY_PREFIX + refreshToken);
            }

            redisTemplate.opsForValue().set(SecurityConstants.BLACKLIST_TOKEN_KEY_PREFIX + accessToken, "invalidated", 10, TimeUnit.MINUTES);
            SecurityContextHolder.clearContext();
            log.info("Đã đăng xuất thành công cho tài khoản Email: {}", email);
        }
    }
}
