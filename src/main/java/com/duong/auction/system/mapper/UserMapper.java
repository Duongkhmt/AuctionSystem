package com.duong.auction.system.mapper;

import com.duong.auction.system.dto.request.RegisterRequestDTO;
import com.duong.auction.system.dto.response.UserResponseDTO;
import com.duong.auction.system.entity.User;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * Mapper chuyển đổi giữa Entity User và các DTOs bằng MapStruct.
 */
@Mapper(componentModel = "spring")
public interface UserMapper {

    /**
     * 🟢 Chuyển đổi từ RegisterRequestDTO + Password mã hóa BCrypt sang Entity User.
     * Tự động gán role = USER, status = ACTIVE và unpaidStrikeCount = 0.
     */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "username", source = "requestDTO.username")
    @Mapping(target = "email", source = "requestDTO.email")
    @Mapping(target = "passwordHash", source = "encodedPassword")
    @Mapping(target = "unpaidStrikeCount", constant = "0")
    @Mapping(target = "bannedUntil", ignore = true)
    @Mapping(target = "role", constant = "USER")
    @Mapping(target = "status", constant = "ACTIVE")
    @Mapping(target = "createdAt", ignore = true)
    User toEntity(RegisterRequestDTO requestDTO, String encodedPassword);

    /**
     * 🟢 Chuyển đổi từ Entity User + cặp Token sang UserResponseDTO cho Angular.
     */
    @Mapping(target = "accessToken", source = "accessToken")
    @Mapping(target = "refreshToken", source = "refreshToken")
    @Mapping(target = "username", source = "user.username")
    @Mapping(target = "role", source = "user.role")
    UserResponseDTO toUserResponseDTO(User user, String accessToken, String refreshToken);
}
