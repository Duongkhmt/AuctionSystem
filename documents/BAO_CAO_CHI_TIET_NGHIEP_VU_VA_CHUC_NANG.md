# BÁO CÁO CHI TIẾT NGHIỆP VỤ BÀI TOÁN VÀ TOÀN BỘ CHỨC NĂNG HỆ THỐNG BACKEND

---

## 📌 I. TỔNG QUAN BÀI TOÁN VÀ ĐỐI TƯỢNG SỬ DỤNG

### 1. Tổng quan Dự án & Bài toán Kinh doanh
- **DuAnTrainning (AuctionSystem)** là hệ thống Backend phục vụ cho Nền tảng **Đấu Giá Trực Tuyến (Online Auction Platform)** đa ngành hàng (Nhà đất, Xe hơi, Tranh nghệ thuật, Đồ cổ, Đồ điện tử...).
- Hệ thống giải quyết bài toán giao dịch tài sản minh bạch, gia tăng cạnh tranh giá theo thời gian thực (*Real-time Competitive Bidding*) và quản lý thuộc tính tài sản động (*Dynamic Product Attributes*).
- Nền tảng hỗ trợ quy trình kiểm duyệt bài đăng nghiêm ngặt bởi Quản trị viên (*Admin Moderation*), tích hợp **Động cơ Đấu giá Tự động (Proxy Bidding Engine)**, **Cơ chế Chống bắn tỉa phút chót (Soft-Close Anti-Sniping)**, **Tự động xử lý đơn hàng bùng tiền quá 48h kèm Chế tài phạt gậy vi phạm (Unpaid 3-Strikes Penalty System)**, và **Tự động hóa vòng đời trạng thái bằng Robot ngầm (AuctionScheduler)**.

### 2. Các Đối tượng Tham gia Hệ thống (Actors)
- **Khách vãng lai (Guest):** Xem danh mục sản phẩm, duyệt danh sách sản phẩm công khai, xem chi tiết thuộc tính động, đếm ngược thời gian và xem lịch sử thầu ẩn danh.
- **Người mua (Bidder):** Tham gia đặt giá thủ công, cài đặt mức giá trần tự động đấu giá (*Proxy Bid*), thực hiện Mua Ngay giá cố định (*Buy Now*), quản lý danh sách sản phẩm trúng thầu, thực hiện Checkout địa chỉ/thanh toán đơn hàng và xác nhận đã nhận hàng thành công.
- **Người bán (Seller):** Đăng bài sản phẩm với bộ ảnh mây và thuộc tính động `JSONB`, cấu hình các thông số tài chính/thời gian cho phiên đấu giá, theo dõi kho hàng cá nhân, chỉnh sửa/xóa bài chưa diễn ra, hủy phiên trước giờ G, đăng lại (*Relist*) phiên hết hạn, xem đơn bán được và nhập mã vận đơn xuất hàng.
- **Quản trị viên (Admin):** Rà soát các bài đăng chờ duyệt (`PENDING`), thực hiện Phê duyệt (*Approve*) hoặc Từ chối (*Reject*) kèm ghi nhận lý do chi tiết.
- **Robot ngầm Hệ thống (AuctionScheduler):** Tự động mở/khóa phiên ngầm định kỳ 10 giây/lần, chốt Winner, sinh Đơn hàng `UNPAID` 48h, và dọn dẹp đơn bùng tiền phạt gậy cấm tài khoản 90 ngày.

### 3. Công nghệ & Hạ tầng Kiến trúc (Tech Stack)
- **Ngôn ngữ & Framework:** Java 21 (Eclipse Temurin 21), Spring Boot 4.1.0 (Web, JPA, Security, Validation).
- **Cơ sở dữ liệu:** PostgreSQL 15+ (hỗ trợ kiểu dữ liệu bản địa `JSONB` cho thuộc tính động).
- **Hạ tầng mây (CDN):** Cloudinary HTTP5 SDK 2.0.0 (Quản lý và đồng bộ tải/xóa ảnh mây).
- **Chuyển đổi dữ liệu & Tiện ích:** MapStruct 1.6.2 (Mapper POJO/DTO), Lombok 1.18.34.
- **Đa ngôn ngữ (i18n):** Spring MVC `MessageSource` + `AcceptHeaderLocaleResolver` (Hỗ trợ `vi` / `en`).
- **Container & Build:** Docker Multi-stage build (Alpine Linux + JRE 21), Maven Wrapper (`mvnw`).
- **Kiểm thử & Độ phủ:** JUnit 5, Mockito, AssertJ, JaCoCo Maven Plugin (Báo cáo HTML).

---

## ⚙️ II. CÁC HỆ THỐNG QUY TẮC NGHIỆP VỤ VÀ CƠ CHẾ TỰ ĐỘNG CỐT LÕI

### 1. Ma trận Vòng đời Trạng thái (State Machine Matrix)

#### Bảng ma trận ánh xạ giữa Sản phẩm và Phiên đấu giá:
| Trạng thái Sản phẩm (`ProductStatus`) | Trạng thái Phiên tương ứng (`AuctionStatus`) | Ý nghĩa nghiệp vụ |
| :--- | :--- | :--- |
| **`PENDING`** (Chờ duyệt) | **`PENDING_APPROVAL`** (Chờ duyệt) | Bài mới đăng, chưa hiển thị công khai |
| **`APPROVED`** (Đã duyệt) | **`SCHEDULED`** (Đã lên lịch) | Đã duyệt, chờ đến mốc `startTime` để mở |
| **`APPROVED`** (Đã duyệt) | **`RUNNING`** (Đang diễn ra) | Đã duyệt, đang trong thời gian cho phép đặt giá |
| **`APPROVED`** (Đã duyệt) | **`ENDED`** (Đã kết thúc) | Đấu giá xong thành công có Winner hoặc chốt Buy Now |
| **`APPROVED`** (Đã duyệt) | **`EXPIRED`** (Đã hết hạn) | Hết giờ đấu giá nhưng không có ai đặt giá |
| **`REJECTED`** (Bị từ chối) | **`CANCELLED`** (Đã hủy) | Admin từ chối bài đăng, phiên bị hủy |

#### Bảng ma trận chuyển đổi trạng thái Đơn hàng (`OrderStatus`):
| Trạng thái hiện tại | Trạng thái tiếp theo | Tác nhân thực hiện | Điều kiện kích hoạt & Ý nghĩa |
| :--- | :--- | :--- | :--- |
| *(Chưa có)* | **`UNPAID`** | Robot / BuyNow | Hết giờ có Winner hoặc Mua Ngay thành công, gán `paymentDeadline = 48h` |
| **`UNPAID`** | **`PAID`** | Người mua (Buyer) | Nhập đủ địa chỉ, SĐT hợp lệ và chọn `PaymentMethod` |
| **`UNPAID`** | **`CANCELLED`** | Robot Scheduler | Đơn `UNPAID` quá 48h (`paymentDeadline <= now`), phạt +1 Gậy Vi Phạm |
| **`PAID`** | **`SHIPPING`** | Người bán (Seller) | Nhập đủ `courierName` và `trackingNumber` bắt buộc |
| **`SHIPPING`** | **`COMPLETED`** | Người mua (Buyer) | Kiểm tra hàng thành công và bấm nút xác nhận nhận hàng |

---

### 2. Robot Quét Trạng Thái Ngầm (`AuctionScheduler`)
- **Tần suất chạy:** `@Scheduled(fixedRate = 10000)` — Khởi chạy định kỳ mỗi **10 giây/lần**.
- **Các nhiệm vụ chính:**
  1. `autoStartAuctions`: Chuyển các phiên từ `SCHEDULED` sang `RUNNING` khi `startTime <= now`.
  2. `autoExpireBuyNowAuctions`: Tự động hết hạn bài Mua Ngay quá 30 ngày.
  3. `processEndedAuctions`: Chốt Winner cho các phiên `RUNNING` có `endTime <= now`, chuyển sang `ENDED` (hoặc `EXPIRED`), tự động sinh bản ghi `Order` ở trạng thái `UNPAID` kèm `paymentDeadline = now + 48h`.
  4. `backfillMissingOrders`: Tự động bổ sung bản ghi Đơn hàng bị khuyết cho các phiên có Winner.
  5. `processExpiredUnpaidOrders`: Quét đơn `UNPAID` có `paymentDeadline <= now`, đổi sang `CANCELLED`, phạt `unpaidStrikeCount + 1`, và gán `bannedUntil = now + 90 days` khi đủ 3 gậy.

---

### 3. Động Cơ Đấu Giá Tự Động (Proxy Bidding Engine)
- Bidder cài đặt mức giá trần tối đa chấp nhận trả (`maxAutoBidAmount`).
- Hệ thống tự động đại diện nâng giá cho người đang cài trần cao hơn.
- Mức giá hiện tại mới của phiên:
  $$\text{currentPrice} = \min(\text{Max}(\text{Đối thủ}) + \text{bidStep}, \text{Max}(\text{Bản thân}))$$
- **Nguyên tắc ưu tiên:** Nếu hai người cài giá trần bằng nhau, người cài đặt trước sẽ giữ vị trí dẫn đầu (*First-Come, First-Served*).
- **100% Audit Trail:** Mọi lượt nâng giá tự động đều được lưu thành 1 bản ghi `Bid` (cờ `autoBid = true`) trong DB để phục vụ kiểm toán minh bạch.

---

### 4. Thang Bước Giá Động Theo Bậc (Dynamic Bid Step Calculator)
Tự động tăng/giảm bước giá tối thiểu (`bidStep`) tương thích với quy mô giá trị hiện tại của tài sản:

| Khoảng giá hiện tại (`currentPrice`) | Bước giá tối thiểu (`bidStep`) | Lý do nghiệp vụ |
| :--- | :--- | :--- |
| **Dưới 1,000,000đ** | **10,000đ** | Phù hợp với sản phẩm giá trị nhỏ, tăng giá nhẹ nhàng |
| **Từ 1,000,000đ đến 10,000,000đ** | **100,000đ** | Phù hợp với hàng điện tử, gia dụng tầm trung |
| **Trên 10,000,000đ** | **500,000đ** | Tối ưu thời gian đấu giá cho tài sản giá trị cao (xe cộ, nhà đất) |

---

### 5. Cơ Chế Chống Bắn Tỉa Phút Chót (Soft-Close Anti-Sniping Window)
- **Khung thời gian kích hoạt:** 3 phút cuối cùng trước khi hết giờ (`endTime - 3 phút <= now`).
- **Hành động:** NẾU có 1 lượt đặt giá hợp lệ gửi vào ➔ Hệ thống tự động cộng thêm **+3 phút** vào `endTime` của phiên.
- **Mục tiêu:** Triệt phá các công cụ bot tự động bắn tỉa ở millisecond cuối, tạo sự bình đẳng cho người mua thực.

---

### 6. Anti-Shill Bidding & Anti-Self-Outbid
- **Anti-Shill Bidding:** Cấm tuyệt đối Người bán (Seller) tự đặt giá cho sản phẩm của chính mình.
- **Anti-Self-Outbid:** Cấm tuyệt đối Người đang dẫn đầu (Highest Bidder) tự đặt giá đè lên chính mình.

---

### 7. Bảo Mật Mã Hóa Ẩn Danh Người Đặt Giá (Bidder Identity Masking)
Bảo vệ thông tin cá nhân trên bảng lịch sử thầu công khai:
- `duong` ➔ `d***g` (Giữ ký tự đầu và cuối).
- `admin_seller` ➔ `a***r`.
- `ab` (tên $\le 2$ ký tự) ➔ `a***`.
- `null` / rỗng ➔ `u***r`.

---

### 8. Đồng Bộ Giao Dịch Hạ Tầng Mây (Cloudinary CDN Transaction Synchronization)
Sử dụng `TransactionSynchronizationManager` để đảm bảo đồng bộ 100% giữa CSDL và bộ nhớ Cloudinary:
- **Khi Upload ảnh mới:** Đăng ký Rollback Hook (`afterCompletion`). Nếu DB lưu lỗi, lập tức phát lệnh xóa ảnh mới nâng lên Cloudinary để tránh rác mây.
- **Khi Xóa ảnh cũ:** Đăng ký Post-Commit Hook (`afterCommit`). Chỉ phát lệnh xóa ảnh trên mây SAU KHI DB đã Commit thành công, tránh lỗi đứt gãy link ảnh (*Broken Link*).

---

### 9. Cơ Chế Xử Lý Đa Ngôn Ngữ (i18n Localization)
```text
[ Client Request (Header Accept-Language: en) ]
                     │
                     ▼
[ Spring AcceptHeaderLocaleResolver ] ──► [ MessageSource (messages_en.properties) ]
                                                    │
                                                    ▼
                                     [ GlobalExceptionHandler ] ──► [ ErrorResponse JSON ]
```
Phân tách văn bản lỗi khỏi mã nguồn Java. Đọc key `ErrorCode` và dịch tự động sang `vi` hoặc `en` tùy theo Header `Accept-Language`.

---

## 🛍️ III. CHI TIẾT CỤ THỂ TOÀN BỘ CHỨC NĂNG NGHIỆP VỤ

### 📱 PHÂN HỆ 1: KHÁCH VẮNG LAI & NGƯỜI MUA (GUEST & BIDDER)

#### 1.1. Xem danh mục sản phẩm (`Category Listing`)
- **API:** `GET /v1/categories`
- **Đối tượng:** Tất cả người dùng.
- **Chi tiết:** Lấy cây danh mục đang hoạt động, cờ `requiresVerification` và `requiresDeposit`.

#### 1.2. Xem danh sách sản phẩm đấu giá công khai (`Public Marketplace`)
- **API:** `GET /v1/products`
- **Đối tượng:** Tất cả người dùng.
- **Chi tiết:** Lọc sản phẩm `APPROVED`, xếp bài mới nhất lên đầu, áp dụng Batch Loading In-Memory Map loại bỏ hoàn toàn lỗi N+1 Query.

#### 1.3. Xem chi tiết sản phẩm & thông số đấu giá (`Product Detail`)
- **API:** `GET /v1/products/{id}`
- **Đối tượng:** Tất cả người dùng.
- **Chi tiết:** Bộ ảnh chuẩn theo thứ tự `displayOrder`, thuộc tính kỹ thuật động `JSONB` (`attributes`), giá khởi điểm, giá hiện tại, bước giá động, đếm ngược thời gian.

#### 1.4. Đặt giá thủ công & Đặt giá tự động (`Place Bid & Proxy Bidding`)
- **API:** `POST /v1/auctions/{auctionId}/bids`
- **Đối tượng:** Người mua (Bidder) đã đăng nhập.
- **Chi tiết:** Validate phiên `RUNNING`, check anti-shill, anti-self-outbid, min bid amount, thuật toán Proxy Bidding, anti-sniping extension (+3 phút), lazy unban check.

#### 1.5. Mua ngay sản phẩm với giá cố định (`Buy Now`)
- **API:** `POST /v1/auctions/{auctionId}/buy-now`
- **Đối tượng:** Người mua (Bidder) đã đăng nhập.
- **Chi tiết:** Check `buyNowPrice`, đóng phiên ngay `ENDED`, gán Winner, tự động sinh Đơn hàng `UNPAID` deadline 48h.

#### 1.6. Xem lịch sử đặt giá công khai (`Public Bid History`)
- **API:** `GET /v1/auctions/{auctionId}/bids`
- **Đối tượng:** Tất cả người dùng.
- **Chi tiết:** Lịch sử thầu sắp xếp mới nhất, mã hóa ẩn danh tên bidder (`d***g`), cờ phân biệt bid thủ công vs Auto-bid.

#### 1.7. Xem sản phẩm trúng thầu & Checkout (`Won Auctions & Checkout`)
- **APIs:** 
  - `GET /v1/bidders/{bidderId}/won-auctions`
  - `POST /v1/bidders/{bidderId}/orders/{orderId}/checkout`
- **Đối tượng:** Người mua (Bidder).
- **Chi tiết:** Danh sách đơn trúng thầu, thực hiện Checkout đơn `UNPAID` (nhập địa chỉ, SĐT, phương thức thanh toán), sinh bản ghi `Payment`, đổi đơn sang `PAID`.

#### 1.8. Xác nhận đã nhận được hàng (`Confirm Received`)
- **API:** `PUT /v1/bidders/{bidderId}/orders/{orderId}/confirm-received`
- **Đối tượng:** Người mua (Bidder).
- **Chi tiết:** Chuyển đơn `SHIPPING` ➔ `COMPLETED`, giải ngân hoàn tất cho Seller.

---

### 🏪 PHÂN HỆ 2: NGƯỜI BÁN (SELLER PORTAL)

#### 2.1. Đăng sản phẩm mới & Cấu hình phiên (`Create Product & Auction`)
- **API:** `POST /v1/sellers/{sellerId}/products`
- **Đối tượng:** Người bán (Seller).
- **Chi tiết:** Nhập tiêu đề, mô tả, danh mục active, thuộc tính động `JSONB`, upload bộ ảnh 1-20 ảnh (<=5MB), chọn loại hình đấu giá (`ENGLISH`, `RESERVE`, `BUY_NOW`), cài giá khởi điểm, giá sàn ẩn, giá mua ngay, `startTime`, `endTime` (>= 30 phút). Sản phẩm tạo ở trạng thái `PENDING`.

#### 2.2. Xem danh sách sản phẩm cá nhân (`Seller Products Listing`)
- **API:** `GET /v1/sellers/{sellerId}/products`
- **Đối tượng:** Người bán (Seller).
- **Chi tiết:** Quản lý bài đăng cá nhân, theo dõi trạng thái kiểm duyệt và lý do từ chối nếu có.

#### 2.3. Chỉnh sửa thông tin bài đăng & Bộ ảnh (`Update Product & Images`)
- **API:** `PUT /v1/sellers/{sellerId}/products/{id}`
- **Đối tượng:** Người bán (Seller).
- **Chi tiết:** Cho phép sửa khi `PENDING_APPROVAL` hoặc `SCHEDULED` (tuyệt đối CHẶN khi đã `RUNNING`/`ENDED`). Thêm/xóa ảnh, tự động re-index `displayOrder` [0..N], post-commit CDN deletion hook.

#### 2.4. Xóa sản phẩm & Gỡ bài đăng (`Delete Product`)
- **API:** `DELETE /v1/sellers/{sellerId}/products/{id}`
- **Đối tượng:** Người bán (Seller).
- **Chi tiết:** Cho phép xóa bài chưa `RUNNING`/`ENDED`. Xóa CSDL và dọn ảnh mây sau commit.

#### 2.5. Chủ động hủy phiên trước giờ G (`Cancel Auction`)
- **API:** `PUT /v1/sellers/{sellerId}/products/{id}/cancel`
- **Đối tượng:** Người bán (Seller).
- **Chi tiết:** Seller chủ động báo hủy phiên chưa diễn ra, đổi phiên sang `CANCELLED`.

#### 2.6. Đăng lại phiên hết hạn (`Relist Expired Auction`)
- **API:** `POST /v1/sellers/{sellerId}/auctions/{auctionId}/relist`
- **Đối tượng:** Người bán (Seller).
- **Chi tiết:** Tái sử dụng thông tin phiên `EXPIRED`, reset chu kỳ thời gian mới, đổi phiên sang `RUNNING`.

#### 2.7. Xem đơn hàng bán được & Nhập mã vận đơn (`Seller Orders & Ship Order`)
- **APIs:** 
  - `GET /v1/sellers/{sellerId}/orders`
  - `PUT /v1/sellers/{sellerId}/orders/{orderId}/ship`
- **Đối tượng:** Người bán (Seller).
- **Chi tiết:** Lọc đơn `PAID`, nhập tên đơn vị vận chuyển (`courierName`) và mã vận đơn (`trackingNumber`), đổi đơn sang `SHIPPING`.

---

### 🛡️ PHÂN HỆ 3: QUẢN TRỊ VIÊN (ADMIN MODERATION)

#### 3.1. Xem danh sách bài chờ duyệt (`Pending Products List`)
- **API:** `GET /v1/admin/products/pending`
- **Đối tượng:** Quản trị viên (Admin).
- **Chi tiết:** Rà soát bài đăng `PENDING`, ưu tiên xếp bài nạp sớm nhất lên đầu.

#### 3.2. Phê duyệt xuất bản bài đăng (`Approve Product`)
- **API:** `PUT /v1/admin/products/{id}/approve`
- **Đối tượng:** Quản trị viên (Admin).
- **Chi tiết:** Đổi sản phẩm sang `APPROVED`. Chặn duyệt nếu `endTime` đã trôi qua. Nếu `startTime <= now` ➔ chuyển phiên sang `RUNNING`. Nếu `startTime > now` ➔ chuyển phiên sang `SCHEDULED`.

#### 3.3. Từ chối xuất bản bài đăng (`Reject Product`)
- **API:** `PUT /v1/admin/products/{id}/reject`
- **Đối tượng:** Quản trị viên (Admin).
- **Chi tiết:** Đổi sản phẩm sang `REJECTED` kèm lý do từ chối bắt buộc, đổi phiên sang `CANCELLED`.

---

### 🤖 PHÂN HỆ 4: ROBOT SCHEDULER NGẦM & HẬU ĐẤU GIÁ

#### 4.1. Tự động chốt phiên & Sinh đơn hàng hậu đấu giá
- Ngầm 10s/lần hoặc khi Mua Ngay thành công, chốt Winner, đổi phiên sang `ENDED`, tự động khởi tạo Đơn hàng (`Order`) ở trạng thái `UNPAID` kèm deadline 48 tiếng (`paymentDeadline = now + 48h`).

#### 4.2. Tự động hủy đơn bùng tiền 48h & Phạt 3-Strikes
- Robot quét đơn `UNPAID` quá 48h ➔ Hủy đơn sang `CANCELLED`, phạt `unpaidStrikeCount + 1`. NẾU bùng đủ 3 lần ➔ Cấm 90 ngày (`bannedUntil = now + 90 days`).

#### 4.3. Cơ chế Mở khóa Lười (`Lazy Unban Check`)
- Tại `BidValidator`, khi qua 90 ngày phạt (`bannedUntil <= now`), ở lần bid tiếp theo, hệ thống tự động xóa án cấm và reset gậy về 0.

---

## 🗄️ IV. THIẾT KẾ CƠ SỞ DỮ LIỆU & MÃ NGUỒN

### 1. Bảng 8 Entity PostgreSQL
1. `User` (`users`): `id`, `username`, `email`, `passwordHash`, `unpaidStrikeCount`, `bannedUntil`, `role` (`UserRole`), `status` (`UserStatus`), `createdAt`.
2. `Category` (`categories`): `id`, `name`, `description`, `parentId`, `requiresVerification`, `requiresDeposit`.
3. `Product` (`products`): `id`, `title`, `description`, `attributes` (`JSONB`), `status` (`ProductStatus`), `rejectionReason`, `seller_id`, `category_id`.
4. `ProductImage` (`product_images`): `id`, `imageUrl`, `publicId`, `displayOrder`, `product_id`.
5. `Auction` (`auctions`): `id`, `auctionType`, `startingPrice`, `reservePrice`, `currentPrice`, `buyNowPrice`, `bidStep`, `startTime`, `endTime`, `status` (`AuctionStatus`), `product_id`, `winner_id`.
6. `Bid` (`bids`): `id`, `bidAmount`, `maxAutoBid`, `autoBid`, `createdAt`, `auction_id`, `bidder_id`. Indexed: `(auction_id, bid_amount DESC, created_at ASC)` & `(auction_id, created_at DESC)`.
7. `Order` (`orders`): `id`, `winningPrice`, `shippingAddress`, `phoneNumber`, `courierName`, `trackingNumber`, `paymentDeadline`, `status` (`OrderStatus`), `auction_id` (Unique), `product_id`, `buyer_id`, `seller_id`.
8. `Payment` (`payments`): `id`, `amount`, `paymentMethod` (`PaymentMethod`), `transactionCode` (Unique), `status` (`PaymentStatus`), `order_id`.

### 2. Cấu trúc Mã nguồn (Codebase Map)
```
src/main/java/com/duong/auction/system
├── aspect          # RateLimitAspect (@RateLimit chống spam API)
├── config          # SecurityConfig, CloudinaryConfig, RedisConfig, WebConfig
├── controller      # AdminProduct, AuctionBidding, Bidder, Category, Product, SellerProduct
├── dto             # request / response DTOs
├── entity          # 8 JPA Entities (User, Category, Product, ProductImage, Auction, Bid, Order, Payment)
├── enums           # AuctionStatus, ProductStatus, OrderStatus, PaymentMethod, PaymentStatus, UserRole, UserStatus
├── exception       # ApplicationException, ErrorCode, GlobalExceptionHandler
├── mapper          # MapStruct Mappers
├── repository      # Spring Data JPA Repositories
├── service         # ProductService, BiddingService, OrderService, CategoryService, CloudinaryService, AuctionScheduler
│   └── helper      # ProxyBiddingEngineHelper, BidStepCalculatorHelper, OrderResponseHelper, ProductResponseHelper...
└── validator       # BidValidator, OrderValidator, AuctionValidator, ProductImageValidator
```

---

> [!NOTE]
> Báo cáo Markdown này bao phủ **100% toàn bộ cấu trúc kiến trúc, nghiệp vụ bài toán, API Endpoints, State Machine và thiết kế CSDL** của dự án Backend `DuAnTrainning.AuctionSystem`.
