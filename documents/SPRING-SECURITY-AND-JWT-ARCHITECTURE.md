# DOCUMENTATION: KIẾN TRÚC BẢO MẬT API & PHÂN QUYỀN TẬP TRUNG (SPRING SECURITY & JWT)

**Dự án:** Hệ thống Đấu giá Trực tuyến (`DuAnTrainning`)  
**Mục tiêu:** Chuyển đổi toàn bộ API sang cơ chế Stateless Authentication bằng JSON Web Token (JWT) kết hợp Phân quyền dựa trên vai trò (Role-Based Access Control - RBAC).

---

## 1. TỔNG QUAN NGUYÊN LÝ BẢO MẬT SYSTEM

```
[ Client / Angular UI / Postman ]
               │
               │ HTTP Request (Header: Authorization: Bearer <JWT_TOKEN>)
               ▼
   [ Tomcat Servlet Container ]
               │
               ▼
     [ DelegatingFilterProxy ]
               │
               ▼
    [ Spring Security Filter Chain ]
    ├── CorsFilter (Cấu hình CORS cho phép Frontend truy cập)
    ├── csrf.disable() (Tắt CSRF vì sử dụng Stateless JWT Header)
    ├── 🛡️ JwtAuthenticationFilter (Giải mã & Validate Token 10 phút)
    └── FilterSecurityInterceptor (Kiểm tra Phân quyền Route ROLE_USER / ROLE_ADMIN)
               │
               ▼ (Nếu Hợp Lệ)
      [ DispatcherServlet ] ➔ [ RestController ]
```

---

## 2. QUY TẮC ĐỊNH DANH & QUẢN LÝ VÒNG ĐỜI TOKEN

### 2.1. Định Danh Đăng Nhập Duy Nhất bằng EMAIL
- **Email**: Là định danh duy nhất (`unique = true`) được sử dụng để Đăng Nhập và Xác Thực người dùng (`userRepository.findByEmail(email)`).
- **Username**: Được sử dụng cho mục đích hiển thị tên người dùng public hoặc mã hóa tên ẩn danh trên sàn đấu giá (ví dụ: `d***g`), **KHÔNG** dùng để đăng nhập.

### 2.2. Thời Gian Sống Của Access Token (10 Phút)
- Cấu hình trong `application.properties`: `app.jwt.expiration-ms=600000` (10 Phút).
- **Lý do thiết kế:** Đảm bảo mức độ an toàn tối cao. Nếu lỡ lộ Token, chuỗi JWT sẽ tự động biến thành "giấy lộn" sau 10 phút.

---

## 3. MÔ HÌNH PHÂN QUYỀN RBAC (USER_ROLE ENUM)

Hệ thống được thiết kế tối giản và linh hoạt theo đúng [UserRole.java](file:///home/duong/Projects/Backend/DuAnTrainning/src/main/java/com/duong/auction/system/enums/UserRole.java):

```
                       ┌──> Vai trò 1: Bidder (Đặt giá, Mua ngay, Thanh toán)
┌──> ROLE_USER ────────┤
│                      └──> Vai trò 2: Seller (Đăng bài sản phẩm, Giao hàng)
│
└──> ROLE_ADMIN ──────────> Quản trị viên (Duyệt/Từ chối bài, Khóa phiên, Duyệt danh mục)
```

1. **`ROLE_USER`**: Đóng song song 2 vai trò linh hoạt:
   - **Bidder / Buyer**: Được phép đặt giá (`POST /v1/auctions/*/bids`), mua ngay (`POST /v1/auctions/*/buy-now`), thanh toán đơn hàng.
   - **Seller**: Được phép đăng bán sản phẩm (`POST /v1/products`), chỉnh sửa bài, giao hàng.
2. **`ROLE_ADMIN`**: Quản trị viên hệ thống:
   - Được phép phê duyệt/từ chối sản phẩm (`PUT /v1/admin/products/*/approve`), khóa phiên đấu giá, phạt gậy vi phạm bùng đơn, quản lý danh mục (`POST /v1/categories`).

---

## 4. BỘ THÀNH PHẦN MÃ NGUỒN CỐT LÕI

| STT | Tên Class / Component | Đường dẫn File | Nhiệm vụ chính |
|---|---|---|---|
| 1 | `UserRepository` | `repository/UserRepository.java` | Khai báo `Optional<User> findByEmail(String email)` |
| 2 | `UserCustomDetails` | `security/UserCustomDetails.java` | Cầu nối giữa Entity `User` với `UserDetails` của Spring Security |
| 3 | `CustomUserDetailsService` | `security/CustomUserDetailsService.java` | Truy vấn `User` theo Email từ DB, tái sử dụng `ErrorCode.USER_NOT_FOUND` |
| 4 | `JwtTokenProvider` | `security/JwtTokenProvider.java` | Sinh Token 10 phút, Giải mã Email và Validate chữ ký HMAC-SHA256 |
| 5 | `JwtAuthenticationFilter` | `security/JwtAuthenticationFilter.java` | Custom Filter kế thừa `OncePerRequestFilter` nạp Authentication |
| 6 | `JwtAuthenticationEntryPoint` | `security/JwtAuthenticationEntryPoint.java` | Trả về JSON HTTP 401 Unauthorized khi thiếu/hết hạn Token |
| 7 | `CustomAccessDeniedHandler` | `security/CustomAccessDeniedHandler.java` | Trả về JSON HTTP 403 Forbidden khi thiếu quyền Role |
| 8 | `SecurityConfig` | `config/SecurityConfig.java` | Lắp ráp chuỗi Filter, bật BCrypt, cấu hình phân chia Route RBAC |

---

## 5. PHÒNG CHỐNG LỖ HỔNG BẢO MẬT OWASP TOP 10

### 5.1. BOLA / IDOR (Broken Object Level Authorization)
- **Rủi ro:** `USER A` đã đăng nhập cố tình sửa `sellerId` trên URL để xem hoặc hủy đơn hàng của `USER B`: `GET /v1/sellers/999/orders`.
- **Giải pháp:** Sử dụng `@EnableMethodSecurity` và gắn `@PreAuthorize` tại Controller/Service:
  ```java
  @GetMapping("/v1/sellers/{sellerId}/orders")
  @PreAuthorize("#sellerId == authentication.principal.id or hasRole('ADMIN')")
  public ResponseEntity<?> getSellerOrders(@PathVariable Long sellerId) { ... }
  ```

### 5.2. Chống Đòn Tấn Công Brute Force Mật Khẩu
- Sử dụng `BCryptPasswordEncoder` mã hóa mật khẩu kèm Salt ngẫu nhiên + Adaptive Work Factor (Cost = 10).


