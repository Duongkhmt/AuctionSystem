# 📋 PRD 02: CHI TIẾT NGHIỆP VỤ QUẢN LÝ SẢN PHẨM & NGƯỜI BÁN (SELLER SPECIFICATION)

> **Role**: Product Owner (PO)  
> **Module**: Seller Product Management (Create, Edit, Cancel, Relist)  
> **Version**: 2.0.0 (Enterprise Specification)

---

## 📝 1. CHỨC NĂNG: ĐĂNG SẢN PHẨM MỚI (CREATE PRODUCT)

### A. Mô Tả Nghiệp Vụ & User Story
> *"Là một Người Bán (Seller), tôi muốn tạo bài đăng sản phẩm mới kèm danh sách từ 1-20 hình ảnh và chọn mô hình bán (ENGLISH, RESERVE, BUY_NOW) 
> để chờ Admin kiểm duyệt."*

### B. Tiêu Chí Chấp Nhận (Acceptance Criteria - AC)
- **AC-01 (Category Status)**: Danh mục sản phẩm `categoryId` được chọn bắt buộc phải tồn tại và đang ở trạng thái `isActive = true`.
- **AC-02 (Image Upload Bounds)**: Danh sách file ảnh `images` bắt buộc có số lượng `1 <= count <= 20`.
- **AC-03 (Cloudinary Rollback Guarantee)**: Nếu quá trình lưu Database bị lỗi giữa chừng (Rollback), toàn bộ file ảnh vừa được đẩy lên Cloudinary bắt buộc phải bị tự động xóa bỏ tận gốc.
- **AC-04 (Auction Type Validation)**:
  - Nếu `auctionType == RESERVE` ➔ BẮT BUỘC `reservePrice != null` và `reservePrice >= startPrice`.
  - Nếu `auctionType == BUY_NOW` ➔ BẮT BUỘC `buyNowPrice != null` và `buyNowPrice > 0`.
  - Nếu `auctionType == ENGLISH` ➔ KHÔNG ĐƯỢC CÓ `reservePrice`.

### C. Chi Tiết API Specification

```http
POST /v1/sellers/{sellerId}/products
Content-Type: multipart/form-data
```

#### Request Parameters & Form-Data:
| Parameter      | Type          | Required | Constraint                      | Description                                 |
|:---------------|:--------------|:--------:|:--------------------------------|:--------------------------------------------|
| `title`        | String        |   Yes    | 5 - 255 chars                   | Tên tiêu đề bài đăng                        |
| `description`  | String        |   Yes    | Max 5000 chars                  | Mô tả chi tiết sản phẩm                     |
| `categoryId`   | Long          |   Yes    | Valid FK                        | ID danh mục sản phẩm                        |
| `auctionType`  | Enum          |   Yes    | `ENGLISH`, `RESERVE`, `BUY_NOW` | Loại hình đấu giá / bán thẳng               |
| `startPrice`   | BigDecimal    |   Yes    | `>= 0.01`                       | Giá khởi điểm                               |
| `reservePrice` | BigDecimal    | Optional | `>= startPrice`                 | Giá bảo lưu (Bắt buộc nếu RESERVE)          |
| `buyNowPrice`  | BigDecimal    | Optional | `>= 0.01`                       | Giá mua ngay cố định (Bắt buộc nếu BUY_NOW) |
| `bidStep`      | BigDecimal    |   Yes    | `>= 0.01`                       | Bước giá tối thiểu                          |
| `startTime`    | LocalDateTime |   Yes    | `>= NOW()`                      | Thời gian bắt đầu dự kiến                   |
| `endTime`      | LocalDateTime |   Yes    | `>= startTime + 30 mins`        | Thời gian kết thúc dự kiến                  |
| `images`       | List<File>    |   Yes    | 1 to 20 files                   | Danh sách file ảnh upload                   |

---

## 🔄 2. CHỨC NĂNG: ĐĂNG LẠI SẢN PHẨM HẾT HẠN (RELIST AUCTION)

### A. Mô Tả Nghiệp Vụ & User Story
> *"Là một Người Bán, khi sản phẩm Mua Ngay của tôi treo 30 ngày mà không ai mua (EXPIRED), tôi muốn bấm nút 
> [Đăng lại] để gia hạn 30 ngày mới mà không cần nhập lại bài từ đầu."*

### B. Tiêu Chí Chấp Nhận (Acceptance Criteria - AC)
- **AC-01 (Permission Check)**: Chỉ có chính chủ Seller sở hữu sản phẩm mới được quyền thực hiện Relist.
- **AC-02 (Strict State Restriction)**: CHỈ CHO PHÉP RELIST KHI `AuctionStatus == EXPIRED`. Ném lỗi `AUCTION_NOT_RELISTABLE` nếu bài bị `CANCELLED` hoặc ở trạng thái khác.
- **AC-03 (Immediate Reactivation)**: Không cần Admin duyệt lại lần 2. Bài nhảy thẳng sang `RUNNING` công khai ngay.
- **AC-04 (Time Reset)**: Cập nhật `startTime = NOW()`, `endTime = NOW() + 30 days`.

### C. Chi Tiết API Specification

```http
POST /v1/sellers/{sellerId}/auctions/{auctionId}/relist
Content-Type: application/json
```

#### Response Success (200 OK):
```json
{
  "productId": 105,
  "title": "Điện thoại iPhone 15 Pro Max 256GB",
  "auctionId": 402,
  "auctionType": "BUY_NOW",
  "buyNowPrice": 28000000.00,
  "auctionStatus": "RUNNING",
  "startTime": "2026-08-04T17:00:00",
  "endTime": "2026-09-03T17:00:00"
}
```

---

## 📊 3. BẢNG BẢO THỦ & LỖI NGHIỆP VỤ (VALIDATION & ERROR MATRIX)

| Mã Lỗi (ErrorCode)       | HTTP Status | Thông điệp nguyên văn (Message)                                      | Nguyên nhân kích hoạt                           |
|:-------------------------|:-----------:|:---------------------------------------------------------------------|:------------------------------------------------|
| `BUY_NOW_PRICE_REQUIRED` |     400     | "Loại hình BUY_NOW bắt buộc phải nhập giá mua ngay"                  | Đăng bài BUY_NOW nhưng để `buyNowPrice = null`  |
| `RESERVE_PRICE_REQUIRED` |     400     | "Loại đấu giá RESERVE bắt buộc phải có giá bảo lưu"                  | Đăng bài RESERVE nhưng để `reservePrice = null` |
| `AUCTION_NOT_RELISTABLE` |     400     | "Chỉ sản phẩm ở trạng thái Hết Hạn (EXPIRED) mới được phép Đăng Lại" | Bấm Đăng lại bài bị `CANCELLED` hoặc `ENDED`    |
| `UNAUTHORIZED_ACCESS`    |     403     | "Bạn không có quyền thao tác trên sản phẩm này"                      | Seller A gọi API sửa/relist bài của Seller B    |
