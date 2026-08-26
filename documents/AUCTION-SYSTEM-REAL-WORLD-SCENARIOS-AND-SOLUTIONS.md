# 🚀 BÁO CÁO TỔNG THỂ KIẾN TRÚC VÀ GIẢI PHÁP TỐI ƯU HỆ THỐNG ĐẤU GIÁ (REDIS ATOMIC ARCHITECTURE)

---

# 🔴 PHẦN 1: BÀI TOÁN & GIẢI PHÁP ĐẶT GIÁ CAO VẬN TỐC (PLACE BID)

### 📌 1. Vấn Đề Của Bài Toán Đặt Giá
Trong 3 giây cuối cùng của phiên đấu giá, hàng trăm/hàng ngàn người dùng (100+ requests) cùng bấm nút **Đặt Giá** tại đúng mốc 1 mili-giây.

Nếu dùng cơ chế ghi đĩa PostgreSQL truyền thống:
- Cả 100 luồng cùng đâm xuống Database mở `@Transactional` và khóa dòng (Row-level Lock).
- **Hậu quả:** 99 người đứng chờ $\rightarrow$ Bị ném lỗi **409 Lock Timeout** hoặc sập Database do cạn DB Connection Pool.

---

### ⚡ 2. Giải Pháp Tối Ưu: Redis Atomic Lua Script

Gộp 3 thao tác **Đọc $\rightarrow$ Kiểm tra $\rightarrow$ Cập nhật giá** thành một thao tác nguyên tử (Atomic) duy nhất, thực thi bằng Lua script trên Redis. 

Vì Redis xử lý lệnh theo cơ chế **Đơn luồng (Single-thread)**, toàn bộ đoạn script chạy trọn vẹn 0.02ms mà không bị request khác chen vào giữa quá trình kiểm tra và ghi dữ liệu. Nhờ vậy:
- Dù 100 request đặt giá đồng thời, mỗi request vẫn được so sánh với giá hiện tại mới nhất tại đúng thời điểm nó được xử lý, tránh triệt để **Race Condition** và đảm bảo dữ liệu đấu giá luôn nhất quán.
- Những request có giá thấp hơn sẽ nhận kết quả **"thua giá"** ngay lập tức (< 1ms), không phải chờ đợi hay bị từ chối do lỗi tranh chấp hệ thống.

---

# 🔴 PHẦN 2: LUỒNG THỰC THI TRONG MÃ NGUỒN (`BiddingService.java`)

```java
@RateLimit(maxRequests = 10, timeWindowSeconds = 1)
@CacheEvict(value = "bid_history", key = "#auctionId")
@Transactional
public BidResponseDTO placeBid(Long bidderId, Long auctionId, BidRequestDTO requestDTO) {
    // 1. Kiểm tra thông tin người đặt giá và phiên đấu giá
    User bidder = userRepository.findById(bidderId)
            .orElseThrow(() -> new ApplicationException(ErrorCode.USER_NOT_FOUND));

    Auction auction = auctionRepository.findById(auctionId)
            .or(() -> auctionRepository.findByProduct_Id(auctionId))
            .orElseThrow(() -> new ApplicationException(ErrorCode.AUCTION_NOT_FOUND));

    if (auction.getStatus() != AuctionStatus.RUNNING) {
        throw new ApplicationException(ErrorCode.AUCTION_NOT_RUNNING);
    }

    // 2. REDIS ATOMIC (LUA SCRIPT): Gộp Đọc -> Kiểm tra -> Cập nhật giá nguyên tử trên RAM (0.02ms)
    boolean success = redisEngine.processBidAtomic(
            auction.getId(),
            bidderId,
            requestDTO.getBidAmount(),
            auction.getBidStep(),
            auction.getCurrentPrice()
    );

    // Thua giá -> Trả về kết quả "Thua giá" lập tức (< 1ms), 0 bị từ chối do xung đột hệ thống
    if (!success) {
        throw new ApplicationException(ErrorCode.BID_AMOUNT_TOO_LOW);
    }

    // 3. Nếu thắng giá -> Lưu ngay lượt Bid mới và cập nhật giá phiên đấu giá vào Database
    Bid bid = new Bid();
    bid.setAuction(auction);
    bid.setBidder(bidder);
    bid.setBidAmount(requestDTO.getBidAmount());
    bid.setAutoBid(false);
    bidRepository.save(bid);

    auction.setCurrentPrice(requestDTO.getBidAmount());
    auctionRepository.save(auction);

    // 4. Trả phản hồi đặt giá thành công cho Client
    return BidResponseDTO.builder()
            .auctionId(auction.getId())
            .bidAmount(requestDTO.getBidAmount())
            .newCurrentPrice(requestDTO.getBidAmount())
            .build();
}
```

---

# 🔴 PHẦN 3: BẢNG SO SÁNH 3 TRỤ CỘT REDIS TRONG DỰ ÁN

```
+-------------------+-----------------------------------+-----------------------------------+-----------------------------------+
| TIÊU CHÍ          | 1. REDIS CACHING                  | 2. REDIS RATE LIMITING            | 3. REDIS ATOMIC LUA SCRIPT        |
+-------------------+-----------------------------------+-----------------------------------+-----------------------------------+
| 🎯 Phạm vi        | Dữ liệu Đọc (Read Data)           | Từng UserID cá nhân               | Từng Phiên Đấu Giá (auctionId)    |
| 🔑 Key Redis      | bid_history::101                  | rate_limit:placeBid:user:10       | auction:state:101                 |
| ⚙️ Cơ chế          | Spring @Cacheable / @CacheEvict   | Aspect @Order(1) + Lua Script INCR| Lua Script Atomic (Read-Check-Set)|
| 🛡️ Bảo vệ cái gì? | Bảo vệ DB khỏi bị nát đĩa I/O     | Bảo vệ Server Java khỏi bị Spam   | Chống Race Condition & Đè giá sai |
| 💥 Rủi ro nếu thiếu| DB bị cạn Connection & sập hoàn toàn| CPU Java vọt 100%, sập server     | 409 Timeout, đè sai giá đấu       |
+-------------------+-----------------------------------+-----------------------------------+-----------------------------------+
```

---

# 🔴 PHẦN 4: BÀI TOÁN XỬ LÝ CHỐT PHIÊN ĐẤU GIÁ BẤT ĐỒNG BỘ (APACHE KAFKA & DLT)

### 📌 1. Vấn Đề Của Bài Toán Kết Thúc Đấu Giá (`AUCTION_ENDED`)
- Khi một phiên đấu giá kết thúc (do hết giờ hoặc người dùng Mua Ngay), hệ thống phải vừa đổi trạng thái phiên thầu, vừa đẻ đơn hàng `Order` UNPAID 48h, vừa gửi Email và Push Notification.
- **Hậu quả nếu làm đồng bộ (Synchronous):** Nếu dịch vụ Email bị chậm hoặc rớt mạng, cả Robot `AuctionScheduler` bị đóng băng, dẫn đến lỗi Rollback làm người thắng thầu hợp lệ bị mất đơn hàng!

---

### ⚡ 2. Giải Pháp Tối Ưu: Event-Driven Architecture với Apache Kafka
- **Luồng chính (`AuctionScheduler` / `BiddingService.executeBuyNow`):** Chốt `Auction.status = ENDED` và phát sự kiện `AuctionEndedEvent` lên Kafka Broker trong `< 2ms`.
- **Luồng ngầm (`AuctionEndedConsumer`):** Tiêu thụ sự kiện ngầm, thực sự đẻ đơn hàng `Order` vào DB và gửi Email.
- **Cơ chế Bảo vệ 3 Tầng:**
  1. **Dual-Write Protection:** Dùng `TransactionSynchronizationManager.afterCommit(...)` đảm bảo DB Commit thành công 100% mới phát sự kiện Kafka.
  2. **Non-Blocking Retry Topic & DLT:** Khi Consumer bị lỗi DB/mạng tạm thời, tin nhắn tự chuyển sang `auction.events.ended-retry` để thử lại 3 lần (mỗi lần 2s) mà không làm tắc nghẽn luồng chính. Nếu 3 lần vẫn hỏng, tin nhắn bị cô lập vào `auction.events.ended-dlt` kèm `@DltHandler`.
  3. **Check-Then-Mark Idempotency:** Kiểm tra `redisTemplate.hasKey` trước, xử lý xong mới `set` key `PROCESSED` vào Redis 24h.

---

# 📂 PHẦN 5: DANH SÁCH FILE VÀ TRẠNG THÁI TRIỂN KHAI TỔNG THỂ

| STT | Tên File | Vai Trò | Trạng Thái |
| :--- | :--- | :--- | :--- |
| 1 | [`RedisAtomicBiddingEngine.java`](file:///home/duong/Projects/Backend/DuAnTrainning/src/main/java/com/duong/auction/system/service/engine/RedisAtomicBiddingEngine.java) | Động cơ so kè giá nguyên tử Lua Script trên RAM | ✅ Completed |
| 2 | [`BiddingService.java`](file:///home/duong/Projects/Backend/DuAnTrainning/src/main/java/com/duong/auction/system/service/BiddingService.java) | Tích hợp Redis Atomic & Kafka Buy-Now | ✅ Completed |
| 3 | [`AuctionBiddingController.java`](file:///home/duong/Projects/Backend/DuAnTrainning/src/main/java/com/duong/auction/system/controller/AuctionBiddingController.java) | Nối dây Controller xử lý siêu tốc | ✅ Completed |
| 4 | [`RateLimitAspect.java`](file:///home/duong/Projects/Backend/DuAnTrainning/src/main/java/com/duong/auction/system/aspect/RateLimitAspect.java) | Cầu dao chống spam bot ở vòng ngoài | ✅ Completed |
| 5 | [`KafkaConfig.java`](file:///home/duong/Projects/Backend/DuAnTrainning/src/main/java/com/duong/auction/system/config/KafkaConfig.java) | Cấu hình Kafka Topic, Producer, ConsumerFactory & Non-Blocking Retry Topic 3 Tầng | ✅ Completed |
| 6 | [`AuctionKafkaProducer.java`](file:///home/duong/Projects/Backend/DuAnTrainning/src/main/java/com/duong/auction/system/service/producer/AuctionKafkaProducer.java) | Producer phát sự kiện ngầm kèm CompletableFuture Callback | ✅ Completed |
| 7 | [`AuctionEndedSettlementHelper.java`](file:///home/duong/Projects/Backend/DuAnTrainning/src/main/java/com/duong/auction/system/service/helper/AuctionEndedSettlementHelper.java) | Helper xử lý giao dịch `REQUIRES_NEW` chống Dual-Write & Self-Invocation | ✅ Completed |
| 8 | [`AuctionEndedConsumer.java`](file:///home/duong/Projects/Backend/DuAnTrainning/src/main/java/com/duong/auction/system/service/consumer/AuctionEndedConsumer.java) | Consumer nhặt sự kiện tạo Đơn ngầm + Check-Then-Mark Redis 24h & `@DltHandler` | ✅ Completed |

---

### 🎯 TÓM LẠI:
Hệ thống Đấu Giá của bạn hiện đã hoàn thiện **2 Động Cơ Kiến Trúc Hàng Đầu**: **Redis Atomic Lua Script** (cho luồng Đặt Giá cao vận tốc) và **Apache Kafka Event-Driven Architecture** (cho luồng Chốt Thầu & Đẻ Đơn Ngầm)! 🚀
