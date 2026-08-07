# 1. Project Overview

**DuAnTrainning (AuctionSystem)** là hệ thống Backend phục vụ cho nền tảng **Đấu Giá Trực Tuyến (Online Auction Platform)** đa ngành hàng.

Hệ thống giải quyết bài toán đấu giá hàng hóa minh bạch, cạnh tranh theo thời gian thực và quản lý tài sản động. 
Nền tảng hỗ trợ người bán (Seller) đăng tải sản phẩm với thuộc tính đa dạng (Nhà đất, Xe hơi, Tranh ảnh, Đồ điện tử...), 
hỗ trợ quy trình kiểm duyệt bài đăng bởi Quản trị viên (Admin), tích hợp công cụ tự động đấu giá (Proxy Bidding Engine), 
cơ chế chống bắn tỉa phút chót (Soft-Close Anti-Sniping), và tự động hóa chuyển đổi trạng thái phiên đấu giá ngầm bằng Robot Scheduler.

### Đối tượng sử dụng:
**Bidder (Người tham gia đấu giá):** Xem thông tin sản phẩm, tham gia đặt giá cạnh tranh, cài đặt mức giá trần tự động đấu giá (Proxy Bid), 
xem lịch sử thầu ẩn danh, mua ngay sản phẩm với giá cố định (Buy Now), quản lý danh sách sản phẩm trúng thầu, 
chốt địa chỉ thanh toán Checkout và xác nhận đã nhận được hàng.

**Seller (Người bán):** Tạo mới bài đăng sản phẩm kèm ảnh mây, chỉnh sửa nội dung/danh sách ảnh, 
hủy phiên đấu giá trước giờ G, đăng lại phiên đã hết hạn (Relist), xem danh sách đơn hàng đã bán và nhập mã vận đơn xuất hàng cho người mua.

**Admin (Quản trị viên):** Xem danh sách các bài đăng sản phẩm chờ duyệt, thực hiện chấp thuận (Approve) 
hoặc từ chối bài đăng (Reject) kèm theo lý do cụ thể.

---

# 2. Tech Stack

- **Java:** 21
- **Spring Boot:** 4.1.0 (starter parent `org.springframework.boot:4.1.0`)
- **Spring Security:** `org.springframework.boot:spring-boot-starter-security`
- **Spring Data JPA:** `org.springframework.boot:spring-boot-starter-data-jpa`
- **Validation:** `org.springframework.boot:spring-boot-starter-validation`
- **Database:** PostgreSQL (`org.postgresql:postgresql`, phiên bản driver theo Spring Boot BOM)
- **JWT:** Không tìm thấy trong source code
- **Docker:** Không tìm thấy trong source code
- **MapStruct:** 1.6.2 (`org.mapstruct:mapstruct` và `org.mapstruct:mapstruct-processor`)
- **Lombok:** 1.18.34 (`org.projectlombok:lombok`)
- **Testing:** JUnit 5 (`spring-boot-starter-test`), Mockito (`org.mockito:mockito-junit-jupiter`), AssertJ (`org.assertj:assertj-core`), JaCoCo Maven Plugin 0.8.12 (`org.jacoco:jacoco-maven-plugin`)
- **Các thư viện khác:**
- **Cloudinary HTTP5:** 2.0.0 (`com.cloudinary:cloudinary-http5`) — Quản lý và lưu trữ hình ảnh trên mây.
- **Java Dotenv:** 3.0.0 (`io.github.cdimascio:dotenv-java`) — Đọc biến môi trường từ tập tin `.env`.

---

# 3. System Requirements

- **JDK:** 21 trở lên
- **Build Tool:** Apache Maven 3.8+ (hoặc sử dụng script `mvnw` đi kèm dự án)
- **Database:** PostgreSQL 15+ (bắt buộc hỗ trợ kiểu dữ liệu `JSONB`)
- **Docker:** Không tìm thấy trong source code
- **Biến môi trường (Environment Variables):**
  - `spring.datasource.url` (Mặc định: `jdbc:postgresql://localhost:5432/auction_system`)
  - `spring.datasource.username` (Mặc định: `postgres`)
  - `spring.datasource.password`
  - `CLOUDINARY_CLOUD_NAME`
  - `CLOUDINARY_API_KEY`
  - `CLOUDINARY_API_SECRET`

---

# 4. Installation

### Bước 1: Clone dự án
```bash
git clone <https://github.com/Duongkhmt/AuctionSystem.git>
cd Backend/DuAnTrainning
```

### Bước 2: Cấu hình Cơ sở dữ liệu & Biến môi trường
Tạo cơ sở dữ liệu PostgreSQL có tên `auction_system` trên máy địa phương hoặc máy chủ.  
Tạo tập tin `.env` tại thư mục gốc của dự án (hoặc cập nhật trực tiếp trong `src/main/resources/application.properties`):

```properties
CLOUDINARY_CLOUD_NAME=your_cloud_name
CLOUDINARY_API_KEY=your_api_key
CLOUDINARY_API_SECRET=your_api_secret
```

### Bước 3: Biên dịch dự án (Build)
```bash
./mvnw clean package -DskipTests
```

### Bước 4: Khởi chạy ứng dụng (Run)
```bash
./mvnw spring-boot:run
```
hoặc chạy tập tin `.jar` sau khi build:
```bash
java -jar target/DuAnTrainning-0.0.1-SNAPSHOT.jar
```
# 5. Project Structure

Mã nguồn dự án được tổ chức theo cấu trúc sau:

```
src/main/java/DuAnTrainning/AuctionSystem
├── config          # Cấu hình Spring Security và Cloudinary API Bean
├── controller      # REST API Endpoints (Admin, Seller, Bidder, Public, AuctionBidding, Category)
├── dto
│   ├── request     # DTO đầu vào (ProductRequestDTO, CheckoutRequestDTO, ShipOrderRequestDTO...)
│   └── response    # DTO đầu ra (ProductResponseDTO, WonAuctionResponseDTO, SellerOrderResponseDTO...)
├── entity          # JPA Entities (Product, Auction, Bid, Order, Payment, Category, User)
├── enums           # Constants (AuctionStatus, ProductStatus, OrderStatus, PaymentMethod, PaymentStatus)
├── exception       # Xử lý ngoại lệ tập trung (ApplicationException, ErrorCode, GlobalExceptionHandler)
├── mapper          # MapStruct Interfaces (ProductMapper, AuctionMapper, BidMapper, OrderMapper)
├── repository      # Spring Data JPA Repositories (Product, Auction, Bid, Order, Payment)
├── service         # Tầng nghiệp vụ chính (ProductService, BiddingService, OrderService, AuctionScheduler)
│   └── helper      # Helpers (OrderResponseHelper, ProductResponseHelper, BidResponseHelper...)
└── validator       # Validators (OrderValidator, BidValidator, AuctionValidator, ProductImageValidator)
```

---

# 6. Business Overview

Hệ thống đấu giá hoạt động theo quy trình nghiệp vụ khép kín từ đăng bán, kiểm duyệt đến diễn ra phiên và kết thúc:

### 1. Xem danh mục & Đăng bán sản phẩm
- Người bán gửi thông tin sản phẩm và phiên đấu giá qua API. Sản phẩm hỗ trợ thuộc tính tĩnh (tiêu đề, mô tả) và 
thuộc tính động dạng `JSONB` (cho phép cấu hình linh hoạt thông số theo từng chủng loại như Số km xe đi, Diện tích nhà đất, Tác giả bức tranh...).

- Bài đăng bắt buộc đính kèm từ 1 đến 20 ảnh (định dạng JPG/PNG/WebP, dung lượng <= 5MB). Ảnh được tải lên mây Cloudinary CDN.

- Người bán chọn hình thức đấu giá: `ENGLISH` (Đấu giá tăng dần), `RESERVE` (Đấu giá có giá bảo lưu/giá sàn ẩn), hoặc `BUY_NOW` (Cho phép mua ngay).
- Bài đăng sau khi tạo thành công có trạng thái `PENDING` (chờ duyệt).

### 2. Kiểm duyệt bài đăng (Admin Moderation)
- Admin kiểm tra danh sách các bài đăng ở trạng thái `PENDING`.
- **Nếu từ chối (Reject):** Bài đăng chuyển sang `REJECTED`, ghi nhận lý do từ chối, và phiên đấu giá chuyển sang `CANCELLED`.
- **Nếu phê duyệt (Approve):** Bài đăng chuyển sang `APPROVED`. 
  - Nếu thời điểm bắt đầu `startTime` nằm trong tương lai, phiên chuyển sang `SCHEDULED`.
  - Nếu thời điểm bắt đầu `startTime` đã qua hoặc bằng hiện tại, phiên lập tức kích hoạt sang `RUNNING`.

### 3. Diễn ra Đấu giá & Tự động Đấu giá (Proxy Bidding)
- Khi phiên ở trạng thái `RUNNING`, các Bidder có thể tham gia đặt giá.
  - **Quy tắc kiểm tra (Rules):** Người bán không được tự đặt giá sản phẩm của mình (Anti-Shill Bidding). 
      Người đang dẫn đầu không được tự đặt giá đè lên chính mình (Anti-Self-Outbid). 
      Mức giá đặt mới phải lớn hơn hoặc bằng `Giá hiện tại + Bước giá tối thiểu` 
      (bước giá tính tự động theo bậc: 10.000đ cho giá < 1M; 100.000đ cho giá 1M - 10M; 500.000đ cho giá > 10M).
  
- **Proxy Bidding Engine:** Bidder có thể nhập giá trần `maxAutoBidAmount`. Hệ thống tự động cạnh tranh và nâng giá hiện tại từng nấc 
để giữ vị trí dẫn đầu cho Bidder mà không vượt quá mức trần đã cài. Mọi lượt nhảy giá tự động đều sinh ra bản ghi `Bid` để đảm bảo 100% Audit Trail.

- **Soft-Close Anti-Sniping:** Nếu có lượt đặt giá hợp lệ xuất hiện trong 3 phút cuối cùng trước khi hết giờ, thời gian kết thúc phiên (`endTime`) 
tự động gia hạn thêm **+3 phút**.
- 
**Mua Ngay (Buy Now):** Đối với phiên có thiết lập giá mua ngay `buyNowPrice`, Bidder chấp nhận mức giá này có thể kích hoạt mua ngay. 
Hệ thống sẽ lập tức chốt phiên (`ENDED`), ghi nhận chiến thắng cho Bidder và cập nhật giá hiện tại bằng giá mua ngay.

### 5. Quản lý Đơn hàng & Thanh toán Hậu Đấu Giá (Post-Auction Order Settlement)
- **Tự động sinh đơn hàng:** Ngay khi phiên đấu giá hết giờ hoặc người mua thực hiện Mua Ngay, hệ thống (`AuctionScheduler` / `BiddingService.executeBuyNow`) tự động chốt người chiến thắng (`winner`) và tạo bản ghi Đơn hàng (`Order`) ở trạng thái **`UNPAID`**.
- **Người mua Checkout:** Người mua vào danh sách đơn trúng thầu chọn đơn `UNPAID`, nhập địa chỉ nhận hàng, số điện thoại và chọn phương thức thanh toán. Hệ thống chuyển đơn sang **`PAID`** và sinh bản ghi Lịch sử thanh toán (`Payment`).
- **Người bán Xuất hàng:** Người bán kiểm tra danh sách đơn bán được, nhập thông tin đơn vị vận chuyển (`courierName`) và mã vận đơn (`trackingNumber`) để xuất hàng. Hệ thống chuyển đơn sang **`SHIPPING`**.
- **Người mua Nhận hàng:** Người mua nhận hàng đúng mô tả và bấm xác nhận. Hệ thống chuyển đơn sang **`COMPLETED`** và giải ngân hoàn tất giao dịch.

### 6. Vòng đời Trạng thái (State Machines)
- **ProductStatus:** `PENDING` ➔ `APPROVED` / `REJECTED`
- **AuctionStatus:** `PENDING_APPROVAL` ➔ `SCHEDULED` / `RUNNING` ➔ `ENDED` / `EXPIRED` / `CANCELLED`
- **OrderStatus:** `UNPAID` ➔ `PAID` ➔ `SHIPPING` ➔ `COMPLETED`

---


# 7. REST API

Danh sách toàn bộ các Endpoint được phân nhóm theo đối tượng sử dụng:

### 1. Public Marketplace & Categories
| Method     | Path                                                 | Mô tả                                                    |
|:-----------|:-----------------------------------------------------|:---------------------------------------------------------|
| **GET**    | `/v1/categories`                                     | Lấy danh sách danh mục sản phẩm đang hoạt động           |
| **GET**    | `/v1/products`                                       | Lấy danh sách sản phẩm công khai đã duyệt (`APPROVED`)   |
| **GET**    | `/v1/products/{id}`                                  | Xem chi tiết thông tin sản phẩm và phiên đấu giá         |

### 2. Seller Portal (Cổng cá nhân Người Bán)
| Method     | Path                                                 | Mô tả                                                    |
|:-----------|:-----------------------------------------------------|:---------------------------------------------------------|
| **GET**    | `/v1/sellers/{sellerId}/products`                    | Lấy danh sách sản phẩm của một người bán                 |
| **POST**   | `/v1/sellers/{sellerId}/products`                    | Tạo bài đăng sản phẩm và phiên đấu giá mới               |
| **PUT**    | `/v1/sellers/{sellerId}/products/{id}`               | Cập nhật thông tin bài đăng và danh sách ảnh sản phẩm    |
| **DELETE** | `/v1/sellers/{sellerId}/products/{id}`               | Xóa bài đăng sản phẩm                                    |
| **PUT**    | `/v1/sellers/{sellerId}/products/{id}/cancel`        | Người bán chủ động hủy phiên đấu giá                     |
| **POST**   | `/v1/sellers/{sellerId}/auctions/{auctionId}/relist` | Người bán đăng lại phiên đấu giá đã hết hạn (`EXPIRED`)  |
| **GET**    | `/v1/sellers/{sellerId}/orders`                      | Người bán xem danh sách đơn hàng đã bán (Lọc status)     |
| **PUT**    | `/v1/sellers/{sellerId}/orders/{orderId}/ship`       | Người bán nhập thông tin đơn vị vận chuyển & xuất hàng   |

### 3. Bidder & Bidding Portal (Cổng Đấu Giá & Người Mua)
| Method     | Path                                                       | Mô tả                                                    |
|:-----------|:-----------------------------------------------------------|:---------------------------------------------------------|
| **POST**   | `/v1/auctions/{auctionId}/bids`                            | Thực hiện đặt giá (Bid) mới                              |
| **GET**    | `/v1/auctions/{auctionId}/bids`                            | Xem lịch sử đặt giá công khai (đã ẩn danh tên người đặt) |
| **POST**   | `/v1/auctions/{auctionId}/buy-now`                         | Thực hiện mua ngay sản phẩm với giá cố định              |
| **GET**    | `/v1/bidders/{bidderId}/won-auctions`                      | Người mua truy vấn danh sách sản phẩm đấu giá trúng thầu |
| **POST**   | `/v1/bidders/{bidderId}/orders/{orderId}/checkout`         | Người mua chốt địa chỉ giao hàng & thanh toán đơn hàng   |
| **PUT**    | `/v1/bidders/{bidderId}/orders/{orderId}/confirm-received` | Người mua xác nhận đã nhận hàng thành công         |

### 4. Admin Moderation (Cổng Quản Trị Viên)
| Method     | Path                                                 | Mô tả                                                    |
|:-----------|:-----------------------------------------------------|:---------------------------------------------------------|
| **GET**    | `/v1/admin/products/pending`                         | Lấy danh sách bài đăng chờ Admin kiểm duyệt (`PENDING`)  |
| **PUT**    | `/v1/admin/products/{id}/approve`                    | Admin chấp thuận phê duyệt bài đăng sản phẩm             |
| **PUT**    | `/v1/admin/products/{id}/reject`                     | Admin từ chối phê duyệt bài đăng sản phẩm kèm lý do      |

---



# 8. Database Overview

Cấu trúc các bảng dữ liệu trong PostgreSQL và mối quan hệ giữa các Entity:

### 1. `users` (Quản lý tài khoản)
- **Quan hệ:**
  - One-to-Many với `products` (vai trò `seller`)
  - One-to-Many với `bids` (vai trò `bidder`)
  - One-to-Many với `auctions` (vai trò `winner`)

### 2. `categories` (Danh mục sản phẩm)
- **Quan hệ:**
  - Phân cấp danh mục tự tham chiếu (`parent_id`)
  - One-to-Many với `products`

### 3. `products` (Thông tin sản phẩm)
- **Quan hệ:**
  - Many-to-One với `users` (`seller_id`)
  - Many-to-One với `categories` (`category_id`)
  - One-to-Many với `product_images`
  - Structural Join với `auctions`
- **Ghi chú:** Cột `attributes` sử dụng kiểu dữ liệu `JSONB` của PostgreSQL để lưu trữ thuộc tính động.

### 4. `product_images` (Hình ảnh sản phẩm)
- **Quan hệ:**
  - Many-to-One với `products` (`product_id`)

### 5. `auctions` (Phiên đấu giá)
- **Quan hệ:**
  - Many-to-One với `products` (`product_id`)
  - Many-to-One với `users` (`winner_id`, FetchType `LAZY`)
  - One-to-Many với `bids`

### 6. `bids` (Lượt đặt giá)
- **Quan hệ:**
  - Many-to-One với `auctions` (`auction_id`, FetchType `LAZY`)
  - Many-to-One với `users` (`bidder_id`, FetchType `LAZY`)
- **Index:**
  - `idx_bid_auction_amount`: Composite Index trên `(auction_id, bid_amount DESC, created_at ASC)`
  - `idx_bid_auction_created`: Composite Index trên `(auction_id, created_at DESC)`

### 7. `orders` (Đơn hàng trúng thầu hậu đấu giá)
- **Quan hệ:**
  - Many-to-One với `auctions` (`auction_id`)
  - Many-to-One với `products` (`product_id`)
  - Many-to-One với `users` (`buyer_id`, `seller_id`)
  - One-to-Many với `payments`
- **Các trường chính:** `id`, `auction_id`, `product_id`, `buyer_id`, `seller_id`, `winning_price`, `shipping_address`, `phone_number`, `courier_name`, `tracking_number`, `status` (`OrderStatus`).

### 8. `payments` (Lịch sử thanh toán đơn hàng)
- **Quan hệ:**
  - Many-to-One với `orders` (`order_id`)
- **Các trường chính:** `id`, `order_id`, `amount`, `payment_method` (`PaymentMethod`), `transaction_code`, `status` (`PaymentStatus`).

---

# 9. Authentication & Authorization

....
---

# 10. Scheduler / Background Jobs

Dự án kích hoạt tính năng lập lịch tự động qua annotation `@EnableScheduling` tại `DuAnTrainningApplication`.

### `AuctionScheduler.java`
- **Tần suất chạy:** `@Scheduled(fixedRate = 10000)` — Chạy ngầm mỗi **10 giây**.
- **Nhiệm vụ nghiệp vụ:**
  1. `autoStartAuctions`: Thực thi câu lệnh Bulk Update SQL tự động chuyển các phiên đấu giá từ `SCHEDULED` sang `RUNNING` 
                          khi thời điểm hiện tại `>= startTime` và sản phẩm có trạng thái `APPROVED`.
  
  2. `autoEndAuctions`: Thực thi câu lệnh Bulk Update SQL tự động chuyển các phiên đấu giá từ `RUNNING` sang `ENDED` khi thời điểm hiện tại `>= endTime`.

---

# 11. Testing

Dự án áp dụng Unit Testing với khung kiểm thử chuẩn:
- **Thư viện:** JUnit 5, Mockito (`MockitoExtension`), AssertJ, JaCoCo Maven Plugin (0.8.12).
- **Unit Test:**
  - `ProductServiceTest`: Kiểm thử độc lập cho các phương thức tạo sản phẩm, truy vấn danh sách seller/public, admin phê duyệt/từ chối, hủy phiên và tái đăng bài thầu.
  - `BiddingServiceTest`: Kiểm thử độc lập cho thao tác đặt giá (bình thường & có gia hạn anti-sniping), lấy lịch sử thầu và tính năng mua ngay (Buy Now).
- **Integration Test:** Không tìm thấy trong source code
- **Code Coverage:** Đã cấu hình JaCoCo tự động tạo báo cáo độ bao phủ mã nguồn HTML khi thực thi lệnh `./mvnw test` (được cấu hình loại trừ các package `entity`, `dto`, `config`, `security`).

---

# 12. Development Status

Checklist trạng thái phát triển dựa trên source code thực tế:

### Đã hoàn thành:
- [x] Quản lý danh mục sản phẩm (Category Listing)
- [x] Tạo sản phẩm & đăng bài đấu giá đính kèm tải ảnh mây Cloudinary
- [x] Đồng bộ giao dịch DB Rollback / Commit với Cloudinary CDN (`TransactionSynchronizationManager`)
- [x] Chỉnh sửa sản phẩm, xóa/thêm ảnh và tự động re-index thứ tự hiển thị
- [x] Xóa bài đăng & hủy phiên đấu giá
- [x] Đăng lại phiên đấu giá đã hết hạn (Relist Auction)
- [x] Kiểm duyệt bài đăng bởi Admin (Approve / Reject kèm lưu lý do)
- [x] Đấu giá trực tuyến (Bidding Engine)
- [x] Tự động đấu giá (Proxy Bidding Engine)
- [x] Anti-Shill Bidding (chặn Seller tự bid) & Anti-Self-Outbid (chặn người dẫn đầu đè giá)
- [x] Chống bắn tỉa phút chót (Soft-Close Anti-Sniping tự động cộng 3 phút)
- [x] Mua ngay sản phẩm với giá cố định (Buy Now)
- [x] Lịch sử đấu giá công khai mã hóa ẩn danh tên người đặt
- [x] Robot quét tự động kích hoạt RUNNING / kết thúc ENDED ngầm (Scheduler 10s)
- [x] Tối ưu hóa truy vấn danh sách loại bỏ lỗi N+1 Query (Batch Loading In-Memory Map)
- [x] Xử lý ngoại lệ tập trung (Global Exception Handler & ErrorCode enum)
- [x] Unit Testing cho tầng Service (JUnit 5 + Mockito + JaCoCo coverage)

