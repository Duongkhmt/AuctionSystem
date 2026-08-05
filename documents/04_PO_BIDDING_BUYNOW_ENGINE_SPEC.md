# 📋 PRD 04: CHI TIẾT ĐỘNG CƠ ĐẤU GIÁ & MUA NGAY (BIDDING & BUY-NOW ENGINE SPECIFICATION)

> **Role**: Product Owner (PO)  
> **Module**: Core Bidding Engine, Proxy Auto-Bid, Buy-Now & Security Rules  
> **Version**: 2.0.0 (Enterprise Specification)

---

## ⚡ 1. CHỨC NĂNG: MUA NGAY GIÁ CỐ ĐỊNH (BUY-NOW INSTANT PURCHASE)

### A. Mô Tả Nghiệp Vụ & User Story
> *"Là một Người Mua (Bidder), tôi muốn bấm nút [⚡ MUA NGAY] để mua đứt sản phẩm theo giá niêm yết mà không cần chờ đấu giá hết giờ."*

### B. Tiêu Chí Chấp Nhận (Acceptance Criteria - AC)
- **AC-01 (Type Check)**: CHỈ BÀI ĐĂNG CÓ `auctionType == BUY_NOW` MỚI ĐƯỢC PHÉP THỰC HIỆN API NÀY. Ném lỗi `BUY_NOW_NOT_SUPPORTED` nếu áp dụng lên phiên `ENGLISH`/`RESERVE`.
- **AC-02 (Data Integrity Check)**: Nếu `buyNowPrice == null` ➔ Ném lỗi `BUY_NOW_PRICE_NOT_SET` (Tuyệt đối không lấy `startPrice` chữa cháy).
- **AC-03 (Status Check)**: Phiên bắt buộc đang ở trạng thái `RUNNING` và thời gian hiện tại `NOW() <= endTime`.
- **AC-04 (Anti-Self-Purchase)**: Người bán không được phép mua bài của chính mình (`CANNOT_BID_OWN_PRODUCT`).
- **AC-05 (Instant Closure)**:
  - Cập nhật `auction.currentPrice = buyNowPrice`.
  - Chuyển `auction.status = ENDED` khóa phiên lập tức.
  - Lưu 1 bản ghi `Bid` duy nhất (`bidAmount = buyNowPrice`, `autoBid = false`).

### C. Chi Tiết API Specification

```http
POST /v1/auctions/{auctionId}/buy-now?bidderId=2
Content-Type: application/json
```

#### Response Success (200 OK):
```json
{
  "bidId": 8901,
  "auctionId": 402,
  "maskedBidderName": "d***g",
  "bidAmount": 28000000.00,
  "newCurrentPrice": 28000000.00,
  "nextMinBidAmount": 28500000.00,
  "timeExtended": false,
  "newEndTime": "2026-09-03T17:00:00",
  "createdAt": "2026-08-04T17:15:30"
}
```

---

## 🤖 2. ĐỘNG CƠ ĐẤU GIÁ TỰ ĐỘNG (PROXY BIDDING ENGINE - EBAY STYLE)

### A. Quy Tắc Thuật Toán Nhảy Giá Tự Động
Giả sử Sản phẩm X có: `startPrice = 5,000,000đ`, `bidStep = 100,000đ`.

```
[Thời điểm T1]: Bidder A cài maxAutoBid = 10,000,000đ.
               -> Giá công khai hiển thị: 5,000,000đ (Dẫn đầu).
               
[Thời điểm T2]: Bidder B vào đặt giá thủ công = 7,000,000đ.
               -> Engine phát hiện maxAutoBid (10M) > 7M.
               -> Engine tự động phản công: Tạo Bid đè cho B = 7,100,000đ (+1 Step).
               -> Giá công khai mới: 7,100,000đ (A vẫn giữ vị trí dẫn đầu).
               
[Thời điểm T3]: Bidder C vào cài maxAutoBid = 12,000,000đ.
               -> Engine so sánh maxAutoBid C (12M) vs maxAutoBid A (10M).
               -> C thắng! Engine nâng giá công khai của C lên = 10,100,000đ (10M + 1 Step).
```

### B. Công Thức Bước Giá Động (`BidStepCalculator`)
- Nếu `currentPrice < 1,000,000đ` ➔ Bước giá tối thiểu = `10,000đ`.
- Nếu `1,000,000đ <= currentPrice < 10,000,000đ` ➔ Bước giá tối thiểu = `100,000đ`.
- Nếu `currentPrice >= 10,000,000đ` ➔ Bước giá tối thiểu = `500,000đ`.

---

## 🛡️ 3. AN TOÀN SÀN & ANTIMISCONDUCT RULES

### A. Gia Hạn Phút Chót (Soft-Close Anti-Sniping)
- Nếu lượt đặt giá diễn ra trong khoảng thời gian `[endTime - 3 mins, endTime]`:
  - `auction.endTime` tự động cộng thêm `+3 phút`.
  - Trả về `timeExtended = true` trong `BidResponseDTO` để Frontend hiển thị hiệu ứng gia hạn.

### B. Bảo Mật Ẩn Danh (Anonymous Bidding)
- Tên hiển thị người đấu giá luôn luôn được mã hóa dạng `m***g` (ví dụ: `duongkhmt` ➔ `d***t`).
- DTO phản hồi tuyệt đối KHÔNG chứa thuộc tính `bidderId`.

---

## 📊 4. BẢNG MÃ LỖI BIDDING ENGINE (ERROR MATRIX)

| Mã Lỗi (ErrorCode) | HTTP Status | Thông điệp nguyên văn (Message) | Nguyên nhân kích hoạt |
| :--- | :---: | :--- | :--- |
| `BUY_NOW_NOT_SUPPORTED` | 400 | "Sản phẩm này không thuộc loại hình Mua Ngay" | Gọi API Buy-Now lên phiên đấu giá `ENGLISH`/`RESERVE` |
| `BUY_NOW_PRICE_NOT_SET` | 500 | "Lỗi dữ liệu: Sản phẩm Mua Ngay nhưng không có giá Buy Now niêm yết" | Dữ liệu DB bị hỏng `buyNowPrice = null` |
| `CANNOT_BID_OWN_PRODUCT` | 400 | "Bạn không thể đặt giá trên sản phẩm do chính mình đăng bán" | Seller tự bấm bid/buy-now bài mình |
| `ALREADY_HIGHEST_BIDDER` | 400 | "Bạn đang là người dẫn đầu giá, không thể tự đặt đè giá mình" | Bidder đang cao nhất tự bấm bid tiếp |
| `BID_AMOUNT_TOO_LOW` | 400 | "Giá đặt nhỏ hơn giá tối thiểu cho phép" | Đặt giá nhỏ hơn `currentPrice + bidStep` |
