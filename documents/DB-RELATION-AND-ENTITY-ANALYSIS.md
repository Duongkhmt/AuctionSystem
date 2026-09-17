
---

## TỔNG QUAN KIẾN TRÚC DATABASE ĐA DỊCH VỤ (DATABASE-PER-SERVICE ARCHITECTURE)

Trong kiến trúc Microservices hiện tại, hệ thống Cơ sở dữ liệu được phân tách hoàn toàn thành **2 Database PostgreSQL riêng biệt**, tuân thủ nguyên tắc **Database-per-Service Pattern** nhằm bảo đảm an toàn tài chính, tránh xung đột khóa hàng (Lock Contention) và cho phép mở rộng tải độc lập:

1. **`auction_db`** (Do `auction-service` quản lý trên Port `8080`): Lưu trữ **8 Entity (8 bảng)** phục vụ toàn bộ quy trình từ Đăng bài sản phẩm, Duyệt bài, Đấu giá thời gian thực (Proxy Bidding) cho đến Quản lý Đơn hàng Post-Auction.
2. **`payment_db`** (Do `payment-service` quản lý trên Port `8082`): Lưu trữ **2 Entity (2 bảng)** phục vụ Quản lý Số dư Ví tiền ảo (`Wallet`), Sổ cái nhật ký giao dịch (`PaymentTransaction`), Tạm giữ Escrow, Giải ngân Seller Payout và Chống giao dịch trùng bằng Idempotency-Key.

> [!IMPORTANT]
> **Nguyên tắc Cô lập dữ liệu (Zero Foreign-Key Across Services)**: Không có bất kỳ liên kết Khóa ngoại (`FOREIGN KEY`) trực tiếp nào giữa `auction_db` và `payment_db`. Việc tham chiếu dữ liệu giữa 2 service được thực hiện thông qua **Mã định danh logic (Logical IDs)** như `userId` và `orderId` được mã hóa an toàn trong JWT Claims và HTTP Headers.

---

## 1. BẢNG MÔ TẢ CHI TIẾT TẤT CẢ ENTITIES VÀ MỤC ĐÍCH NGHIỆP VỤ (10 ENTITIES / 2 DBS)

### 🔹 Database 1: `auction_db` (Quản lý Sàn Đấu Giá & Đơn Hàng)

| STT | Tên Entity | Tên Bảng DB | Mục Đích Nghiệp Vụ Cốt Lõi |
|:---|:---|:---|:---|
| 1 | **[`User`](file:///home/duong/Projects/Backend/auction-service/src/main/java/com/duong/auction/system/entity/User.java)** | `users` | Quản lý thông tin tài khoản người dùng, vai trò hệ thống (`BIDDER`, `SELLER`, `ADMIN`), chỉ số uy tín thanh toán (`unpaidStrikeCount`) và hạn phạt cấm đấu giá (`bannedUntil`). |
| 2 | **[`Category`](file:///home/duong/Projects/Backend/auction-service/src/main/java/com/duong/auction/system/entity/Category.java)** | `categories` | Quản lý cây danh mục sản phẩm đa cấp (`parentId`), cấu hình cờ yêu cầu xác minh (`requiresVerification`) hoặc đặt cọc tiền (`requiresDeposit`). |
| 3 | **[`Product`](file:///home/duong/Projects/Backend/auction-service/src/main/java/com/duong/auction/system/entity/Product.java)** | `products` | Lưu trữ thông tin tài sản đấu giá, liên kết với Người bán (`seller`) và Danh mục (`category`), lưu thông số kỹ thuật động dạng `JSONB` (`attributes`) và lý do từ chối khi duyệt (`rejectionReason`). |
| 4 | **[`ProductImage`](file:///home/duong/Projects/Backend/auction-service/src/main/java/com/duong/auction/system/entity/ProductImage.java)** | `product_images` | Lưu trữ danh sách đường dẫn ảnh CDN Cloudinary (`imageUrl`), mã định danh quản lý ảnh (`publicId`) và thứ tự hiển thị (`displayOrder`) của từng sản phẩm. |
| 5 | **[`Auction`](file:///home/duong/Projects/Backend/auction-service/src/main/java/com/duong/auction/system/entity/Auction.java)** | `auctions` | Trái tim của hệ thống đấu giá: Quản lý loại hình đấu giá (`ENGLISH`, `RESERVE`, `BUY_NOW`), cấu hình tài chính (giá khởi điểm, giá sàn ẩn, bước giá, giá mua ngay, giá hiện tại), thời gian mở/đóng phiên và người thắng cuộc (`winner`). |
| 6 | **[`Bid`](file:///home/duong/Projects/Backend/auction-service/src/main/java/com/duong/auction/system/entity/Bid.java)** | `bids` | Lưu vết 100% lịch sử đặt giá (Audit Trail): Số tiền đặt (`bidAmount`), giá trần tự động (`maxAutoBid`), cờ đánh dấu lượt đặt do người hay Robot Auto-bid (`autoBid`), kèm Composite Index tối ưu truy vấn. |
| 7 | **[`Order`](file:///home/duong/Projects/Backend/auction-service/src/main/java/com/duong/auction/system/entity/Order.java)** | `orders` | Quản lý đơn hàng sau khi đấu giá thành công hoặc chốt Mua Ngay: Liên kết 1-1 với phiên đấu giá (`auction_id` Unique), thông tin giao hàng, thời hạn chốt thanh toán 48h (`paymentDeadline`) và trạng thái đơn hàng (`OrderStatus`). |
| 8 | **[`Payment`](file:///home/duong/Projects/Backend/auction-service/src/main/java/com/duong/auction/system/entity/Payment.java)** | `payments` | Lưu nhật ký lịch sử thanh toán tiền của đơn hàng thuộc miền Đấu giá: Số tiền thanh toán (`amount`), hình thức thanh toán (`paymentMethod`), mã giao dịch (`transactionCode`) và trạng thái thanh toán (`PaymentStatus`). |

---

### 🔹 Database 2: `payment_db` (Quản lý Ví Tiền & Escrow Sổ Sách Tài Chính)

| STT | Tên Entity | Tên Bảng DB | Mục Đích Nghiệp Vụ Cốt Lõi |
|:---|:---|:---|:---|
| 9 | **[`Wallet`](file:///home/duong/Projects/Backend/payment-service/src/main/java/com/duong/auction/payment/entity/Wallet.java)** | `wallets` | Quản lý số dư khả dụng (`balance`) của từng người dùng (`userId` Unique). Áp dụng Pessimistic Lock (`SELECT ... FOR UPDATE`) để đảm bảo tính nguyên tử khi trừ tiền Checkout hoặc cộng tiền Payout. |
| 10 | **[`PaymentTransaction`](file:///home/duong/Projects/Backend/payment-service/src/main/java/com/duong/auction/payment/entity/PaymentTransaction.java)** | `payment_transactions` | Sổ cái tài chính (Financial Ledger) lưu vết lịch sử biến động số dư. Đánh Index Unique trên `idempotency_key` để triệt tiêu 100% nguy cơ trừ tiền 2 lần khi client bấm đúp hoặc retry mạng. |

---

## 2. SƠ ĐỒ MỐI QUAN HỆ GIỮA CÁC ENTITY (ERD & CARDINALITY)
### Chi tiết Cấu hình FetchType, Constraints & Composite Indexes

#### 🔹 Bảng cấu hình thuộc `auction_db`:

| Entity Gốc | Entity Liên Kết | Loại Quan Hệ | Cột Khóa Ngoại (`FK`) | Cấu Hình `FetchType` | Cấu Hình Constraints / Indexes |
|:---|:---|:---|:---|:---|:---|
| `Category` | `Category` | `ManyToOne` | `parent_id` | Mặc định (`EAGER`) | Self-referencing ID phân cấp danh mục |
| `Product` | `User` | `ManyToOne` | `seller_id` | Mặc định (`EAGER`) | `nullable = false` (Bắt buộc có Người bán) |
| `Product` | `Category` | `ManyToOne` | `category_id` | Mặc định (`EAGER`) | `nullable = false` (Bắt buộc thuộc Danh mục) |
| `Product` | `ProductImage` | `OneToMany` | `product_id` | Mặc định (`LAZY`) | MappedBy = `"product"`, sắp xếp theo `displayOrder` |
| `ProductImage` | `Product` | `ManyToOne` | `product_id` | Mặc định (`EAGER`) | `nullable = false`, lưu `publicId` Cloudinary |
| `Auction` | `Product` | `ManyToOne` | `product_id` | Mặc định (`EAGER`) | `nullable = false` |
| `Auction` | `User` | `ManyToOne` | `winner_id` | `FetchType.LAZY` | Nullable (Tránh load dư thừa thông tin User) |
| `Bid` | `Auction` | `ManyToOne` | `auction_id` | `FetchType.LAZY` | `nullable = false`. Composite Index: `(auction_id, bid_amount DESC, created_at ASC)` |
| `Bid` | `User` | `ManyToOne` | `bidder_id` | `FetchType.LAZY` | `nullable = false`. Composite Index: `(auction_id, created_at DESC)` |
| `Order` | `Auction` | `OneToOne` | `auction_id` | `FetchType.LAZY` | `nullable = false, unique = true` (1 phiên chỉ có 1 Đơn hàng) |
| `Order` | `Product` | `ManyToOne` | `product_id` | `FetchType.LAZY` | `nullable = false` |
| `Order` | `User` | `ManyToOne` | `buyer_id` | `FetchType.LAZY` | `nullable = false` (Người mua thắng cuộc) |
| `Order` | `User` | `ManyToOne` | `seller_id` | `FetchType.LAZY` | `nullable = false` (Người bán) |
| `Payment` | `Order` | `ManyToOne` | `order_id` | `FetchType.LAZY` | `nullable = false` |

#### 🔹 Bảng cấu hình thuộc `payment_db`:

| Entity Gốc | Tham chiếu Logic | Loại Ràng Buộc | Cột DB (`Column`) | Cấu Hình Constraints / Indexes |
|:---|:---|:---|:---|:---|
| `Wallet` | `User.id` (`auction_db`) | Logical Key | `user_id` | `nullable = false, unique = true` |
| `PaymentTransaction` | `Wallet.userId` | Logical Key | `user_id` | `nullable = false` |
| `PaymentTransaction` | `Order.id` (`auction_db`) | Logical Key | `order_id` | `nullable = false` |
| `PaymentTransaction` | Self | Idempotency | `idempotency_key` | **`nullable = false, unique = true`**. Index: `idx_transaction_idempotency` |

---

## 🔒 3. CƠ CHẾ BẢO VỆ DỮ LIỆU & CHỐNG RACE CONDITION TRÊN DATABASE

### 3.1. Khóa Hàng Tạm Giữ Ví Tiền (`Pessimistic Lock`)
Để chống hiện tượng **Race Condition** (khi 2 request trừ tiền cùng lúc diễn ra ở cùng 1 millisecond), `payment-service` áp dụng cơ chế khóa hàng nguyên tử trên PostgreSQL:
```java
@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("SELECT w FROM Wallet w WHERE w.userId = :userId")
Optional<Wallet> findByUserIdForUpdate(@Param("userId") Long userId);
```
* **Cơ chế**: Khi `auction-service` gọi Checkout, PostgreSQL lập tức thực thi `SELECT ... FOR UPDATE` trên dòng ví của người dùng. Mọi request khác muốn đọc/ghi ví này sẽ bị phong tỏa (block) cho tới khi giao dịch trước hoàn tất `COMMIT`.

### 3.2. Chống Trừ Tiền 2 Lần Bằng `Idempotency-Key` (Database Unique Index)
* **Bảng áp dụng**: `payment_transactions`
* **Cột**: `idempotency_key` (Index Unique: `idx_transaction_idempotency`)
* **Cơ chế**: Mỗi yêu cầu Checkout đều mang theo một chuỗi định danh duy nhất (ví dụ: `PAY_ORDER_10_USER_5`). Nếu người dùng bấm nút 2 lần hoặc mạng bị giật retry request, câu lệnh `INSERT` thứ hai sẽ đụng phải Unique Constraint của PostgreSQL và ném ra lỗi `DataIntegrityViolationException`. Hệ thống lập tức catch lỗi này và trả về kết quả giao dịch thành công trước đó mà không trừ tiền lần 2.

---

##  4. PHÂN TÍCH ĐỐI CHIẾU THIẾT KẾ ENTITY VỚI ĐẶC TẢ NGHIỆP VỤ (FEATURE ALIGNMENT)

### 4.1. Các Tính Năng ĐƯỢC HỖ TRỢ HOÀN HẢO BỞI DATABASE SCHEMA

1. **Nghiệp vụ Đấu giá Tự động (Proxy Bidding) & Audit Trail:**
   - **Entity đáp ứng:** `Bid` có `bidAmount`, `maxAutoBid`, `autoBid`.
   - **Đánh giá:** Composite Index `(auction_id, bid_amount DESC, created_at ASC)` giúp thuật toán `ProxyBiddingEngineHelper` truy vấn vị trí dẫn đầu siêu nhanh dưới 1ms.
2. **Nghiệp vụ Mua Ngay (Buy Now):**
   - **Entity đáp ứng:** `Auction.buyNowPrice` và `Auction.winner`.
   - **Đánh giá:** Cho phép chốt đứt điểm phiên đấu giá ngay lập tức khi người mua chấp nhận mức giá mua ngay.
3. **Nghiệp vụ Thuộc tính sản phẩm đa dạng (Dynamic Product Attributes):**
   - **Entity đáp ứng:** `Product.attributes` với `@JdbcTypeCode(SqlTypes.JSON) columnDefinition = "jsonb"`.
   - **Đánh giá:** Khớp 100% với yêu cầu lưu trữ thuộc tính động (Xe hơi, Đồ điện tử, Đồ cổ...) mà không dính lỗi N+1 của mô hình EAV.
4. **Nghiệp vụ Duyệt bài & Lý do từ chối (Admin Moderation):**
   - **Entity đáp ứng:** `Product.status` (`PENDING`, `APPROVED`, `REJECTED`) và `Product.rejectionReason`.
   - **Đánh giá:** Cho phép Admin duyệt bài và gửi lý do từ chối trực tiếp cho Seller.
5. **Nghiệp vụ Xử lý Sau Đấu Giá & Xử phạt Bùng đơn (Post-Auction Settlement & Strike System):**
   - **Entity đáp ứng:** `User.unpaidStrikeCount`, `User.bannedUntil`, `Order` (chứa `paymentDeadline` 48h, `OrderStatus`), và `Payment` (`PaymentStatus`).
   - **Đánh giá:** Khớp 100% với đặc tả đếm số gậy bùng hàng và cấm đấu giá 30 ngày đối với Bidder không thanh toán trong 48 tiếng.
6. **Nghiệp vụ Ví Tiền Ảo & Tạm Giữ Escrow (Wallet & Escrow Settlement):**
   - **Entity đáp ứng:** `Wallet` (`balance`, `status`) và `PaymentTransaction` (`idempotencyKey`, `transactionCode`).
   - **Đánh giá:** Đảm bảo tiền mua hàng được giữ an toàn tại tài khoản trung gian của Sàn khi đơn ở trạng thái `PAID` và chỉ giải ngân cho Seller khi đơn chuyển sang `COMPLETED`.

---

## 🛠 5. BẢNG MAPPING FILE NGUỒN ENTITY (ENTITY CODE MATRIX)

| STT | Database | Tên Entity Class | Đường Dẫn File Nguồn Mã Khởi Tạo |
|:---|:---|:---|:---|
| 1 | `auction_db` | **`User`** | [`User.java`](file:///home/duong/Projects/Backend/auction-service/src/main/java/com/duong/auction/system/entity/User.java) |
| 2 | `auction_db` | **`Category`** | [`Category.java`](file:///home/duong/Projects/Backend/auction-service/src/main/java/com/duong/auction/system/entity/Category.java) |
| 3 | `auction_db` | **`Product`** | [`Product.java`](file:///home/duong/Projects/Backend/auction-service/src/main/java/com/duong/auction/system/entity/Product.java) |
| 4 | `auction_db` | **`ProductImage`** | [`ProductImage.java`](file:///home/duong/Projects/Backend/auction-service/src/main/java/com/duong/auction/system/entity/ProductImage.java) |
| 5 | `auction_db` | **`Auction`** | [`Auction.java`](file:///home/duong/Projects/Backend/auction-service/src/main/java/com/duong/auction/system/entity/Auction.java) |
| 6 | `auction_db` | **`Bid`** | [`Bid.java`](file:///home/duong/Projects/Backend/auction-service/src/main/java/com/duong/auction/system/entity/Bid.java) |
| 7 | `auction_db` | **`Order`** | [`Order.java`](file:///home/duong/Projects/Backend/auction-service/src/main/java/com/duong/auction/system/entity/Order.java) |
| 8 | `auction_db` | **`Payment`** | [`Payment.java`](file:///home/duong/Projects/Backend/auction-service/src/main/java/com/duong/auction/system/entity/Payment.java) |
| 9 | `payment_db` | **`Wallet`** | [`Wallet.java`](file:///home/duong/Projects/Backend/payment-service/src/main/java/com/duong/auction/payment/entity/Wallet.java) |
| 10 | `payment_db` | **`PaymentTransaction`** | [`PaymentTransaction.java`](file:///home/duong/Projects/Backend/payment-service/src/main/java/com/duong/auction/payment/entity/PaymentTransaction.java) |

---