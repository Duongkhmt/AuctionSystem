# PAYMENT SERVICE (VIRTUAL WALLET & ESCROW LEDGER)

## 1. Tổng Quan Dịch Vụ
`payment-service` là **Dịch Vụ Ví Tiền Ảo & Sổ Sách Tài Chính Nội Bộ (Escrow & Wallet Ledger)** trong hệ thống Đấu Giá Microservices Monorepo (`Backend/`).

Dịch vụ hoạt động hoàn toàn **ẩn khỏi Internet (Private Network Server-to-Server)**, chỉ tiếp nhận các cuộc gọi ủy quyền từ `AUCTION-SERVICE` qua OpenFeign.

---

## 2. Công Nghệ & Hạ Tầng

- **Language & Framework:** Java 21, Spring Boot 3.2.3, Spring Data JPA, Spring Security 6
- **Service Discovery:** Spring Cloud Netflix Eureka Client (`eureka.client.service-url.defaultZone=http://localhost:8761/eureka/`)
- **Database:** PostgreSQL (`payment_db` - Độc lập 100% với `auction_db`)
- **Cổng hoạt động (Port):** `8082` (Chỉ mở nội bộ)

---

## 3. Cơ Chế Bảo Mặt Nội Bộ (100% Internal Security)

Dịch vụ không tiếp nhận kết nối trực tiếp từ Trình duyệt (Browser) hay Frontend, do đó đã **loại bỏ hoàn toàn các cấu hình JWT Filter & CORS thừa**.

- **`InternalServiceSecurityFilter`**: Bắt buộc mọi HTTP Request gửi tới phải chứa Header bí mật:
  ```http
  X-Internal-Service-Key: AuctionPaymentInternalSecretKey2026!@#
  ```
- **Constant-Time Comparison**: Sử dụng `MessageDigest.isEqual(...)` để so sánh chìa khóa bí mật, triệt tiêu 100% rủi ro tấn công đo thời gian (Timing Attack / Side-Channel Attack).

---

##  4. CSDL & Mối Quan Hệ Bảng (`payment_db`)

Dịch vụ sở hữu CSDL riêng biệt `payment_db` gồm 2 bảng cốt lõi:

1. **`wallets` (Bảng Quản Lý Ví Tiền)**:
   - `id`, `user_id` (Unique), `balance` (Số dư khả dụng), `status` (`ACTIVE`, `FROZEN`), `created_at`.
   - **Pessimistic Write Locking**: Sử dụng `@Lock(LockModeType.PESSIMISTIC_WRITE)` (`SELECT ... FOR UPDATE`) để ngăn ngừa tuyệt đối Race Condition (Lost Update) khi có 2 request trừ tiền cùng miligiây.

2. **`payment_transactions` (Sổ Cái Tài Chính Ledger)**:
   - `id`, `transaction_code`, `order_id`, `user_id`, `amount`, `status` (`SUCCESS`, `FAILED`), `idempotency_key` (Index Unique), `created_at`.
   - **Atomic Idempotency**: Tạo Index Unique trên `idempotency_key` đảm bảo 100% không bao giờ bị trừ tiền 2 lần khi client đúp chuột hoặc retry mạng.

---

## 5. Danh Sách RESTful API Nội Bộ

| Method | Endpoint | Mô Tả Nghiệp Vụ Nội Bộ |
| :--- | :--- | :--- |
| **POST** | `/v1/payments/process-order-payment` | Trừ tiền ví Buyer khi Checkout đơn thầu (Tạm giữ Escrow) |
| **POST** | `/v1/payments/disburse-seller` | Giải ngân cộng tiền ví Seller khi đơn hoàn tất (`COMPLETED`) |
| **GET** | `/v1/wallets/{userId}` | Truy vấn số dư ví của `userId` |
| **GET** | `/v1/wallets/{userId}/transactions` | Lấy lịch sử biến động sổ cái giao dịch của `userId` |

---

## 6. Hướng Dẫn Khởi Chạy

```bash
# Di chuyển vào thư mục payment-service
cd Backend/payment-service

# Biên dịch và chạy service
./mvnw spring-boot:run
```
*(Yêu cầu CSDL PostgreSQL `payment_db` đã được khởi tạo sẵn trên port 5432).*
