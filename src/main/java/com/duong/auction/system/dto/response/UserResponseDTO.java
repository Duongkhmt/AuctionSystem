package com.duong.auction.system.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserResponseDTO {

    // 🟢 Access Token (10 phút)
    private String accessToken;

    // 🟢 Tên hiển thị người dùng (hiển thị trên UI Header)
    private String username;

    // 🟢 Vai trò người dùng (USER hoặc ADMIN)
    private String role;
}

