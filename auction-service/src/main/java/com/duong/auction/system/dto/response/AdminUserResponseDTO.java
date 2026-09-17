package com.duong.auction.system.dto.response;

import com.duong.auction.system.enums.UserRole;
import com.duong.auction.system.enums.UserStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * Class DTO chứa thông tin chi tiết của người dùng trả về cho trang Quản Lý User của Admin.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminUserResponseDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    private String username;
    private String email;
    private UserRole role;
    private UserStatus status;
    private Integer unpaidStrikeCount; // Số lần bùng đơn bị phạt
    private LocalDateTime bannedUntil;  // Thời hạn bị cấm đấu giá
    private LocalDateTime createdAt;    // Ngày khởi tạo tài khoản
}
