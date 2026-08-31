package com.duong.auction.system.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.*;


@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserRequestDTO {

    // 🟢 Kiểm tra Email không được trống và bắt buộc phải có đuôi @gmail.com
    @NotBlank(message = "Email không được để trống")
    @Pattern(
            regexp = "^[A-Za-z0-9._%+-]+@gmail\\.com$",
            message = "Email không đúng định dạng -> example@gmail.com"
    )
    private String email;

    // 🟢 Mật khẩu không được trống và bắt buộc từ 8 ký tự trở lên
    @NotBlank(message = "Mật khẩu không được để trống")
    @Size(min = 8, message = "Mật khẩu phải có ít nhất 8 ký tự.")
    private String password;
}
