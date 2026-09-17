package com.duong.auction.system.service;

import com.duong.auction.system.config.SecurityConstants;
import com.duong.auction.system.dto.request.UserStatusUpdateRequestDTO;
import com.duong.auction.system.dto.response.AdminUserResponseDTO;
import com.duong.auction.system.entity.User;
import com.duong.auction.system.enums.UserStatus;
import com.duong.auction.system.exception.ApplicationException;
import com.duong.auction.system.exception.ErrorCode;
import com.duong.auction.system.mapper.UserMapper;
import com.duong.auction.system.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Service xử lý các nghiệp vụ quản lý tài khoản người dùng dành riêng cho Admin.
 */
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final UserMapper userMapper;
    private final StringRedisTemplate redisTemplate;

    /**
     * Admin xem danh sách tất cả các tài khoản có trong hệ thống.
     * @return Danh sách AdminUserResponseDTO chứa thông tin chi tiết người dùng.
     */
    @Transactional(readOnly = true)
    public List<AdminUserResponseDTO> getAllUsers() {
        List<User> users = userRepository.findAll();
        return userMapper.toAdminUserResponseDTOList(users);
    }

    /**
     * Admin cập nhật trạng thái tài khoản người dùng (Khóa / Mở khóa / Banned).
     * @param userId ID người dùng cần thao tác.
     * @param requestDTO Trạng thái mới (ACTIVE, BANNED).
     * @return AdminUserResponseDTO chứa thông tin sau cập nhật.
     */
    @Transactional
    public AdminUserResponseDTO updateUserStatus(Long userId, UserStatusUpdateRequestDTO requestDTO) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ApplicationException(ErrorCode.USER_NOT_FOUND));

        user.setStatus(requestDTO.getStatus());
        User savedUser = userRepository.save(user);

        // 🟢 Đồng bộ ngay Trạng thái mới lên Redis (user:status:{email})
        redisTemplate.opsForValue().set(
                SecurityConstants.USER_STATUS_KEY_PREFIX + user.getEmail(),
                user.getStatus().name(),
                3, TimeUnit.DAYS
        );
        // Nếu bị khóa/banned, lập tức thu hồi Refresh Token trên Redis
        if (user.getStatus() != UserStatus.ACTIVE) {
            String refreshToken = redisTemplate.opsForValue().get(SecurityConstants.REFRESH_TOKEN_KEY_PREFIX + user.getEmail());
            if (refreshToken != null) {
                redisTemplate.delete(SecurityConstants.REFRESH_TOKEN_KEY_PREFIX + user.getEmail());
                redisTemplate.delete(SecurityConstants.REFRESH_TOKEN_USER_KEY_PREFIX + refreshToken);
            }
        }
        return userMapper.toAdminUserResponseDTO(savedUser);
    }
}
