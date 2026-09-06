
# 1. BÀI TOÁN GỐC

Trong hệ thống Đấu Giá `AuctionSystem`, **`AUCTION_ENDED` (Phiên Đấu Giá Kết Thúc)** là sự kiện quan trọng nhất. Khi đồng hồ đếm ngược hết giờ hoặc có người bấm Mua Ngay, hệ thống phải thực hiện 2 việc chính:
1. **Việc 1 (Cốt lõi):** Đổi trạng thái phiên thầu sang `ENDED` và xác định người thắng (`winner`).
2. **Việc 2 (Hậu thầu):** Tạo bản ghi Đơn hàng `Order` UNPAID hạn 48h trong DB, phát Email thông báo trúng thầu cho Người mua, thông báo cho Người bán...

---

### Thảm họa của cách làm cũ (Đồng bộ - Synchronous):
Nếu ta bắt Robot `AuctionScheduler` vừa đổi trạng thái phiên thầu, vừa tạo Đơn hàng, vừa gọi dịch vụ Email trong **cùng 1 luồng duy nhất**:
- **Nghẽn luồng dây chuyền:** Khi có 100 phiên đấu giá cùng hết hạn tại một thời điểm, nếu mỗi phiên phải gọi dịch vụ Email SMTP mất 1-2 giây ➔ Robot bị nghẽn đứng trong vài phút. Các phiên hết hạn tiếp theo bị giam giữ, sai lệch thời gian chốt thầu.
- **Sập dây chuyền (Cascade Failure):** Nếu dịch vụ gửi Email bị ngắt mạng hoặc nghẽn ➔ Lỗi ném ra làm **Rollback** toàn bộ giao dịch ➔ Dẫn đến thảm họa: **Người mua đấu giá thắng hợp lệ nhưng bị mất đơn hàng oan!**

---

### 🟢 Giải pháp Kafka (Bất đồng bộ - Asynchronous Event-Driven):
Sử dụng Apache Kafka để **TÁCH RỜI 100% (Decoupling)**:
- Luồng chính (`AuctionScheduler`) chỉ làm đúng việc chốt trạng thái phiên thầu sang `ENDED` trong CSDL ➔ Đăng ký phát sự kiện `AuctionEndedEvent` lên Kafka qua `TransactionSynchronization.afterCommit`. Robot rảnh tay ngay lập tức để phục vụ các phiên khác.
- Phía Consumer ngầm (`AuctionEndedConsumer`) nhặt sự kiện từ Kafka và **tự động đẻ bản ghi `Order` 48h vào DB ngầm + Phát Email thông báo trúng thầu** phía sau.

---

# 2. SƠ ĐỒ DÒNG CHẢY HỆ THỐNG (ARCHITECTURAL DATA FLOW)

```text
[ Robot Scheduler quét hết giờ ]
               │
               ▼
┌──────────────────────────────────────────────────────────┐
│ 1. Cập nhật Auction.status = ENDED                       │
│ 2. Đợi DB Commit thành công (afterCommit)                │
│ 3. Bắn AuctionEndedEvent lên Kafka Broker                │
└────────────────────────────┬─────────────────────────────┘
                             │
                             ▼
              [ Apache Kafka Topic: auction.events.ended ]
                             │
                             ▼ (Bất đồng bộ ngầm)
┌──────────────────────────────────────────────────────────┐
│ 4. Consumer nhặt sự kiện (AuctionEndedConsumer)          │
│ 5. Đọc kiểm tra Redis (Check-Then-Mark Idempotency)      │
│ 6. Thực sự tạo Đơn hàng Order UNPAID 48h vào DB ngầm     │
│ 7. Gọi EmailService phát Email chúc mừng cho Winner      │
│ 8. Đánh dấu thành công vào Redis PROCESSED (TTL 24h)     │
└──────────────────────────────────────────────────────────┘
```

---

# 3. GIẢI THÍCH CHI TIẾT TỪNG FILE ĐÃ TẠO

### 1. File [`AuctionSystemApplication.java`](file:///home/duong/Projects/Backend/DuAnTrainning/src/main/java/com/duong/auction/system/AuctionSystemApplication.java)
- **Nhiệm vụ:** Kích hoạt nạp tự động toàn bộ biến môi trường từ file `.env` vào System Properties khi ứng dụng vừa bật:
  ```java
  Dotenv dotenv = Dotenv.configure().ignoreIfMissing().load();
  dotenv.entries().forEach(entry -> System.setProperty(entry.getKey(), entry.getValue()));
  ```

---

### 2. File [`KafkaConfig.java`](file:///home/duong/Projects/Backend/DuAnTrainning/src/main/java/com/duong/auction/system/config/KafkaConfig.java)
- **Nhiệm vụ:** Cấu hình khởi tạo Topic và **Cơ chế Xử Lý Lỗi 3 Tầng**:
  - **Topic Chính:** `auction.events.ended` (3 partitions).
  - **Topic Retry (`auction.events.ended-retry`):** Thử lại 3 lần (cách 2000ms) nếu CSDL/SMTP bị bận tạm thời.
  - **Topic DLT (`auction.events.ended-dlt`):** Nếu thử lại 3 lần vẫn thất bại, tin nhắn bị cô lập vào "Nghĩa Địa Tin Nhắn DLT" để ghi log cảnh báo `@DltHandler`.

---

### 3. File [`AuctionKafkaProducer.java`](file:///home/duong/Projects/Backend/DuAnTrainning/src/main/java/com/duong/auction/system/service/producer/AuctionKafkaProducer.java)
- **Nhiệm vụ:** Bưu tá phát sự kiện lên Kafka kèm Partition Key:
  - Dùng `String.valueOf(event.getAuctionId())` làm Partition Key ➔ Đảm bảo toàn bộ sự kiện của 1 phiên đấu giá đi vào cùng 1 Partition (Thứ tự FIFO tuyệt đối).
  - Dùng `CompletableFuture.whenComplete()` để bắt phản hồi bất đồng bộ từ Broker.

---

### 4. File [`AuctionEndedSettlementHelper.java`](file:///home/duong/Projects/Backend/DuAnTrainning/src/main/java/com/duong/auction/system/service/helper/AuctionEndedSettlementHelper.java)
- **Nhiệm vụ:** Chốt trạng thái phiên thầu trong giao dịch DB độc lập `@Transactional(propagation = Propagation.REQUIRES_NEW)`.
- **Chống Dual-Write:** Dùng `TransactionSynchronizationManager.registerSynchronization(afterCommit)` ➔ CHỈ BẮN KAFKA KHI CSDL ĐÃ COMMIT THÀNH CÔNG!

---

### 5. File [`AuctionEndedConsumer.java`](file:///home/duong/Projects/Backend/DuAnTrainning/src/main/java/com/duong/auction/system/service/consumer/AuctionEndedConsumer.java)
- **Nhiệm vụ:** Nhặt tin nhắn sự kiện `AUCTION_ENDED` ➔ Check Redis Idempotency (`kafka:processed_event:<eventId>`) ➔ Lưu Đơn hàng 48h vào CSDL ➔ Gọi `EmailService.sendAuctionWinnerEmail` phát Email thông báo ngầm cho Winner ➔ Đánh dấu `PROCESSED` vào Redis.

---

### 6. File [`EmailService.java`](file:///home/duong/Projects/Backend/DuAnTrainning/src/main/java/com/duong/auction/system/service/EmailService.java)
- **Nhiệm vụ:** Phát Email thông báo thắng thầu qua Gmail SMTP dạng Plain Text Multiline (`String.format`). Lấy cấu hình `@Value("${app.mail.from}")` đóng gói từ `.env`.

---

### 7. File [`AuctionScheduler.java`](file:///home/duong/Projects/Backend/DuAnTrainning/src/main/java/com/duong/auction/system/service/AuctionScheduler.java)
- **Nhiệm vụ:** Robot 10s/lần quét DB chốt phiên thầu. Thêm `@CacheEvict(value = "auctions", allEntries = true)` để dọn sạch bản cache rác trong Redis khi đổi trạng thái ➔ Khắc phục triệt để lỗi lệch trạng thái "SẮP DIỄN RA" / "ĐANG ĐẤU GIÁ" giữa trang ngoài và trang chi tiết.

---

# 4. CÁC KỊCH BẢN RỦI RO THỰC TẾ & GIẢI PHÁP ĐÃ XỬ LÝ

### 🎬 Kịch Bản 1: Chốt Phiên Hàng Loạt Khi Hết Giờ (Batch Settlement)
- **Vấn đề:** 100 phiên đấu giá cùng kết thúc một lúc. Nếu xử lý đồng bộ, Robot bị ngâm 2-3 phút gửi Email.
- **Giải pháp:** Robot chốt CSDL rồi đẩy qua Kafka (`auction.events.ended`). Consumer ngầm tự động đẻ đơn hàng và gửi Email ngầm ở Background mà không làm đứng Robot.

### 🎬 Kịch Bản 2: Mất Đồng Bộ DB vs Kafka (Dual-Write Discrepancy)
- **Vấn đề:** DB chốt phiên chưa lưu xong nhưng Kafka đã lỡ bắn tin nhắn ➔ Đẻ ra Đơn hàng ma và Email giả nếu DB Rollback!
- **Giải pháp:** Dùng `TransactionSynchronizationManager.afterCommit` trong `AuctionEndedSettlementHelper.java`. CHỈ BẮN KAFKA KHI CSDL THỰC SỰ COMMIT THÀNH CÔNG.

### 🎬 Kịch Bản 3: Mạng Chập Chờn Khiến Kafka Phát Lại Tin Nhắn (Message Replay)
- **Vấn đề:** Consumer tạo Đơn hàng xong nhưng rớt mạng chưa gửi Ack ➔ Kafka phát lại tin nhắn lần 2 ➔ Bị đẻ 2 đơn hàng trùng và gửi 2 Email.
- **Giải pháp:** Áp dụng cơ chế **Redis Idempotency Check 3 bước** (`kafka:processed_event:<eventId>`, TTL 24h). Dù Kafka phát lại 10 lần thì CSDL vẫn chỉ có đúng 1 Đơn hàng và Winner chỉ nhận 1 Email.

### 🎬 Kịch Bản 4: Tin Nhắn Độc / Hỏng Mạng SMTP (Poison Pill & Network Failure)
- **Vấn đề:** Đơn hàng bị lỗi hoặc SMTP bị nghẽn ➔ Làm đứng toàn bộ hàng chờ tin nhắn.
- **Giải pháp:** Cấu hình `RetryTopicConfiguration` (Thử lại 3 lần, mỗi lần 2s). Nếu sau 3 lần vẫn lỗi ➔ Chuyển sang Dead Letter Topic (`auction.events.ended-dlt`) và gọi `@DltHandler` để Admin xử lý thủ công.

### 🎬 Kịch Bản 5: Lệch Trạng Thái Do Redis Cache Bị Cũ (Stale Redis Cache)
- **Vấn đề:** Hàm xem chi tiết bật Redis Cache `@Cacheable(value = "auctions")`. Khi Robot đổi trạng thái trong CSDL từ `SCHEDULED` ➔ `RUNNING`, nếu không xóa cache ➔ Trang chi tiết bị kẹt ở chữ "SẮP DIỄN RA".
- **Giải pháp:** Thêm `@CacheEvict(value = "auctions", allEntries = true)` vào Robot `AuctionScheduler.java`. Mỗi 10s Robot quét DB đồng thời xóa sạch bản cache rác trong Redis ➔ Trang chi tiết và ngoài danh sách luôn đồng bộ 100% `🟢 ĐANG ĐẤU GIÁ`.
