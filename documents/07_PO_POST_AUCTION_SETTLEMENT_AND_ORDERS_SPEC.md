# 07. TÀI LIỆU ĐẶC TẢ CHI TIẾT NGHIỆP VỤ API HẬU ĐẤU GIÁ & QUẢN LÝ ĐƠN HÀNG (POST-AUCTION & ORDER API SPECIFICATION)

---

## 🎯 1. BỐI CẢNH NGHIỆP VỤ BACKEND

Tài liệu này quy định chi tiết **Quy tắc Nghiệp vụ (Business Rules), Luồng Xử Lý Transaction, Mã Lỗi ErrorCodes và Chi Tiết Cấu Trúc Request/Response DTO** cho toàn bộ 5 API quản lý Đơn Hàng hậu đấu giá trên Spring Boot Backend.

---

## 📊 2. THIẾT KẾ CƠ SỞ DỮ LIỆU BACKEND (DATABASE SCHEMA)

### 2.1. Bảng `orders` (Quản lý Đơn hàng trúng thầu)
```sql
-- CREATE TABLE orders (
--     id BIGSERIAL PRIMARY KEY,
--     auction_id BIGINT NOT NULL UNIQUE REFERENCES auctions(id),
--     product_id BIGINT NOT NULL REFERENCES products(id),
--     buyer_id BIGINT NOT NULL REFERENCES users(id),
--     seller_id BIGINT NOT NULL REFERENCES users(id),
--     winning_price DECIMAL(15, 2) NOT NULL,
--     shipping_address TEXT,
--     phone_number VARCHAR(20),
--     courier_name VARCHAR(50),
--     tracking_number VARCHAR(100),
--     status VARCHAR(30) NOT NULL DEFAULT 'UNPAID', -- UNPAID, PAID, SHIPPING, COMPLETED, CANCELLED
--     created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
--     updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
-- );
```

### 2.2. Bảng `payments` (Lưu vết giao dịch thanh toán)
```sql
-- CREATE TABLE payments (
--     id BIGSERIAL PRIMARY KEY,
--     order_id BIGINT NOT NULL REFERENCES orders(id),
--     amount DECIMAL(15, 2) NOT NULL,
--     payment_method VARCHAR(30) NOT NULL, -- VNPAY, WALLET, BANK_TRANSFER
--     transaction_code VARCHAR(100) UNIQUE,
--     status VARCHAR(30) NOT NULL DEFAULT 'SUCCESS',
--     created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
-- );
```

---

## ⚙️ 3. LUỒNG TỰ ĐỘNG HÓA KHI HẾT GIỜ (AUCTION END SCHEDULER)

### 🔄 Luồng xử lý chi tiết của Robot Scheduler:
1. Định kỳ 10 giây/lần, Robot quét các phiên `auctions` có `status = 'RUNNING'` và `end_time <= NOW()`.
2. Kiểm tra bản ghi `bids` cao nhất (`findTopByAuctionIdOrderByBidAmountDesc`):
   - **Trường hợp 1 (Có lượt Bid trúng thầu)**:
     - Đổi `auctions.status = 'ENDED'`.
     - Tự động chèn 1 bản ghi mới vào bảng `orders`:
       - `buyer_id` = ID người thắng cuộc (`highestBid.bidder.id`).
       - `winning_price` = `highestBid.bidAmount` (hoặc `auction.currentPrice`).
       - `status` = `'UNPAID'`.
     - Bắn Event phát Email / Notification mừng trúng thầu cho Buyer & Seller.
   - **Trường hợp 2 (Không có lượt Bid nào)**:
     - Đổi `auctions.status = 'EXPIRED'` (Hết hạn 30 ngày).

---

## 📡 4. ĐẶC TẢ CHI TIẾT NGHIỆP VỤ CÁC REST API BACKEND

### 📥 API 1: `GET /v1/bidders/{bidderId}/won-auctions`
**Mô tả**: Người mua (Bidder) truy vấn danh sách tất cả các sản phẩm mà mình đã đấu giá thắng cuộc.

#### 🛡️ Quy tắc kiểm tra (Business Rules & Validations):
1. Kiểm tra `bidderId` có tồn tại trong hệ thống hay không. Nếu không $\rightarrow$ Ném lỗi `USER_NOT_FOUND` (`404`).
2. Chỉ lấy ra các bản ghi trong bảng `orders` mà `buyer_id = bidderId`.
3. Sắp xếp danh sách theo thời gian khởi tạo mới nhất (`created_at DESC`).

#### 📤 Response DTO (`200 OK`):
```json
[
  {
    "order_id": 101,
    "auction_id": 2,
    "product_id": 2,
    "product_title": "ĐỒ CỔ NGHỆ THUẬT BÌNH HOA NĂM 2026",
    "product_image": "https://res.cloudinary.com/demo/image/upload/vase.jpg",
    "winning_price": 20000000,
    "status": "UNPAID",
    "shipping_address": null,
    "phone_number": null,
    "courier_name": null,
    "tracking_number": null,
    "created_at": "2026-08-05T17:57:00"
  }
]
```

---

### 📥 API 2: `POST /v1/orders/{orderId}/checkout`
**Mô tả**: Người mua thắng cuộc thực hiện chốt địa chỉ nhận hàng và tiến hành thanh toán cho đơn hàng.

#### 🛡️ Quy tắc kiểm tra (Business Rules & Validations):
1. Kiểm tra `orderId` có tồn tại không. Nếu không $\rightarrow$ Ném lỗi `ORDER_NOT_FOUND` (`404`).
2. Kiểm tra `buyer_id` trong đơn hàng có khớp với ID người dùng gửi request không. Nếu không $\rightarrow$ Ném lỗi `UNAUTHORIZED_ACCESS` (`403`).
3. Kiểm tra trạng thái đơn hàng: BẮT BUỘC phải là `UNPAID`. Nếu đơn hàng đang ở trạng thái `PAID`, `SHIPPING`, `COMPLETED` $\rightarrow$ Ném lỗi `ORDER_ALREADY_PAID` (`400`).
4. Validate dữ liệu đầu vào:
   - `shipping_address`: Không được để rỗng (`@NotBlank`).
   - `phone_number`: Bắt buộc đúng định dạng số điện thoại Việt Nam 10 chữ số (`@Pattern(regexp = "^(0[3|5|7|8|9])+([0-9]{8})$")`).
   - `payment_method`: Phải thuộc các enum hợp lệ (`VNPAY`, `WALLET`, `BANK_TRANSFER`).

#### 🔄 Các bước xử lý Transaction (`@Transactional`):
1. Cập nhật `shipping_address`, `phone_number` vào bản ghi `orders`.
2. Tạo bản ghi mới trong bảng `payments`:
   - `amount` = `order.winning_price`.
   - `payment_method` = `request.payment_method`.
   - `transaction_code` = Mã giao dịch duy nhất sinh ngẫu nhiên (Ví dụ `TXN-20260805-99812`).
   - `status` = `'SUCCESS'`.
3. Chuyển trạng thái đơn hàng `orders.status = 'PAID'`.

#### 📥 Request Body:
```json
{
  "shipping_address": "123 Đường Nguyễn Huệ, Phường Bến Nghé, Quận 1, TP. Hồ Chí Minh",
  "phone_number": "0901234567",
  "payment_method": "VNPAY"
}
```

#### 📤 Response DTO (`200 OK`):
```json
{
  "order_id": 101,
  "status": "PAID",
  "winning_price": 20000000,
  "transaction_code": "TXN-20260805-99812",
  "message": "Thanh toán đơn hàng thành công! Đơn hàng đã chuyển sang trạng thái chờ Seller đóng gói."
}
```

---

### 📥 API 3: `GET /v1/sellers/{sellerId}/orders`
**Mô tả**: Người bán (Seller) xem danh sách tất cả các đơn hàng đã đấu giá thành công do shop mình đăng bán.

#### 🛡️ Quy tắc kiểm tra (Business Rules & Validations):
1. Kiểm tra `sellerId` có tồn tại và thuộc vai trò `ROLE_SELLER` / `ROLE_ADMIN` hay không.
2. Lấy danh sách các đơn hàng từ bảng `orders` mà `seller_id = sellerId`.
3. Hỗ trợ lọc tùy chọn theo tham số `status` (`UNPAID`, `PAID`, `SHIPPING`, `COMPLETED`).

#### 📤 Response DTO (`200 OK`):
```json
[
  {
    "order_id": 101,
    "product_id": 2,
    "product_title": "ĐỒ CỔ NGHỆ THUẬT BÌNH HOA NĂM 2026",
    "winning_price": 20000000,
    "buyer_name": "Quốc Anh",
    "buyer_phone": "0901234567",
    "shipping_address": "123 Đường Nguyễn Huệ, Quận 1, TP.HCM",
    "status": "PAID",
    "courier_name": null,
    "tracking_number": null,
    "created_at": "2026-08-05T17:57:00"
  }
]
```

---

### 📥 API 4: `PUT /v1/sellers/{sellerId}/orders/{orderId}/ship`
**Mô tả**: Người bán cập nhật mã vận đơn và đơn vị vận chuyển sau khi đóng gói sản phẩm xong.

#### 🛡️ Quy tắc kiểm tra (Business Rules & Validations):
1. Kiểm tra `orderId` có tồn tại và thuộc sở hữu của `sellerId` hay không.
2. **Quy tắc quan trọng**: Trạng thái đơn hàng BẮT BUỘC phải là `PAID` (Người mua ĐÃ THANH TOÁN TIỀN thì Seller mới được phép xuất hàng). Nếu trạng thái vẫn đang là `UNPAID` $\rightarrow$ Ném lỗi `CANNOT_SHIP_UNPAID_ORDER` (`400`).
3. Validate đầu vào: `courier_name` và `tracking_number` không được rỗng (`@NotBlank`).

#### 🔄 Các bước xử lý Transaction (`@Transactional`):
1. Cập nhật `courier_name` (Ví dụ `Giao Hàng Tiết Kiệm`), `tracking_number` (Ví dụ `GHTK-981244`).
2. Chuyển trạng thái đơn hàng `orders.status = 'SHIPPING'`.

#### 📥 Request Body:
```json
{
  "courier_name": "Giao Hàng Tiết Kiệm",
  "tracking_number": "GHTK-981244"
}
```

#### 📤 Response DTO (`200 OK`):
```json
{
  "order_id": 101,
  "status": "SHIPPING",
  "courier_name": "Giao Hàng Tiết Kiệm",
  "tracking_number": "GHTK-981244",
  "message": "Đã cập nhật mã vận chuyển thành công! Đơn hàng đang trên đường giao tới Buyer."
}
```

---

### 📥 API 5: `PUT /v1/bidders/{bidderId}/orders/{orderId}/confirm-received`
**Mô tả**: Người mua bấm nút xác nhận "Đã nhận được hàng thành công" để hoàn tất chu trình đơn.

#### 🛡️ Quy tắc kiểm tra (Business Rules & Validations):
1. Kiểm tra `orderId` thuộc về `bidderId`.
2. Trạng thái đơn hàng BẮT BUỘC phải là `SHIPPING` $\rightarrow$ Nếu chưa giao hàng mà bấm xác nhận thì ném lỗi `ORDER_NOT_IN_SHIPPING_STATE` (`400`).

#### 🔄 Các bước xử lý Transaction (`@Transactional`):
1. Chuyển `orders.status = 'COMPLETED'`.
2. Giải phóng số tiền thanh toán từ ví hệ thống chuyển về cho ví Người Bán (Seller).

#### 📤 Response DTO (`200 OK`):
```json
{
  "order_id": 101,
  "status": "COMPLETED",
  "message": "Đơn hàng đã được hoàn tất thành công! Cảm ơn bạn đã tham gia đấu giá."
}
```

---

## 🧪 5. MÃ LỖI ERROR CODES BỔ SUNG TRONG BACKEND (`ErrorCode.java`)

| Mã Lỗi (ErrorCode) | HTTP Status | Thông Báo Lỗi Tiếng Việt Thân Thiện |
| :--- | :---: | :--- |
| `ORDER_NOT_FOUND` | `404` | Không tìm thấy thông tin đơn hàng trúng thầu |
| `ORDER_ALREADY_PAID` | `400` | Đơn hàng này đã được thanh toán trước đó |
| `CANNOT_SHIP_UNPAID_ORDER` | `400` | Không thể giao hàng cho đơn chưa được người mua thanh toán |
| `ORDER_NOT_IN_SHIPPING_STATE` | `400` | Đơn hàng chưa ở trạng thái đang vận chuyển |
| `INVALID_PHONE_NUMBER` | `400` | Số điện thoại giao hàng không hợp lệ |
