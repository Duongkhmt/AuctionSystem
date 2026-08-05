# 📋 PRD 01: TỔNG QUAN NGHỆ THUẬT & KIẾN TRÚC HỆ THỐNG ĐẤU GIÁ (PRODUCT OWNER SPECIFICATION)

> **Role**: Product Owner (PO) / Lead Systems Analyst  
> **System**: Sàn Đấu Giá Thương Mại Điện Tử Trực Tuyến (Auction & Buy-Now Platform)  
> **Version**: 2.0.0 (Enterprise Specification)

---

## 🎯 1. TẦM NHÌN SẢN PHẨM & MỤC TIÊU DỰ ÁN

Sản phẩm nhằm giải quyết bài toán giao dịch thương mại điện tử trực tuyến minh bạch, công bằng và linh hoạt giữa 3 thực thể: **Người Bán (Seller)**, **Người Mua (Bidder)** và **Ban Quản Trị (Admin)**.

### 2 Mục Tiêu Kinh Doanh Cốt Lõi:
1. **Tối đa hóa giá trị sản phẩm (Maximizing Asset Value)**: Giúp Người bán đạt mức giá cao nhất cho các tài sản quý hiếm thông qua Động cơ Đấu giá Anh (`ENGLISH`) và Đấu giá Bảo lưu (`RESERVE`).
2. **Tối ưu hóa tốc độ giao dịch (Accelerating Velocity)**: Giúp Người mua sở hữu ngay tức thì sản phẩm bằng Mô hình Mua Ngay Giá Cố Định (`BUY_NOW` - First come, first served) mà không tốn thời gian chờ đợi.

---

## 👥 2. MA TRẬN PHÂN QUYỀN VÀ CÁC THỰC THỂ (ACTORS & PERMISSION MATRIX)

```
┌─────────────────┐       ┌─────────────────┐       ┌─────────────────┐       ┌─────────────────┐
│  SELLER (BÁN)   │       │  BIDDER (MUA)   │       │  ADMIN (DUYỆT)  │       │ SCHEDULER ROBOT │
└────────┬────────┘       └────────┬────────┘       └────────┬────────┘       └────────┬────────┘
         │                         │                         │                         │
         ├─ Tạo bài đăng           ├─ Đặt giá thủ công       ├─ Duyệt bài (APPROVE)    ├─ Quét kích hoạt
         ├─ Hủy bài chưa chạy      ├─ Đặt trần Auto-bid      ├─ Từ chối (REJECT)       ├─ Quét đóng phiên
         └─ Đăng lại (Relist 30d)  └─ Mua ngay (Buy Now)      └─ Quản lý danh mục       └─ Quét EXPIRED 30d
```

### Chi tiết Phân Quyền Hợp Lệ:

| Quyền Hạn (Permission) | Seller | Bidder | Admin | System Robot | Điều kiện ràng buộc nghiệp vụ                                                          |
|:-----------------------|:------:|:------:|:-----:|:------------:|:---------------------------------------------------------------------------------------|
| `CREATE_PRODUCT`       |   ✅   |   ❌   |  ❌   |      ❌      | Bắt buộc chọn đúng Category active, upload 1-20 ảnh.                                   |
| `CANCEL_AUCTION`       |   ✅   |   ❌   |  ❌   |      ❌      | Chỉ được hủy khi phiên ở `PENDING_APPROVAL` hoặc `SCHEDULED` chưa có ai bid.           |
| `RELIST_AUCTION`       |   ✅   |   ❌   |  ❌   |      ❌      | Chỉ cho phép khi trạng thái phiên là `EXPIRED` (Hết 30 ngày).                          |
| `APPROVE_PRODUCT`      |   ❌   |   ❌   |  ✅   |      ❌      | Admin duyệt ➔ Chuyển `ProductStatus = APPROVED`, `AuctionStatus = RUNNING/SCHEDULED`. |
| `REJECT_PRODUCT`       |   ❌   |   ❌   |  ✅   |      ❌      | Admin từ chối ➔ Bắt buộc nhập `rejectionReason`, chuyển `CANCELLED`.                  |
| `PLACE_BID`            |   ❌   |   ✅   |  ❌   |      ❌      | Chỉ thực hiện trên phiên `ENGLISH`/`RESERVE` đang `RUNNING`. Chặn Seller tự bid.       |
| `EXECUTE_BUY_NOW`      |   ❌   |   ✅   |  ❌   |      ❌      | Chỉ thực hiện trên phiên `BUY_NOW` đang `RUNNING`. Chốt đơn sang `ENDED`.              |
| `AUTO_EXPIRE_JOBS`     |   ❌   |   ❌   |  ❌   |      ✅      | Chạy ngầm 10s/lần qua SQL Bulk Update `@Modifying`.                                    |

---

## 🔄 3. SƠ ĐỒ CHUYỂN TRẠNG THÁI VÒNG ĐỜI MA TRẬN (STATE MACHINE SPECIFICATION)

### A. Bảng Trạng Thái Sản Phẩm (`ProductStatus`)
- **`PENDING`**: Sản phẩm vừa được khởi tạo bởi Seller, đang chờ Admin kiểm duyệt.
- **`APPROVED`**: Nội dung và hình ảnh đã được Admin chấp thuận cho phép xuất bản.
- **`REJECTED`**: Bị Admin từ chối xuất bản (Có lưu vết lý do `rejectionReason`).

### B. Bảng Trạng Thái Phiên Đấu Giá (`AuctionStatus`)
- **`PENDING_APPROVAL`**: Trạng thái khởi tạo ban đầu khi chờ Admin duyệt sản phẩm.
- **`SCHEDULED`**: Đã được Admin duyệt, đang chờ đến thời điểm `startTime` để lên sàn.
- **`RUNNING`**: Phiên đang diễn ra công khai trên sàn (Người mua có thể đặt giá hoặc bấm Mua Ngay).
- **`ENDED`**: Phiên đã chốt đơn thành công (Do có người bấm Mua Ngay hoặc kết thúc hết giờ có người dẫn đầu).
- **`CANCELLED`**: Bài bị Seller chủ động hủy hoặc bị Admin từ chối.
- **`EXPIRED`**: Bài Mua Ngay (`BUY_NOW`) treo 30 ngày mà không có bất kỳ ai mua.

---

## 🗄️ 4. THIẾT KẾ DỮ LIỆU ENTITY CORE & INDEXING LOGIC

```
┌─────────────────────────┐       1:1       ┌─────────────────────────┐
│        Product          │ ─────────────── │         Auction         │
├─────────────────────────┤                 ├─────────────────────────┤
│ id (PK)                 │                 │ id (PK)                 │
│ title (VARCHAR 255)     │                 │ product_id (FK)         │
│ description (TEXT)      │                 │ auction_type (ENUM)     │
│ attributes (JSONB)      │                 │ start_price (NUMERIC)   │
│ seller_id (FK)          │                 │ reserve_price (NUMERIC) │
│ category_id (FK)        │                 │ buy_now_price (NUMERIC) │
│ status (ENUM)           │                 │ current_price (NUMERIC) │
│ rejection_reason (TEXT) │                 │ bid_step (NUMERIC)      │
│ created_at (TIMESTAMP)  │                 │ start_time (TIMESTAMP)  │
└────────────┬────────────┘                 │ end_time (TIMESTAMP)    │
             │ 1:N                          │ status (ENUM)           │
             ▼                              └────────────┬────────────┘
┌─────────────────────────┐                              │ 1:N
│      ProductImage       │                              ▼
├─────────────────────────┤                 ┌─────────────────────────┐
│ id (PK)                 │                 │           Bid           │
│ product_id (FK)         │                 ├─────────────────────────┤
│ image_url (VARCHAR 500) │                 │ id (PK)                 │
│ public_id (VARCHAR 255) │                 │ auction_id (FK)         │
│ display_order (INT)     │                 │ bidder_id (FK)          │
└─────────────────────────┘                 │ bid_amount (NUMERIC)    │
                                            │ max_auto_bid (NUMERIC)  │
                                            │ is_auto_bid (BOOLEAN)   │
                                            │ created_at (TIMESTAMP)  │
                                            └─────────────────────────┘
```
