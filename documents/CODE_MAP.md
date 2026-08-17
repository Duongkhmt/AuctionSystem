# CODE MAP — DỰ ÁN DỰ ÁN AUCTION SYSTEM (BACKEND)
## 1. System Overview

- **Framework / Platform:** `✅ Confirmed` Java 21, Spring Boot 4.1.0 (`spring-boot-starter-web`, `spring-boot-starter-data-jpa`, `spring-boot-starter-security`, `spring-boot-starter-validation`).
- **Loại hình kiến trúc:** `✅ Confirmed` Backend RESTful API, Layered Architecture (Controller → Service → Repository → Database).
- **Entry Point:** `✅ Confirmed` [DuAnTrainningApplication.java](file:///home/duong/Projects/Backend/DuAnTrainning/src/main/java/DuAnTrainning/AuctionSystem/DuAnTrainningApplication.java) (có kích hoạt `@EnableScheduling`).
- **Database:** `✅ Confirmed` PostgreSQL (qua `spring-boot-starter-data-jpa`, PostgreSQL Driver, Hibernate Dialect PostgreSQL, cột JSONB mapped bằng `@JdbcTypeCode(SqlTypes.JSON)`).
- **Dịch vụ bên ngoài (External Service):** `✅ Confirmed` Cloudinary API (qua SDK `cloudinary-http5` 2.0.0 để lưu trữ và quản lý ảnh sản phẩm song song).
- **Thư viện bổ trợ (Tools/Mappers):** `✅ Confirmed` MapStruct 1.6.2 (sinh code ánh xạ DTO ↔ Entity), Lombok 1.18.34, `dotenv-java` 3.0.0.
- **Bảo mật (Security):** `✅ Confirmed` Spring Security tích hợp ở mức cơ bản ([SecurityConfig.java](file:///home/duong/Projects/Backend/DuAnTrainning/src/main/java/DuAnTrainning/AuctionSystem/config/SecurityConfig.java)): cho phép truy cập tất cả API (`permitAll()`), vô hiệu hóa CSRF, mở CORS cho mọi Origin. Chưa có JWT Filter / Session Authentication.
- **Tự động hóa ngầm (Scheduler):** `✅ Confirmed` Robot [AuctionScheduler.java](file:///home/duong/Projects/Backend/DuAnTrainning/src/main/java/DuAnTrainning/AuctionSystem/service/AuctionScheduler.java) chạy ngầm định kỳ 10 giây/lần tự động chuyển trạng thái phiên, sinh đơn hàng, xử lý hủy đơn bùng tiền quá 48h và phạt cấm đấu giá 90 ngày.

### Sơ đồ kiến trúc tổng quan hệ thống:

```text
               Client (HTTP RESTful / JSON snake_case)
                                 ↓
                     SecurityConfig (Cors & permitAll)
                                 ↓
                            Controller 
     (SellerProductController, BidderController, AuctionBiddingController,
      AdminProductController, ProductController, CategoryController)
                                 ↓
                      Validation & DTO Mapping
       (AuctionValidator, BidValidator, OrderValidator, ProductImageValidator)
       (ProductMapper, AuctionMapper, OrderMapper, BidMapper, ProductImageMapper)
                                 ↓
                             Service
     (ProductService, BiddingService, OrderService, CategoryService, CloudinaryService)
      ├── Helpers: (ProductResponseHelper, ProxyBiddingEngineHelper, 
      │             BidStepCalculatorHelper, OrderResponseHelper, 
      │             ProductAuctionLookupHelper, BidResponseHelper)
      └── Async Job: (AuctionScheduler @Scheduled 10s)
                                 ↓
                            Repository
     (UserRepository, ProductRepository, AuctionRepository, BidRepository, 
      OrderRepository, PaymentRepository, CategoryRepository, ProductImageRepository)
                                 ↓
                          Entity / Database
       (PostgreSQL: users, categories, products, product_images, 
                    auctions, bids, orders, payments)
                                 ↑
                      Cloudinary Service (Image CDN)
```

---

## 2. Project Structure

Cấu trúc cây thư mục nguồn thực tế trong `src/main/java/DuAnTrainning/AuctionSystem/`:

```text
com/duong/auction/system/
├── AuctionSystemApplication.java       # [Entry Point] Class khởi chạy ứng dụng & bật Scheduling
├── config/
│   ├── CloudinaryConfig.java           # Configuration Bean tạo instance Cloudinary SDK
│   └── SecurityConfig.java             # Configuration Spring Security & CORS
├── controller/
│   ├── AdminProductController.java     # Endpoint dành riêng cho Quản trị viên (Admin)
│   ├── AuctionBiddingController.java   # Endpoint Đặt giá (Bid) & Mua Ngay (Buy Now)
│   ├── BidderController.java           # Endpoint Cổng cá nhân Người Mua (Bidder Portal)
│   ├── CategoryController.java         # Endpoint xem danh mục công khai
│   ├── ProductController.java          # Endpoint xem sản phẩm công khai trên sàn
│   └── SellerProductController.java    # Endpoint dành riêng cho Người Bán (Seller)
├── dto/
│   ├── request/
│   │   ├── BidRequestDTO.java          # DTO gửi lượt đặt giá
│   │   ├── CheckoutRequestDTO.java     # DTO chốt địa chỉ & thanh toán đơn hàng
│   │   ├── ProductRejectRequestDTO.java# DTO nhập lý do Admin từ chối duyệt bài
│   │   ├── ProductRequestDTO.java      # DTO tạo bài đăng sản phẩm + cấu hình phiên
│   │   ├── ProductUpdateRequestDTO.java# DTO cập nhật sản phẩm + cấu hình phiên
│   │   └── ShipOrderRequestDTO.java    # DTO nhập mã vận đơn để xuất hàng
│   └── response/
│       ├── BidHistoryResponseDTO.java  # DTO xem lịch sử đặt giá công khai (ẩn danh)
│       ├── BidResponseDTO.java         # DTO phản hồi kết quả đặt giá / Mua Ngay thành công
│       ├── CategoryResponseDTO.java    # DTO danh mục sản phẩm
│       ├── CheckoutResponseDTO.java    # DTO kết quả thanh toán checkout
│       ├── ProductImageResponseDTO.java# DTO ảnh sản phẩm
│       ├── ProductResponseDTO.java     # DTO phẳng hợp nhất thông tin Product + Auction
│       ├── SellerOrderResponseDTO.java # DTO quản lý đơn hàng bán được cho Seller
│       └── WonAuctionResponseDTO.java  # DTO danh sách sản phẩm trúng thầu của Buyer
├── entity/
│   ├── Auction.java                    # Entity cấu hình & trạng thái phiên đấu giá
│   ├── Bid.java                        # Entity nhật ký lượt đặt giá (Audit Trail)
│   ├── Category.java                   # Entity danh mục sản phẩm phân cấp
│   ├── Order.java                      # Entity đơn hàng hậu đấu giá
│   ├── Payment.java                    # Entity bản ghi giao dịch thanh toán
│   ├── Product.java                    # Entity thông tin bài đăng sản phẩm
│   ├── ProductImage.java               # Entity lưu đường dẫn & public_id ảnh Cloudinary
│   └── User.java                       # Entity người dùng (Admin, Seller, Bidder)
├── enums/
│   ├── AuctionStatus.java              # PENDING_APPROVAL, SCHEDULED, RUNNING, ENDED, CANCELLED, EXPIRED
│   ├── AuctionType.java                # ENGLISH, RESERVE, BUY_NOW
│   ├── OrderStatus.java                # UNPAID, PAID, SHIPPING, COMPLETED, CANCELLED
│   ├── PaymentMethod.java              # VNPAY, WALLET, BANK_TRANSFER
│   ├── PaymentStatus.java              # SUCCESS, FAILED, PENDING
│   ├── ProductStatus.java              # PENDING, APPROVED, REJECTED
│   ├── UserRole.java                   # USER, ADMIN
│   └── UserStatus.java                 # ACTIVE, SUSPENDED
├── exception/
│   ├── ApplicationException.java       # Custom Runtime Exception mang ErrorCode
│   ├── ErrorCode.java                  # Enum tập hợp toàn bộ mã lỗi, thông điệp & HTTP Status
│   ├── ErrorResponse.java              # DTO format chuẩn cho HTTP Response lỗi
│   └── GlobalExceptionHandler.java    # Controller Advice bắt & format exception toàn hệ thống
├── mapper/
│   ├── AuctionMapper.java              # MapStruct ánh xạ DTO ↔ Auction Entity
│   ├── BidMapper.java                  # MapStruct ánh xạ Bid ↔ DTO (mã hóa tên bidder d***g)
│   ├── OrderMapper.java                # MapStruct ánh xạ Order ↔ WonAuction/SellerOrder DTO
│   ├── ProductImageMapper.java         # Ánh xạ danh sách UploadedImage ↔ ProductImage Entity
│   ├── ProductMapper.java              # MapStruct phẳng hóa Product + Auction ↔ ProductResponseDTO
│   └── UserMapper.java                 # (Tạm comment out trong source code hiện tại)
├── repository/
│   ├── AuctionRepository.java          # JPA Query & Bulk Update trạng thái phiên
│   ├── BidRepository.java              # JPA Query lấy Bid cao nhất & đè giá tự động
│   ├── CategoryRepository.java         # JPA Repository cho danh mục
│   ├── OrderRepository.java            # JPA Query đơn hàng theo Buyer/Seller & quá hạn 48h
│   ├── PaymentRepository.java          # JPA Repository cho thanh toán
│   ├── ProductImageRepository.java     # JPA Query ảnh sản phẩm theo displayOrder
│   ├── ProductRepository.java          # JPA Custom Query sắp xếp danh sách theo UX
│   └── UserRepository.java             # JPA Query tìm người dùng theo Email/ID
├── service/
│   ├── AuctionScheduler.java           # Robot chạy ngầm điều phối trạng thái & phạt bùng đơn
│   ├── BiddingService.java             # Nghiệp vụ Đặt giá, Proxy Bidding & Mua Ngay
│   ├── CategoryService.java            # Nghiệp vụ lấy danh mục hoạt động
│   ├── CloudinaryService.java          # Async Parallel Upload & Dọn dẹp ảnh Cloudinary
│   ├── OrderService.java               # Nghiệp vụ Đơn hàng, Checkout, Giao hàng, Xác nhận
│   ├── ProductService.java             # Nghiệp vụ CRUD sản phẩm, duyệt bài Admin
│   └── helper/
│       ├── BidResponseHelper.java      # Đóng gói DTO phản hồi lượt đặt giá
│       ├── BidStepCalculatorHelper.java # Tính bước giá động (Dynamic Bid Step)
│       ├── OrderResponseHelper.java    # Đóng gói DTO đơn hàng kèm ảnh đại diện
│       ├── ProductAuctionLookupHelper.java # Gom bộ đôi Product + Auction cho Admin
│       ├── ProductResponseHelper.java  # Đóng gói DTO sản phẩm & Batch Query chống N+1
│       └── ProxyBiddingEngineHelper.java # Thuật toán Đấu giá tự động Proxy Bidding (eBay Style)
└── validator/
    ├── AuctionValidator.java           # Validate thời gian, bước giá, giá bảo lưu/mua ngay
    ├── BidValidator.java               # Validate lượt bid, cấm tự bid, cấm tài khoản bị khóa
    ├── OrderValidator.java             # Validate chính chủ & trạng thái đơn hàng
    └── ProductImageValidator.java      # Validate số lượng (1-20 ảnh), format (JPG/PNG/WebP), size (<5MB)
```

---

## 3. Architecture Map

Hệ thống hoạt động theo mô hình kiến trúc phân lớp chuẩn (Layered Architecture):

```text
[HTTP Request]
     │
     ▼
[Controller Layer]  ── (Nhận DTO/Query Param, Validate cú pháp)
     │
     ▼
[Service Layer]     ── (Chứa Transaction @Transactional & Business Logic)
     ├──► [Validator Layer]     (Kiểm tra quy tắc nghiệp vụ khắt khe)
     ├──► [Helper Sub-layer]    (Tính toán Proxy Bidding, Bước giá, Batch Mapping)
     ├──► [Mapper Layer]        (MapStruct chuyển đổi DTO ↔ Entity)
     └──► [Cloudinary Service]  (Upload/Delete ảnh song song + Transaction Hook)
     │
     ▼
[Repository Layer]  ── (Spring Data JPA / JPQL Custom Queries)
     │
     ▼
[Database Layer]    ── (PostgreSQL Database)
```

---

## 4. Package Map

| Package          | Mục đích tồn tại                                                                             | Các class quan trọng                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                     | Sự phụ thuộc từ các package khác                                                                                       |
|:-----------------|:---------------------------------------------------------------------------------------------|:-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|:-----------------------------------------------------------------------------------------------------------------------|
| `controller`     | Tiếp nhận và điều hướng HTTP Request, gọi Service và trả về `ResponseEntity`.                | [AdminProductController](file:///home/duong/Projects/Backend/DuAnTrainning/src/main/java/DuAnTrainning/AuctionSystem/controller/AdminProductController.java), [AuctionBiddingController](file:///home/duong/Projects/Backend/DuAnTrainning/src/main/java/DuAnTrainning/AuctionSystem/controller/AuctionBiddingController.java), [BidderController](file:///home/duong/Projects/Backend/DuAnTrainning/src/main/java/DuAnTrainning/AuctionSystem/controller/BidderController.java), [SellerProductController](file:///home/duong/Projects/Backend/DuAnTrainning/src/main/java/DuAnTrainning/AuctionSystem/controller/SellerProductController.java), [ProductController](file:///home/duong/Projects/Backend/DuAnTrainning/src/main/java/DuAnTrainning/AuctionSystem/controller/ProductController.java), [CategoryController](file:///home/duong/Projects/Backend/DuAnTrainning/src/main/java/DuAnTrainning/AuctionSystem/controller/CategoryController.java)                                                               | Phụ thuộc vào `service`, `dto`. Không package nào phụ thuộc lại `controller`.                                          |
| `service`        | Thực thi logic nghiệp vụ kinh doanh, quản lý Transaction và điều phối dữ liệu.               | [ProductService](file:///home/duong/Projects/Backend/DuAnTrainning/src/main/java/DuAnTrainning/AuctionSystem/service/ProductService.java), [BiddingService](file:///home/duong/Projects/Backend/DuAnTrainning/src/main/java/DuAnTrainning/AuctionSystem/service/BiddingService.java), [OrderService](file:///home/duong/Projects/Backend/DuAnTrainning/src/main/java/DuAnTrainning/AuctionSystem/service/OrderService.java), [CloudinaryService](file:///home/duong/Projects/Backend/DuAnTrainning/src/main/java/DuAnTrainning/AuctionSystem/service/CloudinaryService.java), [AuctionScheduler](file:///home/duong/Projects/Backend/DuAnTrainning/src/main/java/DuAnTrainning/AuctionSystem/service/AuctionScheduler.java), [CategoryService](file:///home/duong/Projects/Backend/DuAnTrainning/src/main/java/DuAnTrainning/AuctionSystem/service/CategoryService.java)                                                                                                                                                 | Được gọi bởi `controller`. Phụ thuộc vào `repository`, `mapper`, `validator`, `helper`, `exception`, `entity`, `dto`.  |
| `service.helper` | Đóng gói các thuật toán tính toán phức tạp (Proxy Bidding, Bước giá, Batch Query chống N+1). | [ProxyBiddingEngineHelper](file:///home/duong/Projects/Backend/DuAnTrainning/src/main/java/DuAnTrainning/AuctionSystem/service/helper/ProxyBiddingEngineHelper.java), [ProductResponseHelper](file:///home/duong/Projects/Backend/DuAnTrainning/src/main/java/DuAnTrainning/AuctionSystem/service/helper/ProductResponseHelper.java), [BidStepCalculatorHelper](file:///home/duong/Projects/Backend/DuAnTrainning/src/main/java/DuAnTrainning/AuctionSystem/service/helper/BidStepCalculatorHelper.java), [OrderResponseHelper](file:///home/duong/Projects/Backend/DuAnTrainning/src/main/java/DuAnTrainning/AuctionSystem/service/helper/OrderResponseHelper.java), [ProductAuctionLookupHelper](file:///home/duong/Projects/Backend/DuAnTrainning/src/main/java/DuAnTrainning/AuctionSystem/service/helper/ProductAuctionLookupHelper.java), [BidResponseHelper](file:///home/duong/Projects/Backend/DuAnTrainning/src/main/java/DuAnTrainning/AuctionSystem/service/helper/BidResponseHelper.java)                   | Được sử dụng chuyên biệt bởi `service`.                                                                                |
| `validator`      | Đảm bảo dữ liệu thỏa mãn điều kiện kinh doanh trước khi thay đổi trạng thái hệ thống.        | [AuctionValidator](file:///home/duong/Projects/Backend/DuAnTrainning/src/main/java/DuAnTrainning/AuctionSystem/validator/AuctionValidator.java), [BidValidator](file:///home/duong/Projects/Backend/DuAnTrainning/src/main/java/DuAnTrainning/AuctionSystem/validator/BidValidator.java), [OrderValidator](file:///home/duong/Projects/Backend/DuAnTrainning/src/main/java/DuAnTrainning/AuctionSystem/validator/OrderValidator.java), [ProductImageValidator](file:///home/duong/Projects/Backend/DuAnTrainning/src/main/java/DuAnTrainning/AuctionSystem/validator/ProductImageValidator.java)                                                                                                                                                                                                                                                                                                                                                                                                                         | Được gọi bởi `service`. Ném `ApplicationException` nếu vi phạm quy tắc.                                                |
| `mapper`         | Chuyển đổi dữ liệu giữa DTO và JPA Entity.                                                   | [ProductMapper](file:///home/duong/Projects/Backend/DuAnTrainning/src/main/java/DuAnTrainning/AuctionSystem/mapper/ProductMapper.java), [AuctionMapper](file:///home/duong/Projects/Backend/DuAnTrainning/src/main/java/DuAnTrainning/AuctionSystem/mapper/AuctionMapper.java), [BidMapper](file:///home/duong/Projects/Backend/DuAnTrainning/src/main/java/DuAnTrainning/AuctionSystem/mapper/BidMapper.java), [OrderMapper](file:///home/duong/Projects/Backend/DuAnTrainning/src/main/java/DuAnTrainning/AuctionSystem/mapper/OrderMapper.java), [ProductImageMapper](file:///home/duong/Projects/Backend/DuAnTrainning/src/main/java/DuAnTrainning/AuctionSystem/mapper/ProductImageMapper.java)                                                                                                                                                                                                                                                                                                                     | Được gọi bởi `service` và `helper`.                                                                                    |
| `repository`     | Tương tác trực tiếp với Database PostgreSQL qua Spring Data JPA & Custom JPQL Queries.       | [ProductRepository](file:///home/duong/Projects/Backend/DuAnTrainning/src/main/java/DuAnTrainning/AuctionSystem/repository/ProductRepository.java), [AuctionRepository](file:///home/duong/Projects/Backend/DuAnTrainning/src/main/java/DuAnTrainning/AuctionSystem/repository/AuctionRepository.java), [BidRepository](file:///home/duong/Projects/Backend/DuAnTrainning/src/main/java/DuAnTrainning/AuctionSystem/repository/BidRepository.java), [OrderRepository](file:///home/duong/Projects/Backend/DuAnTrainning/src/main/java/DuAnTrainning/AuctionSystem/repository/OrderRepository.java), [PaymentRepository](file:///home/duong/Projects/Backend/DuAnTrainning/src/main/java/DuAnTrainning/AuctionSystem/repository/PaymentRepository.java), [UserRepository](file:///home/duong/Projects/Backend/DuAnTrainning/src/main/java/DuAnTrainning/AuctionSystem/repository/UserRepository.java)                                                                                                                     | Được phụ thuộc bởi `service`, `helper`, `validator`.                                                                   |
| `entity`         | Mô hình hóa các bảng cơ sở dữ liệu thành Java Object (ORM JPA).                              | [User](file:///home/duong/Projects/Backend/DuAnTrainning/src/main/java/DuAnTrainning/AuctionSystem/entity/User.java), [Product](file:///home/duong/Projects/Backend/DuAnTrainning/src/main/java/DuAnTrainning/AuctionSystem/entity/Product.java), [Auction](file:///home/duong/Projects/Backend/DuAnTrainning/src/main/java/DuAnTrainning/AuctionSystem/entity/Auction.java), [Bid](file:///home/duong/Projects/Backend/DuAnTrainning/src/main/java/DuAnTrainning/AuctionSystem/entity/Bid.java), [Order](file:///home/duong/Projects/Backend/DuAnTrainning/src/main/java/DuAnTrainning/AuctionSystem/entity/Order.java), [Payment](file:///home/duong/Projects/Backend/DuAnTrainning/src/main/java/DuAnTrainning/AuctionSystem/entity/Payment.java), [Category](file:///home/duong/Projects/Backend/DuAnTrainning/src/main/java/DuAnTrainning/AuctionSystem/entity/Category.java), [ProductImage](file:///home/duong/Projects/Backend/DuAnTrainning/src/main/java/DuAnTrainning/AuctionSystem/entity/ProductImage.java) | Được sử dụng xuyên suốt ở tất cả các tầng ngoại trừ Controller.                                                        |
| `dto`            | Data Transfer Object vận chuyển dữ liệu giữa Client và Server.                               | Các file trong `dto/request/` và `dto/response/`.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                        | Sử dụng ở `controller`, `service`, `mapper`, `helper`.                                                                 |
| `exception`      | Quản lý lỗi tập trung và trả về JSON chuẩn theo ErrorCode.                                   | [GlobalExceptionHandler](file:///home/duong/Projects/Backend/DuAnTrainning/src/main/java/DuAnTrainning/AuctionSystem/exception/GlobalExceptionHandler.java), [ApplicationException](file:///home/duong/Projects/Backend/DuAnTrainning/src/main/java/DuAnTrainning/AuctionSystem/exception/ApplicationException.java), [ErrorCode](file:///home/duong/Projects/Backend/DuAnTrainning/src/main/java/DuAnTrainning/AuctionSystem/exception/ErrorCode.java), [ErrorResponse](file:///home/duong/Projects/Backend/DuAnTrainning/src/main/java/DuAnTrainning/AuctionSystem/exception/ErrorResponse.java)                                                                                                                                                                                                                                                                                                                                                                                                                       | Sử dụng ở toàn bộ ứng dụng khi ném exception.                                                                          |
| `config`         | Cấu hình Bean hệ thống (Security, CORS, Cloudinary SDK).                                     | [SecurityConfig](file:///home/duong/Projects/Backend/DuAnTrainning/src/main/java/DuAnTrainning/AuctionSystem/config/SecurityConfig.java), [CloudinaryConfig](file:///home/duong/Projects/Backend/DuAnTrainning/src/main/java/DuAnTrainning/AuctionSystem/config/CloudinaryConfig.java)                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                   | Tự động load bởi Spring Container khi khởi chạy app.                                                                   |

---

## 5. Class Responsibility Map

### Controllers
- **`SellerProductController`**: Cung cấp Endpoint cho Seller tạo bài đăng, sửa sản phẩm, xóa bài chưa chạy, chủ động hủy bài, relist bài hết hạn, xem đơn bán được và bấm ship đơn hàng.
- **`BidderController`**: Cung cấp Endpoint cho Bidder xem danh sách sản phẩm trúng thầu, thực hiện checkout điền địa chỉ & thanh toán, xác nhận đã nhận hàng.
- **`AuctionBiddingController`**: Cung cấp Endpoint đặt giá cạnh tranh (Bid), mua ngay (Buy Now), xem lịch sử đặt giá công khai (mã hóa tên `d***g`).
- **`AdminProductController`**: Cung cấp Endpoint cho Admin duyệt danh sách bài chờ (`PENDING`), duyệt chấp thuận (`APPROVE`), từ chối bài đăng kèm lý do vi phạm (`REJECT`).
- **`ProductController`**: Cung cấp Endpoint công khai xem danh sách sản phẩm đã được duyệt (`APPROVED`) và chi tiết sản phẩm.
- **`CategoryController`**: Cung cấp Endpoint công khai xem danh sách danh mục hoạt động.

### Services & Helpers
- **`ProductService`**: Quản lý toàn bộ vòng đời sản phẩm: validate, tạo bản ghi, upload ảnh Cloudinary song song, đăng ký rollback hook, sửa/xóa ảnh, duyệt bài Admin, relist.
- **`BiddingService`**: Quản lý luồng đặt giá cạnh tranh, gọi Proxy Bidding Engine so kè trần ngân sách, kích hoạt kéo dài thời gian Soft-close (Anti-sniping 3 phút), xử lý chốt đơn Mua Ngay.
- **`OrderService`**: Quản lý luồng đơn hàng: xem đơn trúng thầu, thực hiện checkout tạo giao dịch Payment, chuyển trạng thái đơn hàng (PAID → SHIPPING → COMPLETED).
- **`CloudinaryService`**: Upload song song nhiều file ảnh lên Cloudinary (`CompletableFuture`), dọn dẹp ảnh lỗi hoặc ảnh cần xóa theo `publicId`.
- **`AuctionScheduler`**: Robot tự động chạy định kỳ 10s: chuyển trạng thái `SCHEDULED` → `RUNNING`, chốt Winner khi phiên hết giờ, sinh đơn hàng `UNPAID` (hạn 48h), quét hủy đơn quá 48h và cộng strike phạt khóa 90 ngày.
- **`ProxyBiddingEngineHelper`**: Thực thi thuật toán đấu giá tự động kiểu eBay, so sánh trần ngân sách `maxAutoBid`, tự sinh bản ghi Bid đè giá ngầm lưu Audit Trail 100%.
- **`ProductResponseHelper`**: Đóng gói DTO phẳng duy nhất cho sản phẩm, tích hợp thuật toán Batch Query (`findByProduct_IdIn`) triệt tiêu lỗi N+1 Query.
- **`BidStepCalculatorHelper`**: Tính bước giá động (`STEP_LOW`: 10k, `STEP_MEDIUM`: 100k, `STEP_HIGH`: 500k) kết hợp bước giá tối thiểu của Seller.

### Mappers & Validators
- **`ProductMapper` / `AuctionMapper` / `OrderMapper` / `BidMapper`**: Dùng MapStruct sinh code chuyển đổi dữ liệu DTO ↔ Entity tự động. `BidMapper` tự động mã hóa tên hiển thị công khai.
- **`AuctionValidator`**: Kiểm tra điều kiện loại đấu giá (ENGLISH, RESERVE, BUY_NOW), thời lượng phiên ≥ 30 phút, giá khởi điểm/bảo lưu/mua ngay, thời gian không ở quá khứ.
- **`BidValidator`**: Kiểm tra phiên `RUNNING`, cấm Seller tự đặt giá bài mình, cấm Bidder đang dẫn đầu tự đè giá, kiểm tra tài khoản không ở trạng thái bị phạt khóa.
- **`OrderValidator`**: Kiểm tra chính chủ Buyer/Seller và tính hợp lệ của trạng thái chuyển đổi đơn hàng (`UNPAID` → `PAID` → `SHIPPING` → `COMPLETED`).
- **`ProductImageValidator`**: Kiểm tra file ảnh từ 1 đến 20 file, định dạng JPG/PNG/WebP, dung lượng tối đa 5MB/file.

---

## 6. Dependency Map

### Service Dependency Trees

```text
ProductService
├── UserRepository
├── CategoryRepository
├── ProductRepository
├── ProductImageRepository
├── AuctionRepository
├── ProductMapper
├── AuctionMapper
├── ProductImageMapper
├── AuctionValidator
├── ProductImageValidator
├── CloudinaryService
├── ProductResponseHelper
└── ProductAuctionLookupHelper

BiddingService
├── UserRepository
├── AuctionRepository
├── BidRepository
├── OrderRepository
├── BidValidator
├── BidMapper
├── OrderMapper
├── ProxyBiddingEngineHelper
└── BidResponseHelper

OrderService
├── UserRepository
├── OrderRepository
├── PaymentRepository
├── OrderValidator
├── OrderMapper
└── OrderResponseHelper

AuctionScheduler
├── AuctionRepository
├── BidRepository
├── OrderRepository
├── UserRepository
└── OrderMapper
```

### Lí do từng dependency tồn tại:
1. **`ProductService` → `CloudinaryService`**: Upload ảnh lên CDN Cloudinary và xóa ảnh cũ khi update/delete.
2. **`ProductService` → `TransactionSynchronizationManager`**: Đăng ký hook xóa ảnh Cloudinary nếu DB Rollback hoặc sau khi Transaction Commit thành công.
3. **`BiddingService` → `ProxyBiddingEngineHelper`**: Tách bạch thuật toán so kè trần ngân sách Proxy Bidding phức tạp khỏi BiddingService.
4. **`BiddingService` → `BidValidator`**: Đảm bảo không người dùng nào bị phạt cấm đấu giá hoặc tự bid bài mình được phép đặt giá.
5. **`AuctionScheduler` → `OrderMapper`**: Tự động tạo Entity Order khi robot quét thấy phiên đấu giá kết thúc có người thắng cuộc.

---

## 7. API Request Flow

### 🛒 PHÂN HỆ NGƯỜI BÁN (SELLER)

#### Flow 1: Người Bán tạo bài đăng sản phẩm mới (`POST /v1/sellers/{sellerId}/products`)

```text
POST /v1/sellers/{sellerId}/products (Multipart Form-Data)
        ↓
SellerProductController.createProduct()
        ↓
Validation: ProductRequestDTO (@Valid)
        ↓
ProductService.createProduct()
        ├─► UserRepository.findById(sellerId) -> Kiểm tra Seller tồn tại
        ├─► CategoryRepository.findById() -> Kiểm tra Danh mục active
        ├─► ProductImageValidator.validate() -> Kiểm tra 1-20 ảnh, format, size
        ├─► AuctionValidator.validate() -> Kiểm tra quy tắc loại phiên, thời gian >= 30p
        ├─► ProductMapper.toEntity() -> Map sang Product (ProductStatus = PENDING)
        ├─► CloudinaryService.uploadAll() -> Upload ảnh song song lên Cloudinary
        │     └─► registerSynchronization(afterCompletion -> rollback clean images)
        ├─► ProductRepository.save(product) -> Lưu Product DB
        ├─► ProductImageMapper.toEntities() & ProductImageRepository.saveAll()
        ├─► AuctionMapper.toEntity() & AuctionRepository.save(auction)
        └─► ProductResponseHelper.build() -> Trả về ProductResponseDTO (201 CREATED)
```

#### Flow 2: Người Bán cập nhật bài đăng sản phẩm (`PUT /v1/sellers/{sellerId}/products/{productId}`)

```text
PUT /v1/sellers/{sellerId}/products/{productId} (Multipart Form-Data)
        ↓
SellerProductController.updateProduct()
        ↓
ProductService.updateProduct()
        ├─► findProductAndValidatePermission() -> Kiểm tra sản phẩm tồn tại & đúng chính chủ
        ├─► findAuctionAndValidateStatus() -> Bắt buộc AuctionStatus == PENDING_APPROVAL hoặc SCHEDULED
        ├─► updateCategoryIfChanged() -> Kiểm tra & cập nhật Danh mục mới
        ├─► ProductMapper.updateProductFromDto() & AuctionMapper.updateAuctionFromDto()
        ├─► AuctionValidator.validateAuctionEntity() -> Re-validate quy tắc cấu hình phiên
        ├─► handleImageUpdates() -> Xóa ảnh chọn lọc + Upload ảnh mới [1-20 ảnh]
        │     ├─► registerSynchronization(afterCommit -> xóa Cloudinary cũ)
        │     └─► reindexImageDisplayOrders() -> Đánh lại thứ tự hiển thị displayOrder
        ├─► ProductRepository.save() & AuctionRepository.save()
        └─► ProductResponseHelper.build() -> Trả về ProductResponseDTO (200 OK)
```

#### Flow 3: Người Bán xóa vĩnh viễn sản phẩm (`DELETE /v1/sellers/{sellerId}/products/{productId}`)

```text
DELETE /v1/sellers/{sellerId}/products/{productId}
        ↓
SellerProductController.deleteProduct()
        ↓
ProductService.deleteProduct()
        ├─► findProductAndValidatePermission() -> Kiểm tra chính chủ Seller
        ├─► findAuctionAndValidateStatusForDelete() -> Cấm xóa khi AuctionStatus == RUNNING / ENDED
        ├─► Thu thập danh sách publicId tất cả ảnh sản phẩm
        ├─► registerSynchronization(afterCommit -> dọn dẹp ảnh trên Cloudinary)
        ├─► ProductImageRepository.deleteByProductId() -> Xóa toàn bộ ảnh DB
        ├─► AuctionRepository.deleteByProduct_Id() -> Xóa bản ghi phiên DB
        └─► ProductRepository.delete(product) -> Xóa bản ghi sản phẩm DB (204 NO CONTENT)
```

#### Flow 4: Người Bán chủ động hủy phiên đấu giá (`PUT /v1/sellers/{sellerId}/products/{productId}/cancel`)

```text
PUT /v1/sellers/{sellerId}/products/{productId}/cancel
        ↓
SellerProductController.cancelAuction()
        ↓
ProductService.cancelAuction()
        ├─► findProductAndValidatePermission() -> Kiểm tra chính chủ Seller
        ├─► findAuctionAndValidateStatus() -> Bắt buộc AuctionStatus == PENDING_APPROVAL / SCHEDULED
        ├─► auction.setStatus(AuctionStatus.CANCELLED)
        ├─► AuctionRepository.save(auction)
        └─► ProductResponseHelper.build() -> Trả về ProductResponseDTO (200 OK)
```

#### Flow 5: Người Bán đăng lại (Relist) bài thầu hết hạn 30 ngày (`POST /v1/sellers/{sellerId}/auctions/{auctionId}/relist`)

```text
POST /v1/sellers/{sellerId}/auctions/{auctionId}/relist
        ↓
SellerProductController.relistAuction()
        ↓
ProductService.relistAuction()
        ├─► AuctionRepository.findById(auctionId)
        ├─► AuctionValidator.validateRelist() -> Kiểm tra chính chủ & AuctionStatus == EXPIRED
        ├─► Reset startTime = NOW(), endTime = NOW() + 30 days
        ├─► auction.setStatus(AuctionStatus.RUNNING)
        ├─► AuctionRepository.save(auction)
        └─► ProductResponseHelper.build() -> Trả về ProductResponseDTO (200 OK)
```

#### Flow 6: Người Bán xem danh sách bài đăng của mình (`GET /v1/sellers/{sellerId}/products`)

```text
GET /v1/sellers/{sellerId}/products
        ↓
SellerProductController.getProductsBySellerId()
        ↓
ProductService.getProductsBySellerId()
        ├─► UserRepository.findById(sellerId) -> Kiểm tra Seller tồn tại
        ├─► ProductRepository.findProductsBySellerIdSorted() -> Lấy danh sách sản phẩm
        └─► ProductResponseHelper.buildAll() -> Batch query N+1 & trả về List<ProductResponseDTO> (200 OK)
```

#### Flow 7: Người Bán xem danh sách đơn hàng bán được (`GET /v1/sellers/{sellerId}/orders`)

```text
GET /v1/sellers/{sellerId}/orders
        ↓
SellerProductController.getSellerOrders()
        ↓
OrderService.getSellerOrders()
        ├─► UserRepository.findById(sellerId) -> Kiểm tra Seller tồn tại
        ├─► OrderRepository.findBySeller_IdOrderByCreatedAtDesc() -> Lấy danh sách đơn hàng
        └─► OrderResponseHelper.buildSellerOrderDTOs() -> Trả về List<SellerOrderResponseDTO> (200 OK)
```

#### Flow 8: Người Bán xuất hàng & nhập mã vận đơn (`PUT /v1/sellers/{sellerId}/orders/{orderId}/ship`)

```text
PUT /v1/sellers/{sellerId}/orders/{orderId}/ship
        ↓
SellerProductController.shipOrder()
        ↓
OrderService.shipOrder()
        ├─► OrderRepository.findById(orderId)
        ├─► OrderValidator.validateShipOrder() -> Bắt buộc chính chủ Seller & order.status == PAID
        ├─► Cập nhật courierName, trackingNumber
        ├─► order.setStatus(OrderStatus.SHIPPING)
        ├─► OrderRepository.save(order)
        └─► OrderMapper.toSellerOrderDTO() -> Trả về SellerOrderResponseDTO (200 OK)
```

---

### 🛡️ PHÂN HỆ QUẢN TRỊ VIÊN (ADMIN)

#### Flow 9: Admin lấy danh sách bài đăng chờ duyệt (`GET /v1/admin/products/pending`)

```text
GET /v1/admin/products/pending
        ↓
AdminProductController.getPendingProducts()
        ↓
ProductService.getPendingProducts()
        ├─► ProductRepository.findByStatusOrderByCreatedAtDesc(ProductStatus.PENDING)
        └─► ProductResponseHelper.buildAll() -> Trả về List<ProductResponseDTO> (200 OK)
```

#### Flow 10: Admin chấp thuận xuất bản bài đăng (`PUT /v1/admin/products/{productId}/approve`)

```text
PUT /v1/admin/products/{productId}/approve
        ↓
AdminProductController.approveProduct()
        ↓
ProductService.approveProduct()
        ├─► ProductAuctionLookupHelper.findPendingProductAndAuction() -> Lấy bộ đôi PENDING
        ├─► Kiểm tra endTime > NOW() (Nếu endTime < NOW -> Ném lỗi AUCTION_EXPIRED_BEFORE_APPROVAL)
        ├─► Phân nhánh trạng thái:
        │     ├─► Nếu startTime <= NOW() -> auction.setStatus(AuctionStatus.RUNNING)
        │     └─► Nếu startTime > NOW() -> auction.setStatus(AuctionStatus.SCHEDULED)
        ├─► product.setStatus(ProductStatus.APPROVED)
        ├─► ProductRepository.save() & AuctionRepository.save()
        └─► ProductResponseHelper.build() -> Trả về ProductResponseDTO (200 OK)
```

#### Flow 11: Admin từ chối bài đăng kèm lý do (`PUT /v1/admin/products/{productId}/reject`)

```text
PUT /v1/admin/products/{productId}/reject
        ↓
AdminProductController.rejectProduct()
        ↓
ProductService.rejectProduct()
        ├─► ProductAuctionLookupHelper.findPendingProductAndAuction()
        ├─► product.setStatus(ProductStatus.REJECTED) & setRejectionReason(dto.getRejectionReason())
        ├─► auction.setStatus(AuctionStatus.CANCELLED)
        ├─► ProductRepository.save() & AuctionRepository.save()
        └─► ProductResponseHelper.build() -> Trả về ProductResponseDTO (200 OK)
```

---

### 🔨 PHÂN HỆ ĐẤU GIÁ & ĐẶT GIÁ (BIDDING & BUY NOW)

#### Flow 12: Đặt giá cạnh tranh & Proxy Bidding (`POST /v1/auctions/{auctionId}/bids?bidderId=X`)

```text
POST /v1/auctions/{auctionId}/bids?bidderId=1
        ↓
AuctionBiddingController.placeBid()
        ↓
BiddingService.placeBid()
        ├─► UserRepository.findById(bidderId) -> Tìm người mua
        ├─► AuctionRepository.findById() -> Tìm phiên đấu giá
        ├─► BidRepository.findTopByAuctionId...() -> Lấy lượt bid cao nhất hiện tại
        ├─► BidValidator.validateBid()
        │     ├─► Validate bidder không bị cấm do bùng đơn (BannedUntil < NOW -> reset)
        │     ├─► Validate auction.status == RUNNING & trong khung giờ
        │     ├─► Anti-Shill: Seller không được tự bid
        │     ├─► Anti-Self-Outbid: Bidder dẫn đầu không tự đè giá mình
        │     └─► Validate bidAmount >= currentPrice + bước giá động
        ├─► ProxyBiddingEngineHelper.processProxyBidding()
        │     ├─► Tạo Human Bid của B
        │     ├─► Tìm Max Auto-bid của đối thủ A
        │     └─► So kè trần ngân sách -> Sinh các bản ghi Auto-bid đè giá ngầm
        ├─► Anti-sniping check: endTime - 3 phút < NOW?
        │     └─► Kéo dài endTime thêm +3 phút (timeExtended = true)
        ├─► AuctionRepository.save(auction) -> Cập nhật currentPrice mới
        ├─► BidRepository.saveAll(proxyResult.bidsToSave) -> Lưu 100% Audit Trail
        └─► BidResponseHelper.buildResponse() -> Trả về BidResponseDTO (201 CREATED)
```

#### Flow 13: Mua ngay giá cố định (`POST /v1/auctions/{auctionId}/buy-now?bidderId=X`)

```text
POST /v1/auctions/{auctionId}/buy-now?bidderId=1
        ↓
AuctionBiddingController.executeBuyNow()
        ↓
BiddingService.executeBuyNow()
        ├─► UserRepository.findById(bidderId) & AuctionRepository.findById(auctionId)
        ├─► BidValidator.validateBuyNow() -> Validate status == RUNNING & buyNowPrice != null
        ├─► auction.setStatus(AuctionStatus.ENDED) & auction.setWinner(bidder)
        ├─► Tạo bản ghi Bid mới mức giá buyNowPrice (isBuyNow = true)
        ├─► Hủy toàn bộ Proxy Bid tự động của những người chơi khác (maxAutoBid = null)
        ├─► Tạo ngay bản ghi Order hậu đấu giá (status = UNPAID, hạn 48h)
        ├─► AuctionRepository.save(), BidRepository.save(), OrderRepository.save()
        └─► BidResponseHelper.buildBuyNowResponse() -> Trả về BidResponseDTO (200 OK)
```

#### Flow 14: Xem lịch sử đặt giá công khai ẩn danh (`GET /v1/auctions/{auctionId}/bids`)

```text
GET /v1/auctions/{auctionId}/bids
        ↓
AuctionBiddingController.getAuctionBidHistory()
        ↓
BiddingService.getAuctionBidHistory()
        ├─► AuctionRepository.findById(auctionId) -> Kiểm tra phiên tồn tại
        ├─► BidRepository.findByAuctionIdOrderByBidTimeDesc() -> Lấy danh sách lượt bid
        └─► BidMapper.toBidHistoryDTOs() -> Mã hóa tên người mua (vd: d***g) -> List<BidHistoryResponseDTO> (200 OK)
```

---

### 👤 PHÂN HỆ NGƯỜI MUA (BIDDER PORTAL)

#### Flow 15: Bidder xem danh sách sản phẩm trúng thầu (`GET /v1/bidders/{bidderId}/won-auctions`)

```text
GET /v1/bidders/{bidderId}/won-auctions
        ↓
BidderController.getWonAuctions()
        ↓
OrderService.getWonAuctions()
        ├─► UserRepository.findById(bidderId) -> Kiểm tra Bidder tồn tại
        ├─► OrderRepository.findByBuyer_IdOrderByCreatedAtDesc() -> Lấy danh sách đơn hàng đã thắng
        └─► OrderResponseHelper.buildWonAuctionDTOs() -> Trả về List<WonAuctionResponseDTO> (200 OK)
```

#### Flow 16: Bidder Checkout điền địa chỉ & thanh toán (`POST /v1/bidders/{bidderId}/orders/{orderId}/checkout`)

```text
POST /v1/bidders/{bidderId}/orders/{orderId}/checkout
        ↓
BidderController.checkout()
        ↓
OrderService.checkout()
        ├─► OrderRepository.findById(orderId)
        ├─► OrderValidator.validateCheckout() -> Bắt buộc order.buyer.id == bidderId & order.status == UNPAID
        ├─► Cập nhật shippingAddress, phoneNumber, paymentMethod
        ├─► Tạo bản ghi Payment (PaymentStatus = SUCCESS, transactionCode = "TXN_...")
        ├─► order.setStatus(OrderStatus.PAID)
        ├─► OrderRepository.save(order) & PaymentRepository.save(payment)
        └─► OrderMapper.toCheckoutDTO() -> Trả về CheckoutResponseDTO (200 OK)
```

#### Flow 17: Bidder xác nhận đã nhận hàng (`PUT /v1/bidders/{bidderId}/orders/{orderId}/confirm-received`)

```text
PUT /v1/bidders/{bidderId}/orders/{orderId}/confirm-received
        ↓
BidderController.confirmReceived()
        ↓
OrderService.confirmReceived()
        ├─► OrderRepository.findById(orderId)
        ├─► OrderValidator.validateConfirmReceived() -> Bắt buộc order.buyer.id == bidderId & order.status == SHIPPING
        ├─► order.setStatus(OrderStatus.COMPLETED)
        ├─► OrderRepository.save(order)
        └─► OrderMapper.toWonAuctionDTO() -> Trả về WonAuctionResponseDTO (200 OK)
```

---

### 🌐 PHÂN HỆ CÔNG KHẢI (PUBLIC MARKETPLACE & CATEGORIES)

#### Flow 18: Xem danh sách sản phẩm công khai trên sàn (`GET /v1/products`)

```text
GET /v1/products
        ↓
ProductController.getPublicProducts()
        ↓
ProductService.getPublicProducts()
        ├─► ProductRepository.findAllApprovedProductsSorted() -> Bộ lọc ProductStatus.APPROVED & AuctionStatus != CANCELLED
        └─► ProductResponseHelper.buildAll() -> Batch query N+1 & trả về List<ProductResponseDTO> (200 OK)
```

#### Flow 19: Xem chi tiết 1 sản phẩm (`GET /v1/products/{productId}`)

```text
GET /v1/products/{productId}
        ↓
ProductController.getProductWithAuctionById()
        ↓
ProductService.getProductWithAuctionById()
        ├─► ProductRepository.findById(productId) -> Lấy sản phẩm
        └─► ProductResponseHelper.build() -> Trả về ProductResponseDTO (200 OK)
```

#### Flow 20: Xem danh sách danh mục hoạt động (`GET /v1/categories`)

```text
GET /v1/categories
        ↓
CategoryController.getAllActiveCategories()
        ↓
CategoryService.getAllCategories()
        ├─► CategoryRepository.findByActiveTrue() -> Lọc danh mục active = true
        └─► Mapping sang List<CategoryResponseDTO> (200 OK)
```

---

### 🤖 TỰ ĐỘNG HÓA TẦNG NỀN (BACKGROUND AUTOMATION JOB)

#### Flow 21: Robot Scheduler quét tự động định kỳ 10 giây (`@Scheduled(cron = "*/10 * * * * *")`)

```text
AuctionScheduler.autoStartAuctions() & autoCloseExpiredAuctions() & autoCancelUnpaidOrders() (Chạy ngầm 10s/lần)
        │
        ├─► [Nhiệm vụ 1: Mở thầu đúng giờ]
        │     └─► UPDATE auctions SET status = 'RUNNING' WHERE status = 'SCHEDULED' AND startTime <= NOW()
        │
        ├─► [Nhiệm vụ 2: Chốt thầu hết giờ]
        │     ├─► Lấy danh sách auctions (status == RUNNING AND endTime <= NOW())
        │     ├─► Tìm Bid cao nhất cho từng phiên
        │     ├─► Xử lý loại thầu:
        │     │     ├─► ENGLISH: Gán winner = highestBid.bidder (dù giá nào)
        │     │     └─► RESERVE: Nếu highestBid.amount >= reservePrice -> Gán winner; Ngược lại winner = null
        │     ├─► Chuyển status = 'ENDED'
        │     └─► Nếu có winner -> Tự động sinh bản ghi Order (status = UNPAID, hạn thanh toán NOW() + 48h)
        │
        └─► [Nhiệm vụ 3: Phạt người bùng đơn quá 48h]
              ├─► Lấy danh sách orders (status == UNPAID AND paymentDeadline <= NOW())
              ├─► Đổi order.status = 'CANCELLED'
              ├─► Cộng strike phạt cho Buyer (strikesCount + 1)
              └─► Nếu strikesCount >= 3 -> Khóa tài khoản user.setBannedUntil(NOW() + 90 days)
```

---

## 8. Business Flow

### 1. Vòng đời toàn vẹn của Bài Đăng & Phiên Đấu Giá (Product & Auction Lifecycle)

```text
[Seller Đăng Bài] ──► Product (PENDING) + Auction (PENDING_APPROVAL)
                            │
                            ├──────────────────────────┐
                            ▼                          ▼
               Admin APPROVE               Admin REJECT (nhập lý do)
                            │                          │
              ┌─────────────┴─────────────┐            ▼
              ▼                           ▼     Product (REJECTED) + Auction (CANCELLED)
   (startTime <= NOW)           (startTime > NOW)
              │                           │
              ▼                           ▼
      Auction (RUNNING)          Auction (SCHEDULED)
              │                           │
              │              (Scheduler autoStart 10s)
              │                           │
              └─────────────► ◄───────────┘
                            │
                            ├──► Bidder Đặt giá (Proxy Bidding / Soft-close Anti-sniping)
                            ├──► Hoặc Bidder Mua Ngay (Auction -> ENDED & Sinh Order UNPAID)
                            │
              (Scheduler autoEnd khi hết giờ)
                            │
                            ▼
              Auction (ENDED) + Sinh Order (UNPAID, deadline 48h)
                            │
          (Nếu BUY_NOW / bài không có ai đặt giá hết 30 ngày)
                            │
                            ▼
                     Auction (EXPIRED)
                            │
                 (Seller bấm RELIST)
                            │
                            ▼
      Auction khôi phục RUNNING công khai (Reset 30 ngày hiển thị)
```

### 2. Luồng Hậu Đấu Giá & Thanh Toán Đơn Hàng (Post-Auction Order & Settlement Flow)

```text
Phiên Đấu Giá Kết Thúc / Mua Ngay
                ↓
    Order được tạo tự động (Status = UNPAID, paymentDeadline = NOW + 48h)
                │
                ├──────────────────────────────────────────┐
                ▼                                          ▼
   Người Mua Checkout & Thanh toán                 Bùng hàng quá 48h
  (Điền Địa chỉ, SĐT, PaymentMethod)           (Scheduler quét auto-cancel)
                │                                          │
                ▼                                          ▼
     Order Status = PAID                      Order Status = CANCELLED
  (Tiền nằm ở Escrow tạm giữ)                 Gậy vi phạm strike++ (+1)
                │                                          │
                ▼                                (Strikes >= 3?)
    Seller nhập Mã Vận Đơn                                 │
                │                                          ▼
                ▼                              Khóa tài khoản BannedUntil = NOW + 90 ngày
    Order Status = SHIPPING
                │
                ▼
   Người Mua bấm [Xác Nhận Đã Nhận Hàng]
                │
                ▼
    Order Status = COMPLETED
  (Giải ngân tiền cho Seller)
```

---

## 9. Entity Relationship (ERD)

```text
Category (Danh mục phân cấp)
   ▲ parentId (Self-reference)
   │
   └─── User (Seller/Buyer/Admin)
          │
          ├─── Product ─────────── ProductImage (1 - 20 ảnh)
          │      │
          │      └─── Auction ─── Bid (Nhật ký Audit Trail)
          │             │
          └─────────────┴─── Order ─── Payment (Giao dịch VNPAY/Wallet)
```

### Quan hệ JPA Entity cụ thể:
- **`User`** `1 ── N` **`Product`** (trường `seller` trong `Product`).
- **`Category`** `1 ── N` **`Product`** (trường `category` trong `Product`).
- **`Product`** `1 ── N` **`ProductImage`** (`mappedBy = "product"` trong `Product`).
- **`Product`** `1 ── 1` **`Auction`** (`product_id` FK trong `Auction`).
- **`Auction`** `1 ── N` **`Bid`** (`auction_id` FK trong `Bid`).
- **`User`** `1 ── N` **`Bid`** (`bidder_id` FK trong `Bid`).
- **`Auction`** `1 ── 1` **`Order`** (`auction_id` FK unique trong `Order`).
- **`User`** `1 ── N` **`Order`** (vừa làm `buyer`, vừa làm `seller`).
- **`Order`** `1 ── N` **`Payment`** (`order_id` FK trong `Payment`).

---

## 10. DTO Flow

### Chiều Request Inbound:
```text
HTTP Request (JSON snake_case / Multipart Form)
        ↓
Jackson ObjectMapper (phân giải snake_case -> camelCase Java)
        ↓
Request DTO (@Valid Spring Validation)
        ↓
Service Layer & Validation Layer
        ↓
MapStruct Mapper (toEntity)
        ↓
JPA Entity
        ↓
Repository & Database (PostgreSQL)
```

### Chiều Response Outbound:
```text
Database (PostgreSQL)
        ↓
JPA Entity
        ↓
MapStruct Mapper / Helper Layer (ProductResponseHelper batch query mapping)
        ↓
Response DTO
        ↓
Jackson ObjectMapper (chuyển camelCase Java -> JSON snake_case)
        ↓
HTTP Response (JSON)
```

- **MapStruct Setup:** Đặt ở package `mapper` với `@Mapper(componentModel = "spring")`. `ProductMapper` dùng `uses = {BidMapper.class}` để gọi hàm `@Named("maskUsername")` ẩn danh tên người thắng cuộc.

---

## 11. Exception Flow

### Mô hình xử lý Exception tập trung:

```text
Service / Validator / Helper
            ↓
  throw ApplicationException(ErrorCode.XYZ)
            ↓
  GlobalExceptionHandler (@RestControllerAdvice)
            ↓
  Bắt ApplicationException -> Lấy ErrorCode (code, message, httpStatus)
            ↓
  Tạo ErrorResponse (code, message, status)
            ↓
  Trả về HTTP Status Code chuẩn (400, 403, 404, 500) kèm JSON ErrorResponse
```

### Xử lý Validation Exception:
Nếu Client gửi sai format DTO, `@Valid` ném `MethodArgumentNotValidException`, `GlobalExceptionHandler.handleValidationExceptions()` gom tất cả tin nhắn lỗi trường và trả về `HTTP 400 Bad Request` với mã `1000`.

---

## 12. Security Flow

- **Cấu hình hiện tại:** `✅ Confirmed` Trong [SecurityConfig.java](file:///home/duong/Projects/Backend/DuAnTrainning/src/main/java/DuAnTrainning/AuctionSystem/config/SecurityConfig.java):
  ```java
  http
      .cors(cors -> cors.configurationSource(corsConfigurationSource()))
      .csrf(AbstractHttpConfigurer::disable)
      .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
  ```
- **Xác thực / Phân quyền:** `⚠️ Inferred` Hệ thống hiện tại nhận `sellerId`, `bidderId` trực tiếp qua `@PathVariable` hoặc `@RequestParam` để kiểm tra quyền chính chủ (`check seller.id == sellerId`). Chưa cài đặt JWT Token Filter hay Spring Security Session.

---

## 13. Scheduler & Cross-Cutting Components

### 1. Robot điều phối ngầm (`AuctionScheduler`)
- Kích hoạt bằng `@EnableScheduling` tại `AuctionSystemApplication`.
- Chạy định kỳ `fixedRate = 10000ms` (10 giây/lần):
  1. `autoStartAuctions`: Chuyển `SCHEDULED` → `RUNNING` khi `startTime <= NOW` và bài đăng `APPROVED`.
  2. `autoExpireBuyNowAuctions`: Chuyển `RUNNING` → `EXPIRED` cho bài Mua Ngay hết hạn 30 ngày.
  3. `processEndedAuctions`: Khóa phiên `ENDED`, chốt `winner`, tự sinh bản ghi `Order` (`UNPAID` + deadline 48h).
  4. `backfillMissingOrders`: Self-healing bổ sung đơn hàng bị thiếu cho phiên `ENDED` có `winner`.
  5. `processExpiredUnpaidOrders`: Quét đơn `UNPAID` quá 48h -> Chuyển `CANCELLED`, tăng `unpaidStrikeCount` cho Buyer, nếu `strikes >= 3` -> Khóa cấm đấu giá `bannedUntil = NOW + 90 ngày`.

### 2. Quản lý Ảnh Cloudinary & Transaction Sync
- Tải ảnh song song sử dụng `CompletableFuture.supplyAsync()`.
- **Dọn dẹp khi Rollback:** Nếu lưu DB bị lỗi, `TransactionSynchronizationManager.registerSynchronization(afterCompletion)` sẽ tự xóa toàn bộ ảnh vừa lỡ upload trên Cloudinary.
- **Dọn dẹp sau Commit:** Khi xóa/sửa ảnh, public_id được xóa trên Cloudinary *sau khi* DB Commit thành công (`afterCommit`).

### 3. Tối ưu Hóa Batch Query Chống N+1 Query
- Trong `ProductResponseHelper.buildAll()`, thay vì query `Auction` và `ProductImage` cho từng `Product` trong vòng lặp, hệ thống gom toàn bộ Product ID và bắn 2 câu SQL Batch (`findByProduct_IdIn` và `findByProductIdInOrderBy...`) nhóm lại trong RAM, giảm từ `2N + 1` câu query xuống đúng 3 câu query.

---

## 14. Feature-by-Feature Map

### FEATURE 1: PRODUCT (Quản lý Bài Đăng Sản Phẩm)
- **Controllers:** `SellerProductController`, `AdminProductController`, `ProductController`
- **Services:** `ProductService`, `CloudinaryService`
- **Validators:** `ProductImageValidator`, `AuctionValidator`
- **Mappers:** `ProductMapper`, `AuctionMapper`, `ProductImageMapper`
- **Repositories:** `ProductRepository`, `ProductImageRepository`, `AuctionRepository`
- **Entities:** `Product`, `ProductImage`, `Auction`

### FEATURE 2: AUCTION & BIDDING (Đấu Giá & Proxy Bidding)
- **Controllers:** `AuctionBiddingController`
- **Services:** `BiddingService`
- **Helpers:** `ProxyBiddingEngineHelper`, `BidStepCalculatorHelper`, `BidResponseHelper`
- **Validators:** `BidValidator`
- **Mappers:** `BidMapper`
- **Repositories:** `AuctionRepository`, `BidRepository`
- **Entities:** `Auction`, `Bid`, `User`

### FEATURE 3: ORDER & PAYMENT (Hậu Đấu Giá & Thanh Toán)
- **Controllers:** `BidderController`, `SellerProductController`
- **Services:** `OrderService`
- **Helpers:** `OrderResponseHelper`
- **Validators:** `OrderValidator`
- **Mappers:** `OrderMapper`
- **Repositories:** `OrderRepository`, `PaymentRepository`, `UserRepository`
- **Entities:** `Order`, `Payment`, `User`

### FEATURE 4: SCHEDULER & BAN SYSTEM (Tự Động Hóa & Phạt Bùng Hàng)
- **Services:** `AuctionScheduler`
- **Repositories:** `AuctionRepository`, `BidRepository`, `OrderRepository`, `UserRepository`
- **Entities:** `Auction`, `Order`, `User`

---

## 15. Reading Guide

Dành cho developer mới gia nhập dự án, hãy đọc source code theo từng nghiệp vụ dưới đây để nhanh chóng làm quen:

### 📖 Muốn hiểu nghiệp vụ Đăng Bài & Duyệt Bài (Product Domain)
1. Đọc DTO: [ProductRequestDTO.java](file:///home/duong/Projects/Backend/DuAnTrainning/src/main/java/DuAnTrainning/AuctionSystem/dto/request/ProductRequestDTO.java)
2. Đọc Validator: [ProductImageValidator.java](file:///home/duong/Projects/Backend/DuAnTrainning/src/main/java/DuAnTrainning/AuctionSystem/validator/ProductImageValidator.java) -> [AuctionValidator.java](file:///home/duong/Projects/Backend/DuAnTrainning/src/main/java/DuAnTrainning/AuctionSystem/validator/AuctionValidator.java)
3. Đọc Cloudinary Service: [CloudinaryService.java](file:///home/duong/Projects/Backend/DuAnTrainning/src/main/java/DuAnTrainning/AuctionSystem/service/CloudinaryService.java)
4. Đọc Service chính: [ProductService.java](file:///home/duong/Projects/Backend/DuAnTrainning/src/main/java/DuAnTrainning/AuctionSystem/service/ProductService.java)
5. Đọc Helper chống N+1: [ProductResponseHelper.java](file:///home/duong/Projects/Backend/DuAnTrainning/src/main/java/DuAnTrainning/AuctionSystem/service/helper/ProductResponseHelper.java)
6. Đọc Controllers: [SellerProductController.java](file:///home/duong/Projects/Backend/DuAnTrainning/src/main/java/DuAnTrainning/AuctionSystem/controller/SellerProductController.java) -> [AdminProductController.java](file:///home/duong/Projects/Backend/DuAnTrainning/src/main/java/DuAnTrainning/AuctionSystem/controller/AdminProductController.java)

### 📖 Muốn hiểu nghiệp vụ Đấu Giá Tự Động & Mua Ngay (Bidding Domain)
1. Đọc DTO: [BidRequestDTO.java](file:///home/duong/Projects/Backend/DuAnTrainning/src/main/java/DuAnTrainning/AuctionSystem/dto/request/BidRequestDTO.java)
2. Đọc Validator: [BidValidator.java](file:///home/duong/Projects/Backend/DuAnTrainning/src/main/java/DuAnTrainning/AuctionSystem/validator/BidValidator.java)
3. Đọc tính bước giá: [BidStepCalculatorHelper.java](file:///home/duong/Projects/Backend/DuAnTrainning/src/main/java/DuAnTrainning/AuctionSystem/service/helper/BidStepCalculatorHelper.java)
4. Đọc thuật toán Proxy Bidding: [ProxyBiddingEngineHelper.java](file:///home/duong/Projects/Backend/DuAnTrainning/src/main/java/DuAnTrainning/AuctionSystem/service/helper/ProxyBiddingEngineHelper.java)
5. Đọc Service chính: [BiddingService.java](file:///home/duong/Projects/Backend/DuAnTrainning/src/main/java/DuAnTrainning/AuctionSystem/service/BiddingService.java)
6. Đọc Controller: [AuctionBiddingController.java](file:///home/duong/Projects/Backend/DuAnTrainning/src/main/java/DuAnTrainning/AuctionSystem/controller/AuctionBiddingController.java)

### 📖 Muốn hiểu nghiệp vụ Đơn Hàng & Thanh Toán (Order & Payment Domain)
1. Đọc Entities: [Order.java](file:///home/duong/Projects/Backend/DuAnTrainning/src/main/java/DuAnTrainning/AuctionSystem/entity/Order.java) -> [Payment.java](file:///home/duong/Projects/Backend/DuAnTrainning/src/main/java/DuAnTrainning/AuctionSystem/entity/Payment.java)
2. Đọc DTO: [CheckoutRequestDTO.java](file:///home/duong/Projects/Backend/DuAnTrainning/src/main/java/DuAnTrainning/AuctionSystem/dto/request/CheckoutRequestDTO.java) -> [ShipOrderRequestDTO.java](file:///home/duong/Projects/Backend/DuAnTrainning/src/main/java/DuAnTrainning/AuctionSystem/dto/request/ShipOrderRequestDTO.java)
3. Đọc Validator: [OrderValidator.java](file:///home/duong/Projects/Backend/DuAnTrainning/src/main/java/DuAnTrainning/AuctionSystem/validator/OrderValidator.java)
4. Đọc Service chính: [OrderService.java](file:///home/duong/Projects/Backend/DuAnTrainning/src/main/java/DuAnTrainning/AuctionSystem/service/OrderService.java)
5. Đọc Controller: [BidderController.java](file:///home/duong/Projects/Backend/DuAnTrainning/src/main/java/DuAnTrainning/AuctionSystem/controller/BidderController.java)

### 📖 Muốn hiểu Robot Chạy Ngầm & Phạt Bùng Đơn (Background Scheduler Domain)
1. Đọc cấu hình Scheduler: [DuAnTrainningApplication.java](file:///home/duong/Projects/Backend/DuAnTrainning/src/main/java/DuAnTrainning/AuctionSystem/DuAnTrainningApplication.java)
2. Đọc Robot xử lý: [AuctionScheduler.java](file:///home/duong/Projects/Backend/DuAnTrainning/src/main/java/DuAnTrainning/AuctionSystem/service/AuctionScheduler.java)
3. Đọc Custom Repository Bulk Queries: [AuctionRepository.java](file:///home/duong/Projects/Backend/DuAnTrainning/src/main/java/DuAnTrainning/AuctionSystem/repository/AuctionRepository.java) -> [OrderRepository.java](file:///home/duong/Projects/Backend/DuAnTrainning/src/main/java/DuAnTrainning/AuctionSystem/repository/OrderRepository.java)
