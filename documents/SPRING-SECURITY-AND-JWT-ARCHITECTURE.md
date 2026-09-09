# DOCUMENTATION: KIẾN TRÚC BẢO MẬT SYSTEM, JWT & PHÂN QUYỀN RBAC (SPEC & CODE MAPPING)

---


### Các mục tiêu chính đã hoàn thành:
1. **Tích hợp Spring Security 6.x (Stateless Session Policy)**: Chuyển toàn bộ ứng dụng sang cơ chế xác thực không lưu Session trên Server RAM.
2. **Mã hóa Mật khẩu bằng BCrypt (`BCryptPasswordEncoder`)**: Mã hóa mật khẩu người dùng với muối (Salt) ngẫu nhiên và Work Factor = 10, chống tấn công Brute-Force & Rainbow Table.
3. **Sinh & Xác thực JWT Token (HMAC-SHA256)**: Xây dựng cơ chế cặp Token (**Access Token 10 Phút** + **Refresh Token Rotation 3 Ngày**).
4. **Phân quyền API theo Role (RBAC - Role-Based Access Control)**: Phân chia rõ ràng tuyến đường API cho `ROLE_USER` (Buyer/Bidder & Seller) và `ROLE_ADMIN`.
5. **Quản lý Phiên & Tối ưu RAM Redis (Cache-Aside & PostgreSQL DB Fallback)**: Thu hồi Token tức thì khi User Logout hoặc bị Admin Ban, triệt tiêu 100% rác bộ nhớ RAM Redis và bảo đảm CSDL PostgreSQL làm **Nguồn Sự Thật Duy Nhất (Single Source of Truth)**.
---

## 2. DANH MỤC TOÀN BỘ CÁC LỚP BẢO MẬT & VAI TRÒ CHI TIẾT (FULL CLASS CATALOG)

Dưới đây là danh sách đầy đủ tất cả các Class/Component cấu thành nên hệ thống Bảo mật & Phân quyền, cùng vị trí file và nhiệm vụ cụ thể của từng lớp:

| STT | Tên Lớp (Class Name) | Đường dẫn File (Location) | Vai trò & Nhiệm vụ chính trong Hệ thống |
| :---: | :---| :---| :---|
| **1** | [`SecurityConfig`](file:///home/duong/Projects/Backend/DuAnTrainning/src/main/java/com/duong/auction/system/config/SecurityConfig.java) | `config/SecurityConfig.java` | **Trung tâm Cấu hình Spring Security 6.x**:<br>• Cấu hình chế độ Stateless Session (`STATELESS`).<br>• Tắt CSRF, bật CORS.<br>• Khai báo Bean `BCryptPasswordEncoder` (strength 10) & `AuthenticationManager`.<br>• Định tuyến phân quyền API (`/v1/auth/**` permitAll, `/v1/admin/**` hasRole('ADMIN')). |
| **2** | [`JwtTokenProvider`](file:///home/duong/Projects/Backend/DuAnTrainning/src/main/java/com/duong/auction/system/security/JwtTokenProvider.java) | `security/JwtTokenProvider.java` | **Công cụ thao tác JWT Token (HMAC-SHA256)**:<br>• Sinh Access Token 10m kèm các Claim (`iat`, `exp`, email, role).<br>• Trích xuất thông tin: `getUsernameFromToken()`, `getIssuedAtFromToken()`.<br>• Kiểm tra tính hợp lệ & chữ ký JWT: `validateToken()`. |
| **3** | [`JwtAuthenticationFilter`](file:///home/duong/Projects/Backend/DuAnTrainning/src/main/java/com/duong/auction/system/security/JwtAuthenticationFilter.java) | `security/JwtAuthenticationFilter.java` | **Filter Đánh chặn Request (OncePerRequestFilter)**:<br>• Trích xuất JWT từ Header `Authorization: Bearer <TOKEN>`.<br>• Check 1: Chặn ngay nếu nick bị `BANNED` (đọc Redis RAM).<br>• Check 2: Chặn ngay nếu Token sinh trước mốc `Logout` (Redis + DB Fallback).<br>• Nạp `Authentication` vào `SecurityContextHolder` cho Request đi tiếp. |
| **4** | [`CustomUserDetailsService`](file:///home/duong/Projects/Backend/DuAnTrainning/src/main/java/com/duong/auction/system/security/CustomUserDetailsService.java) | `security/CustomUserDetailsService.java` | **Cầu nối nạp User từ CSDL**:<br>• Implements `UserDetailsService` của Spring Security.<br>• Tìm `User` trong PostgreSQL theo Email qua `userRepository.findByEmail(email)`.<br>• Đóng gói thành đối tượng `UserCustomDetails`. |
| **5** | [`UserCustomDetails`](file:///home/duong/Projects/Backend/DuAnTrainning/src/main/java/com/duong/auction/system/security/UserCustomDetails.java) | `security/UserCustomDetails.java` | **Lớp đại diện User cho Spring Security**:<br>• Implements `UserDetails`.<br>• Chuyển đổi enum `UserRole` thành `GrantedAuthority` với tiền tố `"ROLE_"`.<br>• Chứa thuộc tính `private final transient User user;` để rút thông tin user trực tiếp trên RAM (0ms SQL). |
| **6** | [`JwtAuthenticationEntryPoint`](file:///home/duong/Projects/Backend/DuAnTrainning/src/main/java/com/duong/auction/system/security/JwtAuthenticationEntryPoint.java) | `security/JwtAuthenticationEntryPoint.java` | **Xử lý Lỗi Chưa Đăng nhập (401 Unauthorized)**:<br>• Implements `AuthenticationEntryPoint`.<br>• Kích hoạt khi Client gửi Request không có Token hoặc Token hết hạn/sai chữ ký.<br>• Trả về định dạng JSON chuẩn `ErrorResponse` (HTTP 401). |
| **7** | [`CustomAccessDeniedHandler`](file:///home/duong/Projects/Backend/DuAnTrainning/src/main/java/com/duong/auction/system/security/CustomAccessDeniedHandler.java) | `security/CustomAccessDeniedHandler.java` | **Xử lý Lỗi Sai Quyền / Không đủ quyền (403 Forbidden)**:<br>• Implements `AccessDeniedHandler`.<br>• Kích hoạt khi User đã đăng nhập nhưng cố tình truy cập API không thuộc Role của mình.<br>• Trả về định dạng JSON chuẩn `ErrorResponse` (HTTP 403). |
| **8** | [`SecurityConstants`](file:///home/duong/Projects/Backend/DuAnTrainning/src/main/java/com/duong/auction/system/security/SecurityConstants.java) | `security/SecurityConstants.java` | **Lớp Quản lý Hằng số Bảo mật**:<br>• Lưu trữ Header Name (`Authorization`), Token Prefix (`Bearer `).<br>• Lưu trữ Prefix Key cho Redis: `refresh_token:`, `user:status:`, `user:logout_at:`. |
| **9** | [`AuthService`](file:///home/duong/Projects/Backend/DuAnTrainning/src/main/java/com/duong/auction/system/service/AuthService.java) | `service/AuthService.java` | **Service Tầng Nghiệp vụ Xác thực**:<br>• Xử lý Đăng ký (băm BCrypt, save DB).<br>• Xử lý Đăng nhập (authen BCrypt, sinh Token, lưu Refresh Token Redis).<br>• Xử lý Refresh Token Rotation (Xoay vòng token).<br>• Xử lý Logout (Xóa Refresh Token, ghi `logout_at` lên Redis & DB). |
| **10** | [`GlobalExceptionHandler`](file:///home/duong/Projects/Backend/DuAnTrainning/src/main/java/com/duong/auction/system/exception/GlobalExceptionHandler.java) | `exception/GlobalExceptionHandler.java` | **Xử lý Lỗi Ngoại lệ Tập trung**:<br>• Bắt các Exception bảo mật như `BadCredentialsException`, `DisabledException`, `LockedException`.<br>• Chuyển đổi thành HTTP Response JSON chuẩn hóa cho Client. |

---

## 3. BẢN ĐỒ KỊCH BẢN NGHIỆP VỤ & CODE MAPPING (CLASS / HÀM CHI TIẾT)

### Kịch bản 1: Đăng ký & Đăng nhập tài khoản

- **Đăng ký (`POST /v1/auth/register`)**:
  - Class: [`AuthService`](file:///home/duong/Projects/Backend/DuAnTrainning/src/main/java/com/duong/auction/system/service/AuthService.java) ➔ Hàm: `register(RegisterRequestDTO request)`
  - Logic chi tiết:
    1. Kiểm tra Email & Username trùng lặp trong DB qua `userRepository.existsByEmail()` và `userRepository.existsByUsername()`. Nếu trùng ➔ `throw new ApplicationException(ErrorCode.EMAIL_ALREADY_EXISTS)`.
    2. Mã hóa mật khẩu thô bằng BCrypt: `passwordEncoder.encode(request.getPassword())`.
    3. Chuyển DTO sang Entity qua [`UserMapper`](file:///home/duong/Projects/Backend/DuAnTrainning/src/main/java/com/duong/auction/system/mapper/UserMapper.java) và lưu xuống CSDL PostgreSQL qua `userRepository.save(user)`.
    4. Tự động đăng nhập luôn cho người dùng sau khi đăng ký thành công.

- **Đăng nhập (`POST /v1/auth/login`)**:
  - Class: [`AuthService`](file:///home/duong/Projects/Backend/DuAnTrainning/src/main/java/com/duong/auction/system/service/AuthService.java) ➔ Hàm: `login(LoginRequestDTO request)`
  - Logic chi tiết:
    1. Gọi `authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(email, password))` để Spring Security kiểm tra mật khẩu BCrypt.
    2. Query `userRepository.findByEmail(email)`. Kiểm tra `user.getStatus() != ACTIVE` ➔ `throw new ApplicationException(ErrorCode.USER_BANNED_FROM_BIDDING)`.
    3. Gọi [`JwtTokenProvider.generateToken(authentication)`](file:///home/duong/Projects/Backend/DuAnTrainning/src/main/java/com/duong/auction/system/security/JwtTokenProvider.java#L43) sinh Access Token 10m chứa claim `iat` và `exp`.
    4. Sinh Refresh Token ngẫu nhiên bằng `UUID.randomUUID().toString()`.
    5. Ghi dữ liệu lên Redis: `refresh_token:{email}` (TTL 3d), `refresh_token_user:{refreshToken}` (TTL 3d), và cache trạng thái `user:status:{email}` = `"ACTIVE"` (TTL 3d).
    6. Trả về `UserResponseDTO` chứa `accessToken` và `refreshToken`.

---

### Kịch bản 2: Tự động gia hạn Token ngầm (Refresh Token Rotation)

- **API Refresh (`POST /v1/auth/refresh`)**:
  - Class: [`AuthService`](file:///home/duong/Projects/Backend/DuAnTrainning/src/main/java/com/duong/auction/system/service/AuthService.java) ➔ Hàm: `refreshToken(RefreshTokenRequestDTO request)`
  - Logic chi tiết:
    1. Lấy `oldRefreshToken` từ DTO, tra cứu Email từ Redis `refresh_token_user:{oldRefreshToken}`. Nếu `email == null` ➔ `throw new ApplicationException(ErrorCode.UNAUTHENTICATED)`.
    2. Query `userRepository.findByEmail(email)`. Kiểm tra `user.getStatus() != ACTIVE`. Nếu bị Ban ➔ Xóa Refresh Token trên Redis và `throw new ApplicationException(ErrorCode.USER_BANNED_FROM_BIDDING)`.
    3. **Rotation (Chống dùng lại token cũ)**: Xóa `refresh_token_user:{oldRefreshToken}` trên Redis.
    4. Nạp UserDetails và Authorities từ [`CustomUserDetailsService.loadUserByUsername(email)`](file:///home/duong/Projects/Backend/DuAnTrainning/src/main/java/com/duong/auction/system/security/CustomUserDetailsService.java).
    5. Sinh cặp Token mới: `newAccessToken` (10m) và `newRefreshToken` (UUID 3d).
    6. Ghi cặp mới lên Redis và trả về cho Client.

---

### Kịch bản 3: Đăng xuất chủ động (User Logout)

- **API Logout (`POST /v1/auth/logout`)**:
  - Class: [`AuthService`](file:///home/duong/Projects/Backend/DuAnTrainning/src/main/java/com/duong/auction/system/service/AuthService.java) ➔ Hàm: `logout(HttpServletRequest request)`
  - Logic chi tiết:
    1. Rút `email` từ `SecurityContextHolder.getContext().getAuthentication().getName()`.
    2. Xóa Refresh Token trên Redis (`refresh_token:{email}` và `refresh_token_user:{refreshToken}`).
    3. Ghi mốc thời gian Logout hiện tại lên Redis: `redisTemplate.opsForValue().set(USER_LOGOUT_AT_KEY_PREFIX + email, String.valueOf(System.currentTimeMillis()), 3, DAYS)`.
    4. **Đồng bộ CSDL PostgreSQL**: Tìm `user` theo email, gọi `user.setLastLogoutAt(LocalDateTime.now(DateTimeConfig.DEFAULT_ZONE))` và lưu xuống CSDL qua `userRepository.save(user)`.
    5. Xóa Security Context: `SecurityContextHolder.clearContext()`.

---

### Kịch bản 4: Admin khóa tài khoản (Account Lock / Banned)

- **API Khóa Nick (`PUT /v1/admin/users/{userId}/status`)**:
  - Class: [`UserService`](file:///home/duong/Projects/Backend/DuAnTrainning/src/main/java/com/duong/auction/system/service/UserService.java) ➔ Hàm: `updateUserStatus(Long userId, UserStatusUpdateRequestDTO requestDTO)`
  - Logic chi tiết:
    1. Tìm `User` trong DB theo `userId`. Ném `USER_NOT_FOUND` nếu không tồn tại.
    2. Cập nhật `user.setStatus(requestDTO.getStatus())` (ví dụ `BANNED`) và lưu xuống CSDL PostgreSQL qua `userRepository.save(user)`.
    3. **Đồng bộ ngay lên Redis**: `redisTemplate.opsForValue().set(USER_STATUS_KEY_PREFIX + user.getEmail(), user.getStatus().name(), 3, DAYS)`.
    4. **Thu hồi Refresh Token**: Nếu status khác `ACTIVE`, lấy `refreshToken` từ Redis `refresh_token:{email}` và xóa cả 2 key `refresh_token:{email}` và `refresh_token_user:{refreshToken}`.

---

### Kịch bản 5: Đánh chặn Filter & Fallback CSDL PostgreSQL

- **Filter Đánh Chặn**:
  - Class: [`JwtAuthenticationFilter`](file:///home/duong/Projects/Backend/DuAnTrainning/src/main/java/com/duong/auction/system/security/JwtAuthenticationFilter.java) ➔ Hàm: `doFilterInternal(...)`
  - Logic chi tiết:
    1. Rút Token từ Header `Authorization: Bearer <TOKEN>` qua `getJwtFromRequest(request)`.
    2. Validate chữ ký JWT và thời hạn 10m qua [`JwtTokenProvider.validateToken(jwt)`](file:///home/duong/Projects/Backend/DuAnTrainning/src/main/java/com/duong/auction/system/security/JwtTokenProvider.java#L74).
    3. Trích xuất Email: `tokenProvider.getUsernameFromToken(jwt)`.
    4. **Check 1 (Nick Banned - Redis RAM)**: Gọi `isUserBanned(email)`. Đọc Redis `user:status:{email}`. Nếu `"BANNED"` ➔ Log warn và gọi `sendErrorResponse()` trả HTTP 403 (`USER_BANNED_FROM_BIDDING`).
    5. Nạp UserDetails từ CSDL PostgreSQL qua [`CustomUserDetailsService.loadUserByUsername(email)`](file:///home/duong/Projects/Backend/DuAnTrainning/src/main/java/com/duong/auction/system/security/CustomUserDetailsService.java).
    6. **Check 1.5 (Nick Banned - DB Fallback)**: Kiểm tra `!userDetails.isAccountNonLocked() || !userDetails.isEnabled()`. Nếu Redis bị sập/cache miss, bước này sẽ kiểm tra trực tiếp trạng thái `BANNED` vừa nạp từ CSDL PostgreSQL ➔ Trả HTTP 403 (`USER_BANNED_FROM_BIDDING`).
    7. **Check 2 (Token Logout - Redis + DB Fallback)**: Gọi `isTokenRevokedByLogout(email, jwt, userDetails)`.
       - Đọc Redis `user:logout_at:{email}`. Nếu tồn tại, so sánh `tokenIssuedAt < (lastLogoutAt - 1000ms)` (chứa 1s buffer clock skew).
       - Nếu Redis bị trống (Cache Miss) ➔ Fallback lấy `dbUser.getLastLogoutAt()` từ CSDL PostgreSQL ra so sánh.
       - Nếu Token sinh trước mốc Logout ➔ Log warn và gọi `sendErrorResponse()` trả HTTP 401 (`UNAUTHENTICATED`).
    8. Nạp `SecurityContextHolder.getContext().setAuthentication(authentication)`.

---

## 3. MÔ HÌNH PHÂN QUYỀN RBAC & BẢO VỆ CHÍNH CHỦ (/me RESTFUL)

### 3.1. Cấu hình Phân quyền Route (RBAC)
- Class: [`SecurityConfig`](file:///home/duong/Projects/Backend/DuAnTrainning/src/main/java/com/duong/auction/system/config/SecurityConfig.java) ➔ Bean: `securityFilterChain(HttpSecurity http)`
- Quy tắc Phân quyền:
  - `.requestMatchers("/v1/auth/**").permitAll()` (Public Đăng ký/Đăng nhập).
  - `.requestMatchers("/v1/bidders/**", "/v1/sellers/**").hasRole("USER")` (Quyền Buyer/Seller).
  - `.requestMatchers("/v1/admin/**").hasRole("ADMIN")` (Quyền Quản trị viên).
  - Class bọc User: [`UserCustomDetails`](file:///home/duong/Projects/Backend/DuAnTrainning/src/main/java/com/duong/auction/system/security/UserCustomDetails.java) ➔ Hàm `getAuthorities()` gán tiền tố `"ROLE_" + user.getRole().name()`.

### 3.2. Triệt tiêu Lỗ hổng BOLA / IDOR với Rút User từ RAM (0ms SQL)
- Helper: Trong các Service (`ProductService`, `BiddingService`, `OrderService`), phương thức `getAuthenticatedUser()`:
  - Logic: Rút trực tiếp `User` từ `SecurityContextHolder.getContext().getAuthentication().getPrincipal()` (kiểu `UserCustomDetails`).
  - Lợi ích: Triệt tiêu 100% câu lệnh `SELECT * FROM users` dư thừa, tự động nhận diện chính chủ không cần nhận `userId` từ Client.

---

## 4. BẢNG MAPPING MÃ LỖI HTTP STATUS & ERROR CODES

- Đóng gói phản hồi lỗi: Class [`ErrorResponse`](file:///home/duong/Projects/Backend/DuAnTrainning/src/main/java/com/duong/auction/system/exception/ErrorResponse.java) & Enum [`ErrorCode`](file:///home/duong/Projects/Backend/DuAnTrainning/src/main/java/com/duong/auction/system/exception/ErrorCode.java)
- Handlers xử lý:
  - Class: [`GlobalExceptionHandler`](file:///home/duong/Projects/Backend/DuAnTrainning/src/main/java/com/duong/auction/system/exception/GlobalExceptionHandler.java) ➔ Bắt `DisabledException`/`LockedException` (Trả HTTP 403 `USER_BANNED_FROM_BIDDING`).
  - Class: [`JwtAuthenticationEntryPoint`](file:///home/duong/Projects/Backend/DuAnTrainning/src/main/java/com/duong/auction/system/security/JwtAuthenticationEntryPoint.java) ➔ Bắt lỗi chưa đăng nhập (Trả HTTP 401 `UNAUTHENTICATED`).
  - Class: [`CustomAccessDeniedHandler`](file:///home/duong/Projects/Backend/DuAnTrainning/src/main/java/com/duong/auction/system/security/CustomAccessDeniedHandler.java) ➔ Bắt lỗi truy cập sai Role (Trả HTTP 403 `UNAUTHORIZED_ACCESS`).

| HTTP Status Code | ErrorCode Name | Class / Handler Xử lý | Ngữ cảnh Nghiệp vụ |
|---|---|---|---|
| **401 Unauthorized** | `UNAUTHENTICATED` (1004) | `JwtAuthenticationEntryPoint` / `JwtAuthenticationFilter.sendErrorResponse()` | Token hết hạn, sai chữ ký, hoặc Token bị thu hồi do Logout |
| **403 Forbidden** | `USER_BANNED_FROM_BIDDING` (1003) | `GlobalExceptionHandler` / `JwtAuthenticationFilter.sendErrorResponse()` | Tài khoản bị Admin khóa (`BANNED`) |
| **403 Forbidden** | `UNAUTHORIZED_ACCESS` (1002) | `CustomAccessDeniedHandler` | User thường cố tình gọi API dành riêng cho Admin |

---

## 5. TỐI ƯU HÓA CODE QUALITY & SONARQUBE COMPLIANCE

| Quy tắc SonarQube | Class bị ảnh hưởng | Cách Refactor Khắc Phục |
|---|---|---|
| **java:S3776** (Cognitive Complexity > 15) | `JwtAuthenticationFilter` | Tách 3 hàm helper `isUserBanned()`, `isTokenRevokedByLogout()`, `sendErrorResponse()`. Hạ độ phức tạp từ 23 xuống 3. |
| **java:S1192** (Duplicated String Literal) | `JwtAuthenticationFilter` | Tái sử dụng `MediaType.APPLICATION_JSON_VALUE` đúng 1 lần trong `sendErrorResponse()`. |
| **java:S1854 / java:S1481** (Unused Variable) | `AuthService` | Xóa bỏ khai báo biến `accessToken` rác trong hàm `logout()`. |
| **java:S1948** (Non-serializable field) | `UserCustomDetails` | Đánh dấu `private final transient User user;` cho trường Entity User. |

---

## 6. MÃ NGUỒN MẪU RÚT GỌN (CORE SNIPPETS MAPPING)

### 6.1. Logic Đánh chặn trong `JwtAuthenticationFilter.doFilterInternal()`
```java
// 1. Rút & Validate Token
String jwt = getJwtFromRequest(request);
if (StringUtils.hasText(jwt) && tokenProvider.validateToken(jwt)) {
    String email = tokenProvider.getUsernameFromToken(jwt);

    // 2. Check Nick Banned (Redis)
    if (isUserBanned(email)) {
        sendErrorResponse(response, HttpServletResponse.SC_FORBIDDEN, ErrorCode.USER_BANNED_FROM_BIDDING);
        return;
    }

    // 3. Nạp UserDetails từ PostgreSQL DB
    UserDetails userDetails = customUserDetailsService.loadUserByUsername(email);

    // 3.5. Check Nick Banned (DB Fallback khi Redis sập/cache miss)
    if (!userDetails.isAccountNonLocked() || !userDetails.isEnabled()) {
        sendErrorResponse(response, HttpServletResponse.SC_FORBIDDEN, ErrorCode.USER_BANNED_FROM_BIDDING);
        return;
    }

    // 4. Check Token Logout (Redis + Fallback DB)
    if (isTokenRevokedByLogout(email, jwt, userDetails)) {
        sendErrorResponse(response, HttpServletResponse.SC_UNAUTHORIZED, ErrorCode.UNAUTHENTICATED);
        return;
    }

    // 5. Nạp SecurityContext
    UsernamePasswordAuthenticationToken authentication =
            new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
    SecurityContextHolder.getContext().setAuthentication(authentication);
}
```

### 6.2. Logic Logout trong `AuthService.logout()`
```java
// 1. Xóa Refresh Token trên Redis
redisTemplate.delete(SecurityConstants.REFRESH_TOKEN_KEY_PREFIX + email);
redisTemplate.delete(SecurityConstants.REFRESH_TOKEN_USER_KEY_PREFIX + refreshToken);

// 2. Ghi mốc Logout lên Redis & CSDL PostgreSQL
long currentTimestamp = System.currentTimeMillis();
redisTemplate.opsForValue().set(SecurityConstants.USER_LOGOUT_AT_KEY_PREFIX + email, String.valueOf(currentTimestamp), refreshExpirationDays, TimeUnit.DAYS);

User user = userRepository.findByEmail(email).orElse(null);
if (user != null) {
    user.setLastLogoutAt(LocalDateTime.now(DateTimeConfig.DEFAULT_ZONE));
    userRepository.save(user);
}

SecurityContextHolder.clearContext();
```
