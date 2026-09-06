# DOCUMENTATION: KIẾN TRÚC BẢO MẬT SYSTEM, JWT & QUẢN LÝ PHIÊN REDIS TỐI ƯU

**Dự án:** Hệ thống Đấu giá Trực tuyến (`DuAnTrainning`)  
**Tác giả:** Duongkhmt  
**Phạm vi:** Tài liệu Quy chuẩn Kiến trúc Bảo mật, Nghiệp vụ Xác thực & Giải pháp Tối ưu Bộ nhớ RAM.

---

## 📑 MỤC LỤC
1. [NGUYÊN TẮC & NGUYÊN LÝ BẢO MẬT HỆ THỐNG](#1-nguyên-tắc--nguyên-lý-bảo-mật-hệ-thống)
2. [GIẢI PHÁP TỔNG THỂ & SƠ ĐỒ LUỒNG XÁC THỰC](#2-giải-pháp-tổng-thể--sơ-đồ-luồng-xác-thực)
3. [QUY TẮC VÒNG ĐỜI TOKEN & THÔNG SỐ REDIS KEYS](#3-quy-tắc-vòng-đời-token--thông-số-redis-keys)
4. [CHI TIẾT CÁC LUỒNG NGHIỆP VỤ (END-TO-END BUSINESS FLOWS)](#4-chi-tiết-các-luồng-nghiệp-vụ-end-to-end-business-flows)
5. [QUY CHUẨN XỬ LÝ EDGE CASES & ĐÓNG GÓI LỖI](#5-quy-chuẩn-xử-lý-edge-cases--đóng-gói-lỗi)
6. [BẢNG MAPPING THÀNH PHẦN HỆ THỐNG (COMPONENT SPECIFICATION)](#6-bảng-mapping-thành-phần-hệ-thống-component-specification)

---

## 1. NGUYÊN TẮC & NGUYÊN LÝ BẢO MẬT HỆ THỐNG

Hệ thống Đấu giá Trực tuyến vận hành theo mô hình kiến trúc bảo mật **Stateless Authentication (JWT)** kết hợp **Redis Cache-Aside** và **PostgreSQL Single Source of Truth**:

### 1.1. PostgreSQL là Nguồn Sự Thật Duy Nhất (Single Source of Truth)
- CSDL PostgreSQL chịu trách nhiệm lưu trữ vĩnh viễn và chính xác tuyệt đối thông tin người dùng, mật khẩu đã băm và trạng thái tài khoản (`ACTIVE`, `BANNED`).

### 1.2. Redis đóng vai trò Cache-Aside (Không lưu dữ liệu vĩnh viễn)
- Bộ nhớ đệm RAM Redis chỉ lưu các Key hỗ trợ tăng tốc kiểm tra quyền với thời gian sống có hạn (**TTL 3 ngày**).
- **Tuyệt đối không lưu từng chuỗi token rác (`blacklist_token:*`)** để tối ưu hóa bộ nhớ RAM Redis và không ảnh hưởng tới hạ tầng chung.
- **Cơ chế Fallback DB**: Khi Redis bị trống dữ liệu (do hết hạn TTL, cache bị xóa hoặc Redis restart), Filter tự động **Fallback truy vấn PostgreSQL CSDL** để xác thực, đảm bảo hệ thống vận hành an toàn 100% không phụ thuộc vĩnh viễn vào Redis.

---

## 2. GIẢI PHÁP TỔNG THỂ & SƠ ĐỒ LUỒNG XÁC THỰC

```
                     +---------------------------------------+
                     |       HTTP Request (Bearer JWT)       |
                     +---------------------------------------+
                                         │
                                         ▼
                     +---------------------------------------+
                     |       JwtAuthenticationFilter         |
                     +---------------------------------------+
                                         │
                    1. Validate JWT Signature & Expiration
                                         │
                                         ▼
                 +-----------------------------------------------+
                 | Check 1: Redis `user:status:{email}`          |
                 +-----------------------------------------------+
                  ├── Cache Hit: Status = BANNED ─────────────► [ HTTP 403 Forbidden ] (USER_BANNED_FROM_BIDDING)
                  └── Cache Miss / Status = ACTIVE
                                         │
                                         ▼
                 +-----------------------------------------------+
                 | Check 2: Redis `user:logout_at:{email}`       |
                 +-----------------------------------------------+
                  ├── Cache Hit: iat < (lastLogoutAt - 1000ms) ──► [ HTTP 401 Unauthorized ] (UNAUTHENTICATED)
                  └── Cache Miss / Token Valid
                                         │
                                         ▼
                 +-----------------------------------------------+
                 | Fallback DB: CustomUserDetailsService         |
                 +-----------------------------------------------+
                  ├── DB Status = BANNED ─────────────────────► [ HTTP 403 Forbidden ] (DisabledException)
                  └── DB Status = ACTIVE
                                         │
                                         ▼
                 +-----------------------------------------------+
                 | Set SecurityContextHolder & Re-cache Redis    |
                 +-----------------------------------------------+
```

---

## 3. QUY TẮC VÒNG ĐỜI TOKEN & THÔNG SỐ REDIS KEYS

### 3.1. Thông số Kỹ thuật Token
| Loại Token | Thời gian sống (TTL) | Lưu trữ phía Client | Mục đích sử dụng |
|---|---|---|---|
| **Access Token** | 10 Phút (`600.000ms`) | LocalStorage / Memory | Đính kèm vào Header `Authorization: Bearer <TOKEN>` cho mỗi HTTP Request |
| **Refresh Token** | 3 Ngày (`3 Days`) | HttpOnly Cookie / LocalStorage | Dùng để xin cặp Token mới khi Access Token hết hạn (Refresh Token Rotation) |

### 3.2. Danh mục Redis Keys (Mô hình 1 Key / User)
| Tên Key Redis | Kiểu dữ liệu | TTL | Mục đích nghiệp vụ |
|---|---|---|---|
| `refresh_token:{email}` | String | 3 Ngày | Lưu Refresh Token hiện tại của User |
| `refresh_token_user:{refreshToken}` | String | 3 Ngày | Tra cứu ngược Email từ Refresh Token |
| `user:status:{email}` | String | 3 Ngày | Cache trạng thái tài khoản (`ACTIVE` / `BANNED`) để Filter chặn 0ms SQL |
| `user:logout_at:{email}` | String | 3 Ngày | Lưu mốc timestamp Logout gần nhất để vô hiệu hóa Token cũ |

---

## 4. CHI TIẾT CÁC LUỒNG NGHIỆP VỤ (END-TO-END BUSINESS FLOWS)

### 4.1. Nghiệp vụ Đăng Nhập (`POST /v1/auth/login`)
1. Xác thực Email & Password qua `AuthenticationManager`.
2. Kiểm tra trạng thái tài khoản trong DB: Nếu `status != ACTIVE` ➔ Ném lỗi HTTP 403 (`USER_BANNED_FROM_BIDDING`).
3. Sinh Access Token (10m) và Refresh Token UUID.
4. Ghi dữ liệu Caching lên Redis:
   - `refresh_token:{email}` = `refreshToken` (TTL 3 ngày).
   - `refresh_token_user:{refreshToken}` = `email` (TTL 3 ngày).
   - `user:status:{email}` = `"ACTIVE"` (TTL 3 ngày).

### 4.2. Nghiệp vụ Đăng Xuất (`POST /v1/auth/logout`)
1. Rút Email từ `SecurityContextHolder`.
2. Xóa Refresh Token trên Redis (`refresh_token:{email}` và `refresh_token_user:{refreshToken}`).
3. Ghi mốc thời gian Đăng xuất lên Redis:
   - `user:logout_at:{email}` = `System.currentTimeMillis()` (TTL 3 ngày).
4. Xóa ngữ cảnh bảo mật: `SecurityContextHolder.clearContext()`.

### 4.3. Nghiệp vụ Admin Khóa Tài Khoản (`PUT /v1/admin/users/{userId}/status`)
1. Cập nhật `status = BANNED` trong CSDL PostgreSQL (Nguồn sự thật chính).
2. Đồng bộ trạng thái mới lên Redis ngay lập tức:
   - `user:status:{email}` = `"BANNED"` (TTL 3 ngày).
3. Thu hồi quyền Refresh: Xóa `refresh_token:{email}` trên Redis.

### 4.4. Nghiệp vụ Đánh Chặn Filter (`JwtAuthenticationFilter`)
1. Trích xuất Bearer Token từ Header `Authorization`.
2. Giải mã Email và mốc thời gian phát hành (`iat`).
3. **Check 1 (Nick bị khóa)**: Đọc Redis `user:status:{email}`. Nếu `"BANNED"` ➔ Trả HTTP 403 (`USER_BANNED_FROM_BIDDING`).
4. **Check 2 (Token bị thu hồi do Logout)**: Đọc Redis `user:logout_at:{email}`. Nếu `iat == null` hoặc `iat < (lastLogoutAt - 1000ms)` ➔ Trả HTTP 401 (`UNAUTHENTICATED`).
5. **Fallback DB (Cache Miss)**: Nếu Redis bị hết hạn/restart ➔ Query DB qua `CustomUserDetailsService`. Nếu DB báo `BANNED` ➔ Trả HTTP 403. Nếu DB báo `ACTIVE` ➔ Cho qua và Re-cache lên Redis.

---

## 5. QUY CHUẨN XỬ LÝ EDGE CASES & ĐÓNG GÓI LỖI

### 5.1. Buffer Chống Lệch Thời Gian (Clock Skew Buffer)
- **Quy tắc**: Bổ sung khoảng buffer `1000ms` (1 giây) vào điều kiện so sánh mốc phát hành Token. Token chỉ bị từ chối nếu nó được sinh ra **trước mốc Logout ít nhất 1 giây** nhằm triệt tiêu lỗi lệch millisecond giữa các luồng khi Logout xong Login lại ngay.

### 5.2. Phòng Thủ Token Thiếu Claim `iat`
- **Quy tắc**: Nếu `tokenIssuedAt == null` khi đã có mốc `user:logout_at` trên Redis ➔ Từ chối ngay lập tức với lỗi HTTP 401.

### 5.3. Chuẩn Hóa Phản Hồi Lỗi Tái Sử Dụng Package `exception`
- Không ghi cứng chuỗi JSON thủ công.
- Sử dụng `ObjectMapper` + `ErrorResponse` + `ErrorCode` (`USER_BANNED_FROM_BIDDING` & `UNAUTHENTICATED`) đồng bộ 100% với `GlobalExceptionHandler`.

---

## 6. BẢNG MAPPING THÀNH PHẦN HỆ THỐNG (COMPONENT SPECIFICATION)

| STT | Thành phần / Component | Đường dẫn File Mã Nguồn | Vai trò & Nhiệm vụ Kiến trúc |
|---|---|---|---|
| 1 | `SecurityConstants` | `config/SecurityConstants.java` | Khai báo tiền tố Header và Hằng số Redis Key (`user:status:`, `user:logout_at:`) |
| 2 | `JwtTokenProvider` | `security/JwtTokenProvider.java` | Sinh JWT 10 phút (chứa `iat`), giải mã Email và validate chữ ký HMAC-SHA256 |
| 3 | `UserCustomDetails` | `security/UserCustomDetails.java` | Cầu nối giữa Entity `User` và `UserDetails`, chứa từ khóa `transient` chuẩn SonarQube |
| 4 | `CustomUserDetailsService` | `security/CustomUserDetailsService.java` | Fallback query CSDL PostgreSQL lấy `User` theo Email khi Redis Cache Miss |
| 5 | `JwtAuthenticationFilter` | `security/JwtAuthenticationFilter.java` | Filter đánh chặn kiểm tra 2 bước Redis (`user:status` & `user:logout_at`) + Fallback DB |
| 6 | `AuthService` | `service/AuthService.java` | Xử lý Login (cache status `ACTIVE`), Refresh Token Rotation và Logout (lưu timestamp `logout_at`) |
| 7 | `UserService` | `service/UserService.java` | Xử lý Admin cập nhật trạng thái User (lưu DB PostgreSQL + đồng bộ Redis `user:status`) |
| 8 | `ErrorCode` | `exception/ErrorCode.java` | Enum quản lý mã lỗi tập trung (`USER_BANNED_FROM_BIDDING`, `UNAUTHENTICATED`) |
| 9 | `ErrorResponse` | `exception/ErrorResponse.java` | DTO đóng gói cấu trúc phản hồi lỗi JSON chuẩn RESTful API |
