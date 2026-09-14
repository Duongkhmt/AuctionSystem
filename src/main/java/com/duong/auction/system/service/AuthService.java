package com.duong.auction.system.service;

import com.duong.auction.system.config.DateTimeConfig;
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

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
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
        // 1. Kiểm tra Email đã tồn tại chưa
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new ApplicationException(ErrorCode.EMAIL_ALREADY_EXISTS);
        }
        // 2. Kiểm tra Username đã tồn tại chưa
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new ApplicationException(ErrorCode.USERNAME_ALREADY_EXISTS);
        }
        // 3. Mã hóa mật khẩu bằng BCrypt và lưu User mới xuống PostgreSQL
        String encodedPassword = passwordEncoder.encode(request.getPassword());
        User user = userMapper.toEntity(request, encodedPassword);
        userRepository.save(user);
        // 4. Tự động đăng nhập cho User vừa tạo tài khoản thành công
        LoginRequestDTO loginRequest = new LoginRequestDTO();
        loginRequest.setEmail(request.getEmail());
        loginRequest.setPassword(request.getPassword());
        // 🚀 Gọi trực tiếp hàm login() bên dưới!
        return login(loginRequest);
    }

    /**
     * 🟢 2. ĐĂNG NHẬP
     */

    private String hashToken(String rawToken) {
        if (rawToken == null) return null;
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hashBytes) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            log.error("Không tìm thấy thuật toán SHA-256", e);
            throw new ApplicationException(ErrorCode.UNCATEGORIZED_EXCEPTION);
        }
    }

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

        // 1. Sinh chuỗi UUID thô gửi về cho Client
        String rawRefreshToken = UUID.randomUUID().toString();

        // 2. Băm SHA-256 chuỗi UUID trước khi lưu lên Redis
        String hashedRefreshToken = hashToken(rawRefreshToken);
        // 3. Ghi bản băm lên Redis RAM (An toàn tuyệt đối nếu Redis bị rò rỉ)
        redisTemplate.opsForValue().set(SecurityConstants.REFRESH_TOKEN_KEY_PREFIX + request.getEmail(), hashedRefreshToken, refreshExpirationDays, TimeUnit.DAYS);
        redisTemplate.opsForValue().set(SecurityConstants.REFRESH_TOKEN_USER_KEY_PREFIX + hashedRefreshToken, request.getEmail(), refreshExpirationDays, TimeUnit.DAYS);
        redisTemplate.opsForValue().set(SecurityConstants.USER_STATUS_KEY_PREFIX + request.getEmail(), user.getStatus().name(), refreshExpirationDays, TimeUnit.DAYS);
        // 4. Trả chuỗi UUID thô (rawRefreshToken) cho Client lưu giữ
        return userMapper.toUserResponseDTO(user, accessToken, rawRefreshToken);
    }

    /**
     * 🟢 3. REFRESH TOKEN (ROTATION + RESET TTL + CHECK REAL-TIME STATUS)
     */
    public UserResponseDTO refreshToken(RefreshTokenRequestDTO request) {
        String oldRawRefreshToken = request.getRefreshToken();

        // 1. Băm chuỗi token cũ do Client gửi lên
        String oldHashedToken = hashToken(oldRawRefreshToken);

        // 2. Tra cứu email bằng bản băm oldHashedToken trên Redis
        String email = redisTemplate.opsForValue().get(SecurityConstants.REFRESH_TOKEN_USER_KEY_PREFIX + oldHashedToken);
        if (email == null) {
            throw new ApplicationException(ErrorCode.UNAUTHENTICATED);
        }
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ApplicationException(ErrorCode.USER_NOT_FOUND));
        if (user.getStatus() != UserStatus.ACTIVE) {
            redisTemplate.delete(SecurityConstants.REFRESH_TOKEN_KEY_PREFIX + email);
            redisTemplate.delete(SecurityConstants.REFRESH_TOKEN_USER_KEY_PREFIX + oldHashedToken);
            throw new ApplicationException(ErrorCode.USER_BANNED_FROM_BIDDING);
        }
        // 3. ROTATION: Xóa bản băm Refresh Token cũ trên Redis
        redisTemplate.delete(SecurityConstants.REFRESH_TOKEN_USER_KEY_PREFIX + oldHashedToken);
        UserDetails userDetails = customUserDetailsService.loadUserByUsername(email);
        Authentication authentication = new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
        String newAccessToken = tokenProvider.generateToken(authentication);

        // 4. Sinh token mới & Băm SHA-256 token mới
        String newRawRefreshToken = UUID.randomUUID().toString();
        String newHashedToken = hashToken(newRawRefreshToken);
        // 5. Ghi bản băm mới lên Redis RAM
        redisTemplate.opsForValue().set(SecurityConstants.REFRESH_TOKEN_KEY_PREFIX + email, newHashedToken, refreshExpirationDays, TimeUnit.DAYS);
        redisTemplate.opsForValue().set(SecurityConstants.REFRESH_TOKEN_USER_KEY_PREFIX + newHashedToken, email, refreshExpirationDays, TimeUnit.DAYS);
        // 6. Trả chuỗi UUID thô mới (newRawRefreshToken) về cho Client
        return userMapper.toUserResponseDTO(user, newAccessToken, newRawRefreshToken);
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
            // Lấy bản băm hashedToken từ key email ra để xóa key ngược refresh_token_user
            String hashedToken = redisTemplate.opsForValue().get(SecurityConstants.REFRESH_TOKEN_KEY_PREFIX + email);
            if (hashedToken != null) {
                redisTemplate.delete(SecurityConstants.REFRESH_TOKEN_KEY_PREFIX + email);
                redisTemplate.delete(SecurityConstants.REFRESH_TOKEN_USER_KEY_PREFIX + hashedToken);
            }
            long currentTimestamp = System.currentTimeMillis();
            redisTemplate.opsForValue().set(SecurityConstants.USER_LOGOUT_AT_KEY_PREFIX + email, String.valueOf(currentTimestamp), refreshExpirationDays, TimeUnit.DAYS);
            User user = userRepository.findByEmail(email).orElse(null);
            if (user != null) {
                user.setLastLogoutAt(LocalDateTime.now(DateTimeConfig.DEFAULT_ZONE));
                userRepository.save(user);
            }
            SecurityContextHolder.clearContext();
            log.info("Đã đăng xuất thành công cho tài khoản Email: {}", email);
        }
    }
}
