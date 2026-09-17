package com.duong.auction.system.controller;

import com.duong.auction.system.dto.request.LoginRequestDTO;
import com.duong.auction.system.dto.request.RefreshTokenRequestDTO;
import com.duong.auction.system.dto.request.RegisterRequestDTO;
import com.duong.auction.system.dto.response.UserResponseDTO;
import com.duong.auction.system.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Controller quản lý các Endpoint Xác thực (Đăng ký, Đăng nhập, Refresh Token & Đăng xuất).
 * Được phân quyền permitAll() công khai trong SecurityConfig.
 */
@RestController
@RequestMapping("/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    // =========================================================================
    // 1. API ĐĂNG KÝ TÀI KHOẢN MỚI
    // POST /v1/auth/register
    // =========================================================================
    @PostMapping("/register")
    public ResponseEntity<UserResponseDTO> register(@Valid @RequestBody RegisterRequestDTO requestDTO) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.register(requestDTO));
    }

    // =========================================================================
    // 2. API ĐĂNG NHẬP HỆ THỐNG
    // POST /v1/auth/login
    // =========================================================================
    @PostMapping("/login")
    public ResponseEntity<UserResponseDTO> login(@Valid @RequestBody LoginRequestDTO requestDTO) {
        return ResponseEntity.ok(authService.login(requestDTO));
    }

    // =========================================================================
    // 3. API LÀM MỚI ACCESS TOKEN (REFRESH TOKEN)
    // POST /v1/auth/refresh
    // =========================================================================
    @PostMapping("/refresh")
    public ResponseEntity<UserResponseDTO> refreshToken(@Valid @RequestBody RefreshTokenRequestDTO requestDTO) {
        return ResponseEntity.ok(authService.refreshToken(requestDTO));
    }

    // =========================================================================
    // 4. API ĐĂNG XUẤT TÀI KHOẢN
    // POST /v1/auth/logout
    // =========================================================================
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest request) {
        authService.logout(request);
        return ResponseEntity.ok().build();
    }
}
