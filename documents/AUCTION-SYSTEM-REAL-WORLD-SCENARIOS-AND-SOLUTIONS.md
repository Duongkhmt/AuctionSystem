# 📖 TÀI LIỆU KIẾN TRÚC & CÁC KỊCH BẢN THỰC TẾ TRONG DỰ ÁN ĐẤU GIÁ (DuAnTrainning)

Tài liệu này tổng hợp toàn bộ các **Kịch bản sự cố thực tế (Real-world Scenarios)**, **Rủi ro dữ liệu (Data Risks)** và **Phân tích Kiến trúc Giải pháp Chuẩn xác (Refined Engineering Solutions)** được thiết kế riêng cho hệ thống Đấu Giá Trực Tuyến `DuAnTrainning`.

---

## 🎯 TỔNG QUAN PHÂN LỚP KIẾN TRÚC TỐI ƯU

```
+-----------------------------------------------------------------------------------------+
|  1. CẦU DAO CHỐNG SPAM (REDIS RATE LIMITING - ASPECT LUA SCRIPT)                        |
|     -> Áp dụng: Tất cả các API public (placeBid, executeBuyNow)                         |
|     -> Nhiệm vụ: Chặn đứng Bot Auto-click 100 req/s từ RAM 0.1ms (Lỗi HTTP 429).        |
+-----------------------------------------------------------------------------------------+
|  2. KHÓA PHÂN TÁN REDISSON LOCK (DISTRIBUTED LOCK)                                     |
|     -> Áp dụng CHÍNH XÁC: Luồng MUA NGAY GIÁ CỐ ĐỊNH (`executeBuyNow`)                   |
|     -> Nhiệm vụ: Sản phẩm duy nhất (Stock = 1). Người thứ nhất khóa phòng chốt đơn,    |
|        người thứ 2 đến sau lập tức bị ngắt an toàn với lỗi `AUCTION_NOT_RUNNING`.       |
+-----------------------------------------------------------------------------------------+
|  3. TRẠNG THÁI TRÊN REDIS RAM (REDIS IN-MEMORY STATE MACHINE & LUA SCRIPT)               |
|     -> Áp dụng CHÍNH XÁC: Luồng ĐẶT GIÁ CẠNH TRANH TẢI CAO (`placeBid`)                |
|     -> Nhiệm vụ: Đè giá siêu tốc 0.1ms trên RAM. 0 bắt hàng ngàn người dùng xếp hàng   |
|        chờ khóa đĩa DB 5s. Ghi DB ngầm qua Message Queue ngầm phía sau.                 |
+-----------------------------------------------------------------------------------------+
```

---

## 📌 KỊCH BẢN 1: TẤN CÔNG BẮN BOT SPAM ĐẶT GIÁ (BOT AUTO-CLICK ATTACK)

### 🎬 Mô tả kịch bản:
Kẻ xấu sử dụng script tự động (Python/Node.js) hoặc phần mềm Auto-click gửi **100 requests `placeBid()` trong vòng 1 giây** vào cùng một tài khoản.

### 💥 Sự cố thực tế nếu KHÔNG xử lý:
- Để kiểm tra một lượt bid có hợp lệ hay không, Server Java phải chạy qua 3 bước truy vấn SQL (`userRepository`, `auctionRepository`, `bidRepository`).
- Khi Bot gửi 100 requests/giây $\rightarrow$ Phát sinh **300 câu lệnh SQL SELECT nặng nhọc đâm xuống PostgreSQL đĩa cứng trong 1 giây**.
- **Hậu quả:** Đĩa cứng PostgreSQL bị nghẽn đĩa I/O, cạn kiệt DB Connection Pool $\rightarrow$ **Toàn bộ hệ thống bị quay tròn và sập hoàn toàn (Denial of Service - DoS)!**

### 🛡️ Giải pháp kỹ thuật triển khai:
- Khởi tạo Aspect **`RateLimitAspect.java`** bọc `@Order(Ordered.HIGHEST_PRECEDENCE)` (chạy ngoài cùng trước `@Transactional`).
- Dùng **Redis Lua Script nguyên tử (`INCR` + `EXPIRE 10s`)** để đếm số lượt bấm trên RAM.
- Gắn nhãn `@RateLimit(maxRequests = 5, timeWindowSeconds = 10)` lên hàm `placeBid()`.
- **Kết quả:** 5 requests đầu tiên được cho qua. **95 requests rác còn lại bị dập tắt ngay từ RAM (mất 0.1ms) với lỗi HTTP 429**. 0 có bất kỳ câu SQL rác nào được phép đâm xuống Database!

---

## 📌 KỊCH BẢN 2: BÀI TOÁN MUA NGAY GIÁ CỐ ĐỊNH (BUY-NOW: PHÙ HỢP HOÀN HẢO VỚI DISTRIBUTED LOCK)

### 🎬 Mô tả kịch bản:
Sản phẩm đấu giá loại `BUY_NOW` có giá Mua Ngay `15,000,000 VNĐ` (Số lượng tồn kho đúng 1 chiếc). **Khách A và Khách B cùng bấm nút "⚡ MUA NGAY" ở mốc 11:59:59**.

### 💥 Sự cố thực tế nếu KHÔNG xử lý:
- Luồng A và B cùng chui vào `BidValidator.validateBuyNow()` $\rightarrow$ Cùng thấy trạng thái `status == RUNNING`.
- Luồng A và B cùng check `orderRepository.existsByAuction_Id()` $\rightarrow$ Cùng thấy chưa có Đơn hàng nào!
- Cả A và B cùng chạy `orderMapper.toEntity()` và lưu DB.
- **Hậu quả:** Sinh ra **2 Đơn hàng mua đứt (`Order A` và `Order B`) cho 1 chiếc điện thoại duy nhất!** Người bán không thể giao 1 chiếc điện thoại cho 2 người khác nhau!

### 🛡️ Giải pháp kỹ thuật: ĐÂY CHÍNH LÀ ĐẤT DIỄN CHUẨN XÁC CỦA DISTRIBUTED LOCK!
- Bọc Redisson Distributed Lock cho phương thức `executeBuyNowWithLock`: `RLock lock = redissonClient.getLock("lock:auction:" + auctionId)`.
- Khách A lấy chìa khóa vào trước $\rightarrow$ Đổi status sang `ENDED`, gán `winner = A`, sinh `Order A`.
- Khách B vào sau $\rightarrow$ Chui vào `BidValidator` thấy `status == ENDED` $\rightarrow$ Ném lỗi `AUCTION_NOT_RUNNING` lập tức! B bị từ chối an toàn.
- **Đánh giá kiến trúc:** Với luồng Mua Ngay (chỉ bán cho 1 người duy nhất), dùng Distributed Lock là **hoàn hảo 100%**, vì người thắng mua đứt sản phẩm và kết thúc phiên luôn!

---

## 📌 KỊCH BẢN 3: BÀI TOÁN ĐẶT GIÁ CẠNH TRANH NHIỀU NGƯỜI CÙNG LÚC (HIGH CONCURRENCY BIDDING)

### 🎬 Mô tả kịch bản:
Ở 5 giây cuối cùng của phiên đấu giá chiếc iPhone 15 (#101), **có 1,000 đến 10,000 người dùng cùng nhấp "Đặt Giá" ở cùng 1 thời điểm**.

### 💥 Phân tích tại sao KHÔNG NÊN bọc Khóa Phân Tán DB Lock cho luồng Đặt Giá này:
- Nếu bọc Redisson Lock + Ghi đĩa PostgreSQL đồng bộ cho `placeBid()`:
  - Thời gian giữ lock là **5ms/người** (do ghi đĩa DB).
  - Trong 5s chờ của chìa khóa, hệ thống chỉ xử lý được 1,000 người đầu tiên.
  - **9,000 người còn lại ở sau bị đứng chờ 5s và nhận lỗi HTTP 409 Timeout $\rightarrow$ Trải nghiệm người dùng rất tệ!**

### 🛡️ Giải pháp tối ưu kiến trúc chuẩn cấp ngành (Shopee / eBay Pattern):
Không dùng DB Lock ép người dùng đứng chờ 5s nữa! Thay vào đó:

1. **Lưu Trạng Thái Đè Giá 100% trên Redis RAM (In-Memory State Machine):**
   - Giá hiện tại `currentPrice` và `highestBidder` được đệm trực tiếp trên Redis RAM.
   - Khi có lượt bid mới, gửi **Redis Lua Script** xuống RAM để so kè giá và cập nhật giá mới ngay trên RAM.
   - **Thời gian giữ lock RAM giảm từ 5ms xuống 0.1ms (Nhanh gấp 50 lần!)** $\rightarrow$ Xử lý mượt mà **10,000 người/5s thành công**, không ai bị đứng chờ hay ăn lỗi 409!
2. **Ghi DB ngầm phía sau (Asynchronous Write-Behind via Message Queue):**
   - Trả kết quả THÀNH CÔNG cho Web ngay 0.01s.
   - Ném lượt bid vào Kafka / Redis Stream Queue.
   - Worker ngầm phía sau thong thả lấy ra ghi vào PostgreSQL DB ngầm.

---

## 📌 KỊCH BẢN 4: POLLING XEM LỊCH SỬ BID & CHI TIẾT SẢN PHẨM TẢI CAO (READ TRAFFIC BOTTLE)

### 🎬 Mô tả kịch bản:
Có **5,000 người dùng** cùng mở màn hình ngắm chiếc iPhone 15 và ứng dụng Frontend tự động Polling (gửi GET request) xem lịch sử bid 2 giây/lần.

### 💥 Sự cố thực tế nếu KHÔNG xử lý:
- 5,000 người Polling 2s/lần $\rightarrow$ Phát sinh **2,500 câu lệnh SQL SELECT/giây** đâm xuống PostgreSQL.
- **Hậu quả:** PostgreSQL bị nghẽn đĩa I/O, cạn kiệt Connection pool $\rightarrow$ Server sập hoàn toàn.

### 🛡️ Giải pháp kỹ thuật triển khai:
- Sử dụng **Spring Cache + Redis RAM**:
  - `@Cacheable(value = "bid_history", key = "#auctionId")` (TTL = 30s)
  - `@CacheEvict(value = "bid_history", key = "#auctionId")` (Xóa cache ngay khi có bid mới)
- **Kết quả:** Trả dữ liệu trong **1 - 2ms trên Redis RAM**, giảm **>90%** câu lệnh SQL xuống PostgreSQL.

---

## 📊 BẢNG TỔNG HỢP PHÂN CÔNG CÔNG NGHỆ CHUẨN KIẾN TRÚC

| NGHIỆP VỤ | CÔNG NGHỆ ÁP DỤNG | LÝ DO KIẾN TRÚC |
| :--- | :--- | :--- |
| **1. Chống Spam Bot API** | Redis Rate Limit (`INCR` + Lua Script + `@Order(1)`) | Chặn rác từ RAM 0.1ms, không cho SQL rác đâm DB. |
| **2. Mua Ngay (`executeBuyNow`)** | **Redisson Distributed Lock (`RLock`)** | **Phù hợp 100%!** Hàng tồn kho đúng 1 chiếc. Người thứ 1 chốt mua, người thứ 2 ngắt ngay. |
| **3. Đặt Giá Cạnh Tranh (`placeBid`)** | **Redis In-Memory State (Lua Script) + Async Queue** | **Tránh bị chờ lâu!** Xử lý đè giá 0.1ms trên RAM, trả kết quả 0.01s, 0 bắt user chờ khóa DB 5s. |
| **4. Xem Lịch Sử & Chi Tiết** | Spring Cache + Redis RAM (`@Cacheable`) | Trả kết quả 1ms, giảm 90%+ truy vấn đĩa DB. |
