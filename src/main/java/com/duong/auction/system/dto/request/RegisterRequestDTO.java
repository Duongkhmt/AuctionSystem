package com.duong.auction.system.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO nhận dữ liệu gửi lên từ Form Đăng ký tài khoản người dùng mới.
 * Khớp 100% với các trường bắt buộc của bảng `users` trong CSDL.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RegisterRequestDTO {

    // 🟢 1. Email định danh đăng nhập (Bắt buộc duy nhất, đuôi @gmail.com)
    @NotBlank(message = "Email không được để trống")
    @Pattern(
            regexp = "^[A-Za-z0-9._%+-]+@gmail\\.com$",
            message = "Email không đúng định dạng -> example@gmail.com"
    )
    @Size(max = 50, message = "Email không được vượt quá 50 ký tự")
    private String email;

    // 🟢 2. Mật khẩu thô (Bắt buộc tối thiểu 8 ký tự -> Backend sẽ băm BCrypt thành passwordHash)
    @NotBlank(message = "Mật khẩu không được để trống")
    @Size(min = 8, message = "Mật khẩu phải có ít nhất 8 ký tự.")
    private String password;

    // 🟢 3. Username hiển thị public trên sàn đấu giá (Bắt buộc duy nhất, tối đa 50 ký tự)
    @NotBlank(message = "Username không được để trống")
    @Size(max = 50, message = "Username không được vượt quá 50 ký tự")
    private String username;
}
