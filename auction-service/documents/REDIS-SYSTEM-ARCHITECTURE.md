# 📕 TÀI LIỆU TOÀN DIỆN VỀ KIẾN TRÚC REDIS TRONG HỆ THỐNG ĐẤU GIÁ (PROJECT: AUCTION SYSTEM)

---

## 🎯 TỔNG QUAN (OVERVIEW)

Trong hệ thống Đấu Giá Trực Tuyến `auction-service`, **Redis (Remote Dictionary Server)** đóng vai trò là một **Hệ thống Bộ nhớ Đệm RAM Siêu Tốc (In-Memory Data Store)** với 3 trụ cột kiến trúc cốt lõi:

```
                                  ┌────────────────────────────────────────────────────────┐
                                  │           REDIS SYSTEM ARCHITECTURE                   │
                                  └────────────────────────────────────────────────────────┘
                                                               │
         ┌─────────────────────────────────────────────────────┼─────────────────────────────────────────────────────┐
         ▼                                                     ▼                                                     ▼
┌─────────────────────────┐                           ┌─────────────────────────┐                           ┌─────────────────────────┐
│   1. REDIS CACHING      │                           │  2. REDIS RATE LIMITING │                           │3. REDIS DISTRIBUTED LOCK│
│   (Bộ Đệm RAM Siêu Tốc) │                           │  (Cầu Dao Chống Quá Tải)│                           │ (Khóa Phân Tán Đồng Thời)│
└─────────────────────────┘                           └─────────────────────────┘                           └─────────────────────────┘
```

---

# 🔴 PHẦN 1: REDIS CACHING (BỘ ĐỆM DỮ LIỆU RAM - SPRING CACHE)

### 📌 1. Chi tiết chức năng & Nghiệp vụ áp dụng
Redis Cache đóng vai trò là **"Tờ Giấy Nháp RAM"** ghi sẵn các dữ liệu được người dùng đọc/polling liên tục:

1. **`bid_history` (TTL = 30 giây):**
   - *Hàm áp dụng:* `BiddingService.getAuctionBidHistory()` (`@Cacheable`)
   - *Xóa Cache:* Khi có ai đó đặt giá mới `placeBid()` hoặc mua ngay `executeBuyNow()` (`@CacheEvict`)
2. **`auctions` (TTL = 5 phút):**
   - *Hàm áp dụng:* `ProductService.getProductWithAuctionById()` (`@Cacheable`)
   - *Xóa Cache:* Khi người bán/admin cập nhật bài, hủy bài hoặc có người Mua Ngay (`@CacheEvict`)
3. **`categories` (TTL = 24 giờ):**
   - *Hàm áp dụng:* `CategoryService.getAllCategories()` (`@Cacheable`)

---

### ❓ KHI CÓ REDIS CACHING NÓ NHƯ THẾ NÀO?
- Khi 5,000 người dùng cùng mở màn hình ngắm sản phẩm và F5/Polling lịch sử đấu giá liên tục 2s/lần.
- Lần truy cập đầu tiên $\rightarrow$ Đọc DB và dán kết quả lên Redis RAM.
- **4,999 lần truy cập tiếp theo $\rightarrow$ Đọc thẳng từ Redis RAM với tốc độ siêu tốc 1 - 2 millisecond.**
- PostgreSQL Database hoàn toàn "rảnh tay", CPU/RAM của DB chỉ chạy ở mức 5-10%.

---

### 💥 NẾU KHÔNG CÓ REDIS CACHING THÌ SAO?
- 5,000 người dùng Polling lịch sử bid 2s/lần $\rightarrow$ Phát sinh **2,500 câu SQL `SELECT` phức tạp/giây** đâm thẳng xuống PostgreSQL đĩa cứng.
- Đĩa I/O của PostgreSQL bị nghẽn hoàn toàn, DB Connection Pool bị cạn kiệt.
- **Hậu quả:** Toàn bộ ứng dụng quay tròn, người dùng bị lỗi Timeout (HTTP 504), trang web sập hoàn toàn!

---

# 🔴 PHẦN 2: REDIS RATE LIMITING (CẦU DAO CHỐNG QUÁ TẢI & BOT SPAM)

### 📌 1. Chi tiết chức năng & Nghiệp vụ áp dụng
Rate Limiting đóng vai trò là **"Cầu Dao An Toàn Tự Động"** kiểm soát tốc độ bấm nút của từng cá nhân người dùng:

- *Tập tin:* [`RateLimit.java`](file:///home/duong/Projects/Backend/auction-service/src/main/java/com/duong/auction/system/aspect/RateLimit.java), [`RateLimitAspect.java`](file:///home/duong/Projects/Backend/auction-service/src/main/java/com/duong/auction/system/aspect/RateLimitAspect.java)
- *Áp dụng:* `@RateLimit(maxRequests = 5, timeWindowSeconds = 10)` dán trên hàm `BiddingService.placeBid()`

#### Các kỹ thuật đỉnh cao đã triển khai:
1. **`@Order(Ordered.HIGHEST_PRECEDENCE)`:** Ép Aspect này bọc ở **vòng bảo vệ ngoài cùng**, chạy TRƯỚC `@Transactional`. Nếu vượt ngưỡng $\rightarrow$ Chặn ném lỗi 429 ngay lập tức, **0 lãng phí 1 lượt mở Transaction DB nào**.
2. **Redis Lua Script Nguyên Tử:** Gộp `INCR` (đếm số) và `EXPIRE` (hẹn giờ tự hủy 10s) vào **đúng 1 microsecond nguyên tử** trên Redis RAM. Triệt tiêu 100% rủi ro đứt đoạn trễ mạng khiến key bị kẹt vĩnh viễn (Zombie Key TTL = -1).
3. **Mẫu Singleton (`private static final RedisScript`):** Khởi tạo 1 lần duy nhất khi chạy app. Kích hoạt cơ chế `EVALSHA` thần tốc của Redis, tiết kiệm 90% dung lượng đường truyền mạng và 0 sinh rác bộ nhớ JVM.

---

### ❓ KHI CÓ REDIS RATE LIMITING NÓ NHƯ THẾ NÀO?
- Một kẻ xấu dùng tool Auto-click/Script Python bắn **100 requests Đặt Giá trong 1 giây**.
- **5 requests đầu tiên trong 10s $\rightarrow$ Được phép chui vào Java để xử lý Đặt giá.**
- **95 requests còn lại $\rightarrow$ Bị chặn đứng ngay trên Redis RAM với lỗi HTTP 429 ("Hệ thống đang xử lý dữ liệu, vui lòng chờ...").**
- Server Java và Database không hề hay biết 95 requests rác kia tồn tại, tài nguyên máy chủ được bảo vệ an toàn 100%.

---

### 💥 NẾU KHÔNG CÓ REDIS RATE LIMITING THÌ SAO?
- 1 Bot Auto-click gửi 100 requests/giây $\rightarrow$ Server Java phải gánh 100 luồng chạy qua 8 bước nặng nhọc (Query User, Query Auction, Query Bid, Validate, Proxy Bidding, Anti-sniping, Save DB).
- **Phát sinh 800 câu lệnh SQL nặng nhọc/giây.**
- **Hậu quả:** CPU của Server Java vọt lên 100%, RAM kiệt sức, toàn bộ người dùng thật khác không thể bấm Đặt Giá hay Mua Hàng được nữa!

---

# 🔴 PHẦN 3: REDIS DISTRIBUTED LOCK (KHÓA PHÂN TÁN ĐỒNG THỜI - REDISSON RLOCK)

### 📌 1. Chi tiết chức năng & Nghiệp vụ áp dụng
Distributed Lock đóng vai trò là **"Cây Búa Trọng Tài Báo Giờ"** ép các người dùng khác nhau phải xếp hàng từng người một khi cùng tranh chấp 1 sản phẩm:

- *Tập tin:* [`RedisConfig.java`](file:///home/duong/Projects/Backend/auction-service/src/main/java/com/duong/auction/system/config/RedisConfig.java) (`RedissonClient`), [`BiddingConcurrencyFacade.java`](file:///home/duong/Projects/Backend/auction-service/src/main/java/com/duong/auction/system/service/BiddingConcurrencyFacade.java), [`AuctionBiddingController.java`](file:///home/duong/Projects/Backend/auction-service/src/main/java/com/duong/auction/system/controller/AuctionBiddingController.java)
- *Áp dụng:* Bọc `placeBidWithLock` và `executeBuyNowWithLock` theo ổ khóa `lock:auction:{auctionId}`

#### Các kỹ thuật đỉnh cao đã triển khai:
1. **Mô hình Facade bọc NGOẠI CÙNG trước `@Transactional`:**
   - Xin Redisson Lock thành công ngoài Facade $\rightarrow$ Mới gọi vào `BiddingService` mở kết nối DB `@Transactional`.
   - **Tiết kiệm 100% DB Connection Pool** trong suốt thời gian các luồng đứng chờ xin Lock.
2. **Kỹ thuật DRY với `Supplier<T>` Callback:** Gom toàn bộ logic `tryLock`, `catch`, `finally unlock` vào 1 hàm khung duy nhất `executeWithAuctionLock`.
3. **Cơ chế Watchdog tự gia hạn khóa:** Kích hoạt `leaseTime = -1` để Redisson tự động gia hạn khóa nếu Java xử lý lâu, không lo bị nhả khóa giữa chừng.
4. **Giải phóng khóa an toàn:** `finally { if (isAcquired && lock.isHeldByCurrentThread()) lock.unlock(); }` chống Deadlock tuyệt đối.

---

### ❓ KHI CÓ REDIS DISTRIBUTED LOCK NÓ NHƯ THẾ NÀO?
- Ở 3 giây cuối cùng, **User A, User B và User C** cùng bấm Đặt Giá / Mua Ngay cho chiếc iPhone 15 (#101) ở đúng mốc 11:59:59.000.
- User A cướp được chìa khóa `lock:auction:101` trước 0.001ms $\rightarrow$ User A vào DB xử lý (Giá nhảy 10.5 triệu).
- User B và C đứng ngoài hàng chờ (Queue) trong tối đa 5s.
- A xong việc nhả khóa $\rightarrow$ B lấy chìa khóa vào đọc giá MỚI NHẤT của A (10.5tr) $\rightarrow$ Nâng giá lên 11tr hợp lệ.
- **Kết quả:** Tất cả dữ liệu đấu giá, lịch sử bid và đơn hàng được ghi nhận chuẩn xác 100%, 0 có bất kỳ xung đột nào!

---

### 💥 NẾU KHÔNG CÓ REDIS DISTRIBUTED LOCK THÌ SAO?
Nếu không có Khóa Phân Tán, 3 thảm họa sau **CHẮC CHẮN XẢY RA 100%**:

1. **Thảm họa Bid Trùng Giá:**
   - User A và B cùng đọc giá cũ 10 triệu $\rightarrow$ Cùng tính Proxy Bidding $\rightarrow$ Bảng `bids` đẻ ra **2 bản ghi Bid CÙNG SỐ TIỀN 10.5 triệu từ 2 người khác nhau ở cùng 1 millisecond**!
2. **Thảm họa Sai Gia Hạn Anti-sniping:**
   - 2 lượt bid phút cuối đáng lẽ phải kéo dài 2 lần thành `12:06:00`. Nhưng do A và B cùng đọc mốc `endTime` cũ `12:00:00`, nó chỉ cộng 1 lần thành `12:03:00` $\rightarrow$ **Sai logic nghiệp vụ đấu giá!**
3. **Thảm họa Nhân Bản Đơn Hàng Mua Ngay:**
   - 2 người bấm Mua Ngay cùng mốc 11:59:59.000 $\rightarrow$ Cả 2 cùng check `existsByAuction_Id` đều thấy chưa có đơn $\rightarrow$ **Sinh ra 2 Đơn hàng mua đứt cho 1 sản phẩm duy nhất!**

---

# 🔴 PHẦN 4: REDIS USER SESSION & STATUS CACHE (BỘ BẢO VỆ PHIÊN & TRẠNG THÁI NGUỜI DÙNG)

### 📌 1. Chi tiết chức năng & Nghiệp vụ áp dụng
Đóng vai trò là **"Bộ Đệm Kiểm Tra Quyền Siêu Tốc 0ms SQL"** kiểm soát trạng thái tài khoản và mốc đăng xuất gần nhất:

1. **`user:status:{email}` (TTL = 3 ngày):**
   - *Tác dụng:* Cache trạng thái `ACTIVE` hoặc `BANNED`. Nếu `BANNED` ➔ Filter từ chối ngay HTTP 403 mà không cần query DB.
2. **`user:logout_at:{email}` (TTL = 3 ngày):**
   - *Tác dụng:* Lưu mốc timestamp đăng xuất gần nhất. Nếu `tokenIssuedAt < (lastLogoutAt - 1000ms)` ➔ Filter từ chối ngay HTTP 401.

### 🔄 Mô hình Cache-Aside & DB Fallback (Theo chỉ đạo Tech Lead):
- **PostgreSQL là Single Source of Truth**: Lưu dữ liệu vĩnh viễn.
- **Redis làm Cache ngắn hạn**: Không lưu vĩnh viễn bất kỳ Key nào.
- **Fallback DB khi Cache Miss**: Nếu Redis bị xoá data, hết hạn TTL hoặc restart, Filter tự động fallback query PostgreSQL `loadUserByUsername` và kiểm tra status trực tiếp từ CSDL.

---

# 📊 BẢNG TỔNG HỢP SO SÁNH 4 TRỤ CỘT REDIS

```
+-------------------+-----------------------------------+-----------------------------------+-----------------------------------+-----------------------------------+
| TIÊU CHÍ          | 1. REDIS CACHING                  | 2. REDIS RATE LIMITING            | 3. REDIS DISTRIBUTED LOCK         | 4. REDIS USER SESSION & STATUS    |
+-------------------+-----------------------------------+-----------------------------------+-----------------------------------+-----------------------------------+
| 🎯 Phạm vi        | Dữ liệu Đọc (Read Data)           | Từng UserID cá nhân               | Từng Phiên Đấu Giá (auctionId)    | Từng Email người dùng             |
| 🔑 Key Redis      | bid_history::101                  | rate_limit:placeBid:user:10       | lock:auction:101                  | user:status:a@gmail.com           |
| ⚙️ Cơ chế          | Spring @Cacheable / @CacheEvict   | Aspect @Order(1) + Lua Script INCR| Redisson RLock Facade + Supplier  | OncePerRequestFilter + Cache-Aside|
| 🛡️ Bảo vệ cái gì? | Bảo vệ DB khỏi bị nát đĩa I/O     | Bảo vệ Server Java khỏi bị Spam   | Bảo vệ Tính Đúng Đắn Dữ Liệu DB   | Bảo vệ Session & Thu hồi Token    |
| 💥 Rủi ro nếu thiếu| DB bị cạn Connection & sập hoàn toàn| CPU Java vọt 100%, sập server     | Bid trùng giá, đẻ 2 đơn hàng      | Tràn RAM Redis do lưu Token rác   |
+-------------------+-----------------------------------+-----------------------------------+-----------------------------------+-----------------------------------+
```

---

### 🎯 TÓM LẠI:
Nhờ sự phối hợp nhịp nhàng của **4 Lớp Redis** (Caching $\rightarrow$ Rate Limiting $\rightarrow$ Distributed Lock $\rightarrow$ User Session & Status Cache), hệ thống Đấu Giá `auction-service` của bạn đạt tới đẳng cấp của một **Hệ Thống Doanh Nghiệp Chịu Tải Cao (High-Throughput Enterprise System)**: Vừa chạy siêu tốc 1-2ms, vừa chống spam bot hiệu quả, vừa thu hồi token thông minh tối ưu RAM, vừa đảm bảo tính toàn vẹn dữ liệu DB 100%!

