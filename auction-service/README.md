# AUCTION SERVICE (CORE ENGINE & SINGLE ENTRY POINT)

---

## 1. TỔNG QUAN DỊCH VỤ

`auction-service` (Port 8080) là **Dịch Vụ Đấu Giá Chính, Quản Lý Đơn Hàng & Cổng Tiếp Nhận DUY NHẤT (Single Entry Point)** cho ứng dụng Frontend trong hệ thống Microservices Monorepo Backend (`Backend/`).

Dịch vụ đảm nhận toàn bộ nghiệp vụ cốt lõi của sàn đấu giá:
- **Quản lý sản phẩm & danh mục**: Đăng bài sản phẩm với thuộc tính động `JSONB`, tích hợp tải ảnh mây Cloudinary CDN (từ 1 đến 20 ảnh/bài).
- **Phê duyệt bài đăng (Admin Moderation)**: Duyệt (`Approve`) hoặc Từ chối (`Reject`) bài đăng chờ duyệt (`PENDING`).
- **Động cơ so kè giá nguyên tử (Redis Atomic Lua Script 0.02ms)**: Xử lý 100+ thầu đồng thời, triệt tiêu Race Condition.
- **Tự động đấu giá (Proxy Bidding Engine)**: Tự động nhảy giá giữ vị trí dẫn đầu cho Bidder theo mức giá trần cài đặt.
- **Chốt thầu thời gian cứng (Hard-Close Mode)**: Hết giờ là hết giờ.
- **Mua Ngay (Buy Now)**: Chốt ngay sản phẩm với mức giá niêm yết cố định.
- **Vòng đời đơn hàng hậu đấu giá**: `UNPAID` ➔ `PAID` ➔ `SHIPPING` ➔ `COMPLETED` | `CANCELLED`.
- **Robot Tự Động Hóa (`AuctionScheduler` 10s/lần)**: Chốt winner, kích hoạt phiên, retry thanh toán 50 đơn/lượt, tự động hủy đơn bùng 48h & phạt gậy vi phạm (`3-Strikes Penalty` cấm 90 ngày).
- **Trạm Ủy Quyền Ví Tiền (`WalletProxyController`)**: Tiếp nhận request xem ví `/v1/wallets/me` từ Frontend, rút `userId` từ JWT và uỷ quyền gọi `payment-service` qua OpenFeign.

---

## 2. THÔNG SỐ KỸ THUẬT & CÔNG NGHỆ

- **Ngôn ngữ & Framework:** Java 21, Spring Boot 3.2.3, Spring Cloud 2023.0.0
- **Service Discovery:** Eureka Client (`eureka.client.service-url.defaultZone=http://localhost:8761/eureka/`)
- **Cổng hoạt động (Port):** `8080` (Cổng duy nhất tiếp nhận request từ Frontend)
- **Cơ sở dữ liệu:** PostgreSQL (`auction_db` - 8 bảng CSDL)
- **Cache & Concurrency:** Redis 7 (Lua Script Atomic Bidding), Redisson 3.35.0 (Distributed Lock)
- **Messaging:** Apache Kafka (Spring Kafka) - Non-Blocking Retry Topic 3 tầng (`-retry`, `-dlt`)
- **Third-party SDK:** Cloudinary HTTP5 2.0.0 (Quản lý ảnh CDN)

---

## 3. CẤU TRÚC CƠ SỞ DỮ LIỆU (`auction_db`)

Dịch vụ sở hữu riêng CSDL `auction_db` gồm 8 bảng:

1. **`users`**: Người dùng (Admin, Seller, Buyer), gậy vi phạm `unpaid_strike_count`, thời hạn phạt `banned_until`.
2. **`categories`**: Danh mục sản phẩm tự tham chiếu phân cấp (`parent_id`).
3. **`products`**: Bài đăng sản phẩm, cột `attributes` lưu dữ liệu động dạng `JSONB`.
4. **`product_images`**: Đường dẫn URL & `public_id` ảnh Cloudinary CDN.
5. **`auctions`**: Cấu hình & trạng thái phiên thầu (`startTime`, `endTime`, `startingPrice`, `stepPrice`, `reservePrice`, `buyNowPrice`, `status`, `winner_id`).
6. **`bids`**: Nhật ký lượt đặt giá (Audit Trail). Composite Index `(auction_id, bid_amount DESC)`.
7. **`orders`**: Đơn hàng trúng thầu hậu đấu giá (`winning_price`, `payment_deadline = 48h`, `status`).
8. **`payments`**: Nhật ký thanh toán đơn hàng.

---

## 4. CÁC THÀNH PHẦN CỐT LÕI (CORE COMPONENTS)

- **`RedisAtomicBiddingEngine`**: Chạy Lua script nguyên tử gộp `Đọc ➔ Kiểm tra ➔ Ghi giá` trên RAM Redis trong 0.02ms.
- **`AuctionScheduler`**: Robot chạy ngầm `@Scheduled(fixedRate = 10000)` (10s/lần) chuyển phiên `RUNNING`, chốt `winner`, retry 50 đơn thanh toán/lần, hủy đơn bùng 48h & phạt gậy.
- **`WalletProxyController`**: Controller uỷ quyền tiếp nhận `GET /v1/wallets/me` và `GET /v1/wallets/me/transactions` ở Port 8080, trích xuất `userId` từ JWT `UserCustomDetails`, gọi `PaymentFeignClient`.
- **`PaymentFeignClient` & `PaymentFeignFallback`**: OpenFeign client gọi sang `PAYMENT-SERVICE`. Tích hợp Resilience4j Circuit Breaker: ném `ApplicationException` (HTTP 503) khi truy vấn ví sập; gia hạn 24h khi trừ tiền sập.

---

## 5. DANH SÁCH REST API ENDPOINTS (PORT 8080)

### 🔹 1. Public Marketplace & Categories
| Method | Endpoint | Mô Tả |
| :--- | :--- | :--- |
| **GET** | `/v1/categories` | Lấy danh sách danh mục sản phẩm đang hoạt động |
| **GET** | `/v1/products` | Danh sách sản phẩm công khai đã duyệt (`APPROVED`) |
| **GET** | `/v1/products/{id}` | Chi tiết sản phẩm & trạng thái phiên thầu |

### 🔹 2. Seller Portal (Dành cho Người Bán)
| Method | Endpoint | Mô Tả |
| :--- | :--- | :--- |
| **POST** | `/v1/sellers/{sellerId}/products` | Đăng bài sản phẩm + phiên thầu mới (Up 1-20 ảnh) |
| **PUT** | `/v1/sellers/{sellerId}/products/{id}` | Cập nhật bài đăng sản phẩm |
| **DELETE** | `/v1/sellers/{sellerId}/products/{id}` | Xóa bài đăng sản phẩm |
| **PUT** | `/v1/sellers/{sellerId}/products/{id}/cancel` | Người bán chủ động hủy phiên thầu |
| **POST** | `/v1/sellers/{sellerId}/auctions/{auctionId}/relist` | Đăng lại phiên thầu đã hết hạn (`EXPIRED`) |
| **GET** | `/v1/sellers/{sellerId}/orders` | Quản lý đơn hàng bán được |
| **PUT** | `/v1/sellers/{sellerId}/orders/{orderId}/ship` | Nhập mã vận đơn & xuất hàng |

### 🔹 3. Bidder Portal & Wallet Proxy (Dành cho Người Mua)
| Method | Endpoint | Mô Tả |
| :--- | :--- | :--- |
| **POST** | `/v1/auctions/{auctionId}/bids` | Đặt giá thầu (Bid) mới |
| **GET** | `/v1/auctions/{auctionId}/bids` | Lịch sử đặt giá công khai (ẩn danh) |
| **POST** | `/v1/auctions/{auctionId}/buy-now` | Mua Ngay sản phẩm |
| **GET** | `/v1/bidders/{bidderId}/won-auctions` | Danh sách sản phẩm trúng thầu |
| **POST** | `/v1/bidders/{bidderId}/orders/{orderId}/checkout` | Checkout thanh toán đơn thầu |
| **PUT** | `/v1/bidders/{bidderId}/orders/{orderId}/confirm-received` | Xác nhận đã nhận hàng thành công |
| **GET** | `/v1/wallets/me` | Xem số dư ví cá nhân (Proxy sang `payment-service`) |
| **GET** | `/v1/wallets/me/transactions` | Xem lịch sử giao dịch cá nhân (Proxy sang `payment-service`) |

### 🔹 4. Admin Moderation (Dành cho Quản Trị Viên)
| Method | Endpoint | Mô Tả |
| :--- | :--- | :--- |
| **GET** | `/v1/admin/products/pending` | Danh sách bài đăng chờ kiểm duyệt (`PENDING`) |
| **PUT** | `/v1/admin/products/{id}/approve` | Chấp thuận phê duyệt bài đăng |
| **PUT** | `/v1/admin/products/{id}/reject` | Từ chối phê duyệt bài đăng kèm lý do |

---

## 6. HƯỚNG DẪN BIÊN DỊCH & CHẠY DỰ ÁN

```bash
# Di chuyển vào thư mục service
cd Backend/auction-service

# Biên dịch thử nghiệm (Skip Tests)
./mvnw clean compile -DskipTests

# Chạy Unit Tests
./mvnw test

# Khởi chạy ứng dụng
./mvnw spring-boot:run
```
