package com.duong.auction.system.dto.request;

import com.duong.auction.system.enums.UserStatus;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

/**
 * Class DTO chứa thông tin trạng thái tài khoản muốn cập nhật từ Admin (Khóa / Mở Khóa).
 */
@Getter
@Setter
public class UserStatusUpdateRequestDTO {

    /**
     * Trạng thái mới của tài khoản người dùng (ACTIVE, BANNED).
     */
    @NotNull(message = "Trạng thái người dùng không được để trống")
    private UserStatus status;
}
