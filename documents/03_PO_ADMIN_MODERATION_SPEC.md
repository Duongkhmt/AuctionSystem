# 📋 PRD 03: CHI TIẾT QUY TRÌNH KIỂM DUYỆT BÀI ĐĂNG ADMIN (ADMIN MODERATION SPECIFICATION)

> **Role**: Product Owner (PO)  
> **Module**: Admin Moderation & Quality Control  
> **Version**: 2.0.0 (Enterprise Specification)

---

## 🛡️ 1. CHỨC NĂNG: DUYỆT BÀI ĐĂNG (APPROVE PRODUCT)

### A. Mô Tả Nghiệp Vụ & User Story
> *"Là một Admin, tôi muốn xem danh sách các sản phẩm đang chờ duyệt (`ProductStatus = PENDING`) và bấm nút [Chấp Thuận] để kích hoạt bài đăng lên sàn đấu giá."*

### B. Tiêu Chí Chấp Nhận (Acceptance Criteria - AC)
- **AC-01 (Expiration Check)**: Nếu `endTime` của sản phẩm đã trôi qua trước khi Admin kịp duyệt ➔ Chặn duyệt và ném lỗi `AUCTION_EXPIRED_BEFORE_APPROVAL`.
- **AC-02 (State Branching)**:
  - Nếu `startTime <= NOW()` hoặc Loại hình bài đăng là `BUY_NOW` ➔ Set `AuctionStatus = RUNNING` ngay tại chỗ!
  - Nếu `startTime > NOW()` (Bài đấu giá hẹn giờ trong tương lai) ➔ Set `AuctionStatus = SCHEDULED`.
- **AC-03 (Product State Update)**: Set `ProductStatus = APPROVED`.

### C. Chi Tiết API Specification

```http
PUT /v1/admin/products/{productId}/approve
Content-Type: application/json
```

#### Response Success (200 OK):
```json
{
  "productId": 105,
  "status": "APPROVED",
  "auctionId": 402,
  "auctionStatus": "RUNNING",
  "startTime": "2026-08-04T17:00:00",
  "endTime": "2026-09-03T17:00:00"
}
```

---

## ❌ 2. CHỨC NĂNG: TỪ CHỐI BÀI ĐĂNG (REJECT PRODUCT)

### A. Mô Tả Nghiệp Vụ & User Story
> *"Là một Admin, khi phát hiện bài đăng chứa hình ảnh vi phạm hoặc thông tin sai lệch, tôi muốn bấm nút [Từ Chối] và nhập lý do cụ thể để Seller biết sửa đổi."*

### B. Tiêu Chí Chấp Nhận (Acceptance Criteria - AC)
- **AC-01 (Mandatory Reason)**: Thâm số `rejectionReason` bắt buộc phải có độ dài từ 5 đến 1000 ký tự.
- **AC-02 (State Update)**: Set `ProductStatus = REJECTED` và `AuctionStatus = CANCELLED`.
- **AC-03 (Audit Log)**: Lý do từ chối được lưu trữ vĩnh viễn trong cột `product.rejection_reason`.

### C. Chi Tiết API Specification

```http
PUT /v1/admin/products/{productId}/reject
Content-Type: application/json
```

#### Request Body:
```json
{
  "rejectionReason": "Hình ảnh sản phẩm bị mờ, không rõ nhãn hiệu và chứng nhận xuất xứ."
}
```

---

## 📊 3. BẢNG MÃ LỖI ADMIN MODERATION (ERROR MATRIX)

| Mã Lỗi (ErrorCode)                | HTTP Status | Thông điệp nguyên văn (Message)                                           | Nguyên nhân kích hoạt                                            |
|:----------------------------------|:-----------:|:--------------------------------------------------------------------------|:-----------------------------------------------------------------|
| `AUCTION_EXPIRED_BEFORE_APPROVAL` |     400     | "Thời gian đấu giá đã trôi qua trong lúc chờ duyệt, không thể chấp thuận" | Admin bấm duyệt bài hẹn giờ nhưng giờ `endTime` đã qua           |
| `PRODUCT_NOT_PENDING`             |     400     | "Bài đăng hiện không ở trạng thái chờ duyệt"                              | Bấm duyệt/từ chối bài đăng đã được duyệt hoặc đã bị hủy trước đó |
