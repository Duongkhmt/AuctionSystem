# 1. Project Overview

**AuctionSystem (Backend Microservices)** là hệ thống Backend phục vụ cho nền tảng **Đấu Giá Trực Tuyến (Online Auction Platform)** đa ngành hàng.

Hệ thống giải quyết bài toán đấu giá hàng hóa minh bạch, cạnh tranh theo thời gian thực và quản lý tài sản động. 
Nền tảng hỗ trợ người dùng đăng tải sản phẩm với thuộc tính đa dạng (Nhà đất, Xe hơi, Tranh ảnh, Đồ điện tử...), hỗ trợ quy trình kiểm duyệt bài đăng bởi Quản trị viên (Admin), tích hợp công cụ tự động đấu giá (Proxy Bidding Engine), 
chế độ chốt thầu thời gian cứng (Hard-Close Mode — Hết giờ là hết giờ), tự động xử lý đơn hàng bùng tiền quá 48h kèm phạt gậy vi phạm (Unpaid Strikes), và tự động hóa chuyển đổi trạng thái phiên đấu giá ngầm bằng Robot Scheduler.

### Đối tượng sử dụng:
**User (Người dùng hệ thống):** Xem thông tin sản phẩm, tham gia đặt giá cạnh tranh, cài đặt mức giá trần tự động đấu giá (Proxy Bid), 
xem lịch sử thầu ẩn danh, mua ngay sản phẩm với giá cố định (Buy Now), tạo sản phẩm đăng bán cá nhân, quản lý danh sách sản phẩm trúng thầu, 
chốt địa chỉ thanh toán Checkout, xác nhận nhận hàng và quản lý đơn hàng bán được (nhập mã vận đơn xuất hàng).

**Admin (Quản trị viên):** Xem danh sách các bài đăng sản phẩm chờ duyệt, thực hiện chấp thuận (Approve) 
hoặc từ chối bài đăng (Reject) kèm theo lý do cụ thể.

---

# 2. Tech Stack

- **Java:** 21 (Eclipse Temurin 21)
- **Spring Boot:** 3.2.3 (starter parent `org.springframework.boot:3.2.3`, Spring Cloud `2023.0.0`)
- **Apache Kafka (Spring Kafka):** `org.springframework.kafka:spring-kafka` — Hạ tầng Event-Driven Messaging bất đồng bộ xử lý vòng đời kết thúc đấu giá (`AUCTION_ENDED`), tích hợp cơ chế Non-Blocking Retry Topic 3 tầng (`-retry`, `-dlt`), cô lập tin nhắn hỏng DLT (`@DltHandler`), chống Dual-Write bằng Spring `afterCommit`, và Check-Then-Mark Idempotent Consumer kết hợp với Redis 24h.
- **Spring Security:** `org.springframework.boot:spring-boot-starter-security`
- **Spring Data JPA:** `org.springframework.boot:spring-boot-starter-data-jpa`
- **Validation:** `org.springframework.boot:spring-boot-starter-validation`
- **Spring Data Redis:** `org.springframework.boot:spring-boot-starter-data-redis` — Bộ nhớ đệm phân tán Redis (Caching `categories`, `auctions`, `bid_history`), Aspect giới hạn tốc độ Rate Limit bằng Redis Lua Script, Động cơ so kè giá nguyên tử Redis Atomic Lua Script (0.02ms) triệt tiêu Race Condition khi 100+ requests thầu đồng thời, và Redis Check-Then-Mark 24h chống đẻ trùng đơn hàng.
- **Redisson:** 3.35.0 (`org.redisson:redisson-spring-boot-starter`) — Hỗ trợ Distributed Lock cho hạ tầng đa nút.
- **Database:** PostgreSQL (`org.postgresql:postgresql`, phiên bản driver theo Spring Boot BOM)
- **MapStruct:** 1.6.2 (`org.mapstruct:mapstruct` và `org.mapstruct:mapstruct-processor`)
- **Lombok:** 1.18.34 (`org.projectlombok:lombok`)
- **Testing:** JUnit 5 (`spring-boot-starter-test`), Mockito (`org.mockito:mockito-junit-jupiter`), AssertJ (`org.assertj:assertj-core`), JaCoCo Maven Plugin 0.8.12 (`org.jacoco:jacoco-maven-plugin`)
- **Docker:** Multi-stage build (Alpine Linux + JDK/JRE 21)
- **Các thư viện khác:**
  - **Cloudinary HTTP5:** 2.0.0 (`com.cloudinary:cloudinary-http5`) — Quản lý và lưu trữ hình ảnh trên mây.
  - **Java Dotenv:** 3.0.0 (`io.github.cdimascio:dotenv-java`) — Đọc biến môi trường từ tập tin `.env`.

---

# 3. System Requirements

- **JDK:** 21 trở lên
- **Build Tool:** Apache Maven 3.8+ (hoặc sử dụng script `mvnw` đi kèm dự án)
- **Database:** PostgreSQL 15+ (bắt buộc hỗ trợ kiểu dữ liệu `JSONB`)
- **In-Memory Cache:** Redis 6+ (phục vụ Caching & Rate Limiting Lua Script)
- **Docker & Docker Compose:** Tuỳ chọn (phục vụ đóng gói container và triển khai)
- **Biến môi trường (Environment Variables):**
  - `spring.datasource.url` (Mặc định: `jdbc:postgresql://localhost:5432/auction_system`)
  - `spring.datasource.username` (Mặc định: `postgres`)
  - `spring.datasource.password`
  - `spring.data.redis.host` (Mặc định: `localhost`)
  - `spring.data.redis.port` (Mặc định: `6379`)
  - `CLOUDINARY_CLOUD_NAME`
  - `CLOUDINARY_API_KEY`
  - `CLOUDINARY_API_SECRET`

---

# 4. Installation & Docker Deployment

### Cách 1: Khởi chạy trên môi trường cục bộ (Local Development)

#### Bước 1: Clone dự án
```bash
git clone https://github.com/Duongkhmt/AuctionSystem.git
cd Backend/auction-service
```

#### Bước 2: Cấu hình Cơ sở dữ liệu & Biến môi trường
Tạo cơ sở dữ liệu PostgreSQL có tên `auction_system` trên máy địa phương hoặc máy chủ.  
Tạo tập tin `.env` tại thư mục gốc của dự án (hoặc cập nhật trực tiếp trong `src/main/resources/application.properties`):

```properties
CLOUDINARY_CLOUD_NAME=your_cloud_name
CLOUDINARY_API_KEY=your_api_key
CLOUDINARY_API_SECRET=your_api_secret
```

#### Bước 3: Biên dịch dự án (Build)
```bash
./mvnw clean package -DskipTests
```

#### Bước 4: Khởi chạy ứng dụng (Run)
```bash
./mvnw spring-boot:run
```
hoặc chạy tập tin `.jar` sau khi build:
```bash
java -jar target/auction-service-0.0.1-SNAPSHOT.jar
```

---

### Cách 2: Khởi chạy toàn bộ hệ thống bằng Docker Compose 

Dự án hỗ trợ `docker-compose.yml` điều phối tự động PostgreSQL 16 + Spring Boot Backend + Angular Frontend Nginx tích hợp sẵn cơ chế **Healthcheck** (`pg_isready`):

#### 1. Khởi chạy toàn bộ 3 dịch vụ (Database + BE + FE):
```bash
cd /home/duong/Projects
docker-compose up -d --build
```

#### 2. Khởi chạy cặp đôi Backend & Database PostgreSQL riêng (Dev Mode):
```bash
cd /home/duong/Projects/Backend/auction-service
docker-compose up -d --build
```

#### 3. Kiểm tra trạng thái và nhật ký log:
```bash
docker-compose ps
docker-compose logs -f backend-api
```

#### 4. Dừng và dọn dẹp hệ thống:
```bash
docker-compose down
```

---

### Cách 3: Build và Chạy Docker Container thủ công

#### Build Docker Image:
```bash
docker build -t auction-system-backend:latest .
```

#### Chạy Docker Container đơn lẻ:
```bash
docker run -d -p 8080:8080 \
  -e CLOUDINARY_CLOUD_NAME=your_cloud_name \
  -e CLOUDINARY_API_KEY=your_api_key \
  -e CLOUDINARY_API_SECRET=your_api_secret \
  --name auction-backend auction-system-backend:latest
```

---


# 5. Project Structure

Mã nguồn dự án được tổ chức theo cấu trúc sau:

```
src/main/java/com/duong/auction/system
├── aspect          # RateLimitAspect (@RateLimit chống spam API nguyên tử bằng Redis Lua Script)
├── config          # Cấu hình Spring Security, Cloudinary API, Redis CacheManager & i18n WebConfig
├── controller      # REST API Endpoints (Admin, Seller, Bidder, Public, AuctionBidding, Category)
├── dto
│   ├── request     # DTO đầu vào (ProductRequestDTO, CheckoutRequestDTO, ShipOrderRequestDTO...)
│   └── response    # DTO đầu ra (ProductResponseDTO, WonAuctionResponseDTO, SellerOrderResponseDTO...)
├── entity          # JPA Entities (Product, Auction, Bid, Order, Payment, Category, User)
├── enums           # Constants (AuctionStatus, ProductStatus, OrderStatus, PaymentMethod, PaymentStatus, UserRole, UserStatus)
├── exception       # Xử lý ngoại lệ tập trung (ApplicationException, ErrorCode, GlobalExceptionHandler)
├── mapper          # MapStruct Interfaces (ProductMapper, AuctionMapper, BidMapper, OrderMapper, UserMapper)
├── repository      # Spring Data JPA Repositories (Product, Auction, Bid, Order, Payment, UserRepository)
├── service         # Tầng nghiệp vụ chính (ProductService, BiddingService, OrderService, AuctionScheduler, CloudinaryService)
│   ├── engine      # RedisAtomicBiddingEngine (So kè giá nguyên tử Lua Script 0.02ms trên RAM)
│   └── helper      # Helpers (OrderResponseHelper, ProductResponseHelper, BidResponseHelper, ProxyBiddingEngineHelper...)
└── validator       # Validators (OrderValidator, BidValidator, AuctionValidator, ProductImageValidator)
```

---

# 6. Business Overview

Hệ thống đấu giá hoạt động theo quy trình nghiệp vụ khép kín từ đăng bán, kiểm duyệt, diễn ra phiên, kết thúc và thanh toán xử lý bùng tiền:

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
  
- **Redis Atomic Concurrency Engine:** Hệ thống gộp 3 thao tác `Đọc -> Kiểm tra -> Cập nhật giá` thành 1 thao tác nguyên tử duy nhất bằng Redis Lua Script (0.02ms). Vì Redis xử lý đơn luồng, 100+ requests đặt giá đồng thời vẫn được xếp hàng so kè chuẩn xác tại thời điểm xử lý, loại bỏ hoàn toàn Race Condition. Người trả giá thấp hơn nhận kết quả "thua giá" ngay lập tức mà không bị xung đột hệ thống.

- **Proxy Bidding Engine:** Bidder có thể nhập giá trần `maxAutoBidAmount`. Hệ thống tự động cạnh tranh và nâng giá hiện tại từng nấc 
để giữ vị trí dẫn đầu cho Bidder mà không vượt quá mức trần đã cài. Mọi lượt nhảy giá tự động đều sinh ra bản ghi `Bid` để đảm bảo 100% Audit Trail.

- **Chốt thầu thời gian cứng (Hard-Close Mode):** Phiên đấu giá kết thúc chính xác tại mốc thời gian `endTime` được thiết lập ban đầu (hết giờ là hết giờ), bảo đảm thời gian chốt thầu cố định và minh bạch.

- **Mua Ngay (Buy Now):** Đối với phiên có thiết lập giá mua ngay `buyNowPrice`, Bidder chấp nhận mức giá này có thể kích hoạt mua ngay. 
Hệ thống sẽ lập tức chốt phiên (`ENDED`), ghi nhận chiến thắng cho Bidder và cập nhật giá hiện tại bằng giá mua ngay.

### 4. Quản lý Đơn hàng & Thanh toán Hậu Đấu Giá (Post-Auction Order Settlement)
- **Tự động sinh đơn hàng:** Ngay khi phiên đấu giá hết giờ hoặc người mua thực hiện Mua Ngay, hệ thống (`AuctionScheduler` / `BiddingService.executeBuyNow`) tự động chốt người chiến thắng (`winner`) và tạo bản ghi Đơn hàng (`Order`) ở trạng thái **`UNPAID`** kèm thời hạn chót **48 giờ** (`paymentDeadline`).
- **Người mua Checkout:** Người mua vào danh sách đơn trúng thầu chọn đơn `UNPAID`, nhập địa chỉ nhận hàng, số điện thoại và chọn phương thức thanh toán. Hệ thống chuyển đơn sang **`PAID`** và sinh bản ghi Lịch sử thanh toán (`Payment`).
- **Người bán Xuất hàng:** Người bán kiểm tra danh sách đơn bán được, nhập thông tin đơn vị vận chuyển (`courierName`) và mã vận đơn (`trackingNumber`) để xuất hàng. Hệ thống chuyển đơn sang **`SHIPPING`**.
- **Người mua Nhận hàng:** Người mua nhận hàng đúng mô tả và bấm xác nhận. Hệ thống chuyển đơn sang **`COMPLETED`** và giải ngân hoàn tất giao dịch.

### 5. Xử lý Bùng Hàng & Gậy Vi Phạm (Unpaid Order Auto-Cancel & 3-Strikes Penalty)
- **Hạn chót thanh toán 48 tiếng (`paymentDeadline`):** Đơn hàng trúng thầu bắt buộc phải hoàn tất Checkout trong vòng 48h.
- **Tự động hủy đơn & Phạt gậy (`AuctionScheduler`):** Nếu quá 48h đơn vẫn ở trạng thái `UNPAID`, Robot Scheduler tự động chuyển đơn sang **`CANCELLED`** và tính **+1 Gậy Vi Phạm (Unpaid Strike)** cho tài khoản người mua.
- **Chế tài cấm đấu giá 90 ngày (3-Strikes Rule):** Khi người dùng tích lũy đủ **3 Gậy Vi Phạm**, hệ thống tự động khóa tính năng đặt giá / mua ngay trong vòng **90 ngày** (`bannedUntil = now + 90 days`).
- **Cơ chế tự động mở khóa lười (Lazy Unban Check):** Ngay khi hết thời hạn 90 ngày phạt, ở lần bấm đặt giá kế tiếp của người dùng, `BidValidator` tự động phát hiện, gỡ bỏ án cấm và reset gậy vi phạm về 0.

### 6. Vòng đời Trạng thái (State Machines)
- **ProductStatus:** `PENDING` ➔ `APPROVED` / `REJECTED`
- **AuctionStatus:** `PENDING_APPROVAL` ➔ `SCHEDULED` / `RUNNING` ➔ `ENDED` / `EXPIRED` / `CANCELLED`
- **OrderStatus:** `UNPAID` ➔ `PAID` ➔ `SHIPPING` ➔ `COMPLETED` | `CANCELLED` (do bùng quá 48h)
- **UserRole:** `USER`, `ADMIN`
- **UserStatus:** `ACTIVE`, `SUSPENDED`

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
- **Các trường chính:** `id`, `username`, `email`, `password_hash`, `unpaid_strike_count`, `banned_until`, `role` (`UserRole`), `status` (`UserStatus`), `created_at`.
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
- **Các trường chính:** `id`, `auction_id`, `product_id`, `buyer_id`, `seller_id`, `winning_price`, `shipping_address`, `phone_number`, `courier_name`, `tracking_number`, `payment_deadline`, `status` (`OrderStatus`).

### 8. `payments` (Lịch sử thanh toán đơn hàng)
- **Quan hệ:**
  - Many-to-One với `orders` (`order_id`)
- **Các trường chính:** `id`, `order_id`, `amount`, `payment_method` (`PaymentMethod`), `transaction_code`, `status` (`PaymentStatus`).

---

# 9. Authentication & Authorization

Dự án thiết lập sẵn hạ tầng Spring Security, hỗ trợ phân quyền vai trò dựa trên `UserRole` (`USER`, `ADMIN`). Giai đoạn tiếp theo sẽ tích hợp JWT Authentication Filter để đọc token từ `Authorization: Bearer <token>` Header.

---

# 10. Scheduler / Background Jobs

Dự án kích hoạt tính năng lập lịch tự động qua annotation `@EnableScheduling` tại `AuctionSystemApplication`.

### `AuctionScheduler.java`
- **Tần suất chạy:** `@Scheduled(fixedRate = 10000)` — Chạy ngầm mỗi **10 giây**.
- **Nhiệm vụ nghiệp vụ:**
  1. `autoStartAuctions`: Tự động chuyển các phiên từ `SCHEDULED` sang `RUNNING` khi tới giờ `startTime`.
  2. `autoExpireBuyNowAuctions`: Tự động hết hạn bài Mua Ngay 30 ngày.
  3. `processEndedAuctions`: Tự động chốt Winner và tạo Đơn hàng `UNPAID` kèm `paymentDeadline = 48h` cho phiên thầu kết thúc.
  4. `backfillMissingOrders`: Tự động bổ sung bản ghi đơn hàng bị khuyết cho các phiên có Winner.
  5. `processExpiredUnpaidOrders`: Tự động hủy đơn `UNPAID` quá 48h (`CANCELLED`), tăng `unpaidStrikeCount + 1` và cấm đấu giá 90 ngày khi bùng đủ 3 lần.

---

# 11. Testing & Code Coverage

Dự án áp dụng Pure Unit Testing với khung kiểm thử chuyên sâu:
- **Thư viện:** JUnit 5, Mockito (`MockitoExtension`), AssertJ, JaCoCo Maven Plugin (0.8.12).
- **Unit Test Suites (7 file test suite lớn):**
  - `ProductServiceTest`: Kiểm thử độc lập cho tạo sản phẩm, upload ảnh mây, admin duyệt/từ chối, hủy phiên và relist bài thầu.
  - `BiddingServiceTest`: Kiểm thử thao tác đặt giá, thời gian cứng Hard-Close Mode, lịch sử bid và mua ngay Buy Now.
  - `OrderServiceTest`: Kiểm thử truy vấn đơn trúng thầu, checkout thanh toán, người bán xuất hàng và người mua xác nhận nhận hàng.
  - `CategoryServiceTest`: Kiểm thử lọc danh mục sản phẩm active.
  - `AuctionSchedulerTest`: Kiểm thử robot chốt thầu hết giờ, phân biệt kịch bản `ENGLISH` vs `RESERVE`.
  - `BidStepCalculatorHelperTest`: Kiểm thử tính bước giá động theo 3 mốc bậc thang bằng kỹ thuật Spying (`@Spy`).
  - `CloudinaryServiceTest`: Kiểm thử tải ảnh và xóa ảnh trên Cloudinary CDN.
- **Code Coverage Report:** JaCoCo tự động tiêm probe đo độ phủ bytecode và tạo báo cáo HTML trực quan tại `target/site/jacoco/index.html` khi chạy `./mvnw test`.

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
- [x] Chốt thầu thời gian cứng (Hard-Close Mode — Hết giờ là hết giờ)
- [x] Mua ngay sản phẩm với giá cố định (Buy Now)
- [x] Lịch sử đấu giá công khai mã hóa ẩn danh tên người đặt
- [x] Quản lý đơn hàng trúng thầu & thanh toán Checkout / Xuất hàng / Xác nhận nhận hàng
- [x] Tự động hủy đơn bùng tiền quá 48h & Phạt Gậy Vi Phạm (Unpaid Strikes) cấm 90 ngày khi đủ 3 gậy
- [x] Refactor Enum hóa `UserRole` (`USER`, `ADMIN`) và `UserStatus` (`ACTIVE`, `SUSPENDED`)
- [x] Robot quét tự động kích hoạt RUNNING / kết thúc ENDED ngầm / dọn đơn 48h (Scheduler 10s)
- [x] Tối ưu hóa truy vấn danh sách loại bỏ lỗi N+1 Query (Batch Loading In-Memory Map)
- [x] Xử lý ngoại lệ tập trung (Global Exception Handler & ErrorCode enum)
- [x] Unit Testing cho tầng Service (7 Test Suites, JUnit 5 + Mockito + JaCoCo coverage)
- [x] Đóng gói Container Docker Multi-stage build (Alpine Linux + JRE 21)
