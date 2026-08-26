# 📖 CẨM NANG TOÀN DIỆN: GIẢI THÍCH CHI TIẾT KIẾN TRÚC KAFKA & CÁC BÀI TOÁN XỬ LÝ LỖI TRONG DỰ ÁN

---

# 🔴 1. TẠI SAO DỰ ÁN LẠI CẦN APACHE KAFKA? (VẤN ĐỀ GỐC)

Trong hệ thống Đấu Giá `AuctionSystem`, **`AUCTION_ENDED` (Phiên Đấu Giá Kết Thúc)** là sự kiện quan trọng nhất. Khi đồng hồ đếm ngược hết giờ hoặc có người bấm Mua Ngay, hệ thống phải thực hiện 2 việc chính:
1. **Việc 1 (Cốt lõi):** Đổi trạng thái phiên thầu sang `ENDED` và xác định người thắng (`winner`).
2. **Việc 2 (Hậu thầu):** Tạo bản ghi Đơn hàng `Order` UNPAID hạn 48h trong DB, gửi Email thông báo trúng thầu cho Người mua, gửi Email cho Người bán, gửi Push Notification lên Mobile...

---

### 💥 Thảm họa của cách làm cũ (Đồng bộ - Synchronous):
Nếu ta bắt Robot `AuctionScheduler` vừa đổi trạng thái phiên thầu, vừa tạo Đơn hàng, vừa gọi dịch vụ Email trong **cùng 1 luồng duy nhất**:
- **Nghẽn luồng:** Nếu dịch vụ gửi Email bị chậm 3 giây, toàn bộ Robot bị đứng ngâm 3 giây.
- **Sập dây chuyền:** Nếu dịch vụ gửi Email bị ngắt mạng $\rightarrow$ Lỗi ném ra làm **Rollback** toàn bộ giao dịch $\rightarrow$ Dẫn đến thảm họa: **Người mua đấu giá thắng hợp lệ nhưng bị mất đơn hàng oan!**

---

### ⚡ Giải pháp Kafka (Bất đồng bộ - Asynchronous Event-Driven):
Sử dụng Apache Kafka để **TÁCH RỜI 100% (Decoupling)**:
- Luồng chính (`AuctionScheduler`) chỉ làm đúng việc chốt trạng thái phiên thầu sang `ENDED` và **bắn 1 sự kiện `AuctionEndedEvent` lên Kafka trong < 2ms**.
- Phía Consumer ngầm nhặt sự kiện từ Kafka và tự **thực sự đẻ bản ghi `Order` vào DB ngầm + Gửi Email/Push Notification** phía sau.

---

# 🟢 2. SƠ ĐỒ DÒNG CHẢY HỆ THỐNG (ARCHITECTURAL DATA FLOW)

```text
[ Robot Scheduler quét hết giờ ]
               │
               ▼ (< 2ms)
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
│ 7. Đánh dấu thành công vào Redis PROCESSED                │
└──────────────────────────────────────────────────────────┘
```

---

# 📂 3. GIẢI THÍCH CHI TIẾT TỪNG FILE ĐÃ TẠO VÀ SỬA

### 1. File [`docker-compose.yml`](file:///home/duong/Projects/docker-compose.yml)
- **Nhiệm vụ:** Dùng Docker khởi chạy 2 container ngầm cho hạ tầng Kafka:
  - `zookeeper` (port 2181): Đóng vai trò là "quản trị viên" điều phối Kafka Cluster.
  - `kafka` (port 9092): Broker chứa các đường truyền tin nhắn (Topic).

---

### 2. File [`pom.xml`](file:///home/duong/Projects/Backend/DuAnTrainning/pom.xml) & [`application.properties`](file:///home/duong/Projects/Backend/DuAnTrainning/src/main/resources/application.properties)
- **`pom.xml`:** Khai báo thư viện `spring-kafka`.
- **`application.properties`:** Khai báo cấu hình chuyển đổi kiểu dữ liệu truyền qua mạng:
  - `key-serializer`: Dùng `StringSerializer` (Mã hóa Key là chuỗi `auctionId`).
  - `value-serializer`: Dùng `JsonSerializer` (Mã hóa Java DTO thành chuỗi JSON truyền qua Kafka).
  - `group-id`: Đặt tên Consumer Group mặc định là `auction-service-group`.

---

### 3. File [`AuctionEndedEvent.java`](file:///home/duong/Projects/Backend/DuAnTrainning/src/main/java/com/duong/auction/system/dto/event/AuctionEndedEvent.java)
- **Nhiệm vụ:** Là DTO (Data Transfer Object) chứa thông tin gói tin truyền từ Producer sang Consumer:
  - `eventId`: Mã UUID duy nhất cho từng gói tin (dùng chống đẻ trùng đơn hàng và soi log khi lỗi DLT).
  - `auctionId`, `winnerId`, `finalPrice`, `endedReason` ("TIMEOUT" hoặc "BUY_NOW"), `timestamp`.

---

### 4. File [`KafkaConfig.java`](file:///home/duong/Projects/Backend/DuAnTrainning/src/main/java/com/duong/auction/system/config/KafkaConfig.java)
- **Nhiệm vụ:** Cấu hình khởi tạo Topic và **Cơ chế Xử Lý Lỗi 3 Tầng**:
  - **Topic Chính:** `auction.events.ended` (3 partitions).
  - **Topic Retry (`auction.events.ended-retry`):** Khi Consumer bị lỗi DB/mạng tạm thời, tin nhắn tự chuyển sang Topic này để thử lại 3 lần (mỗi lần cách nhau 2s).
  - **Topic DLT (`auction.events.ended-dlt`):** Nếu thử lại 3 lần vẫn thất bại (do tin nhắn hỏng), tin nhắn bị cô lập vào "Nghĩa Địa Tin Nhắn DLT" để bắn Alert cho Admin kiểm tra thủ công.

---

### 5. File [`AuctionKafkaProducer.java`](file:///home/duong/Projects/Backend/DuAnTrainning/src/main/java/com/duong/auction/system/service/producer/AuctionKafkaProducer.java)
- **Nhiệm vụ:** Đóng vai trò là "Bưu tá" phát tin nhắn sự kiện.
- **Tính năng cao cấp:** Dùng `CompletableFuture.whenComplete()` để bắt phản hồi bất đồng bộ từ Kafka Broker:
  - Nếu gửi thành công $\rightarrow$ In log xác nhận `Topic`, `Partition`, `Offset`.
  - Nếu gửi thất bại $\rightarrow$ In log lỗi ngắt kết nối.

---

### 6. File [`AuctionEndedSettlementHelper.java`](file:///home/duong/Projects/Backend/DuAnTrainning/src/main/java/com/duong/auction/system/service/helper/AuctionEndedSettlementHelper.java)
- **Nhiệm vụ:** Helper xử lý chốt từng phiên đấu giá riêng biệt.
- **Tính năng cao cấp:** `@Transactional(propagation = Propagation.REQUIRES_NEW)` tạo giao dịch DB độc lập cho từng phiên thầu. Giải quyết triệt để 2 lỗi kiến trúc lớn (Self-Invocation & Blast Radius).

---

### 7. File [`AuctionScheduler.java`](file:///home/duong/Projects/Backend/DuAnTrainning/src/main/java/com/duong/auction/system/service/AuctionScheduler.java)
- **Nhiệm vụ:** Robot 10s gọi sang `settlementHelper` để chốt phiên hết giờ. Giải phóng hoàn toàn khâu tạo Đơn hàng đồng bộ.

---

### 8. File [`AuctionEndedConsumer.java`](file:///home/duong/Projects/Backend/DuAnTrainning/src/main/java/com/duong/auction/system/service/consumer/AuctionEndedConsumer.java)
- **Nhiệm vụ:** "Người nhận tin" ngầm phía sau:
  - Nhặt tin nhắn `AUCTION_ENDED`.
  - Kiểm tra Redis chống đẻ 2 đơn trùng (**Check-Then-Mark Pattern**).
  - Thực sự tạo bản ghi `Order` UNPAID 48h vào Database.
  - Chứa hàm `@DltHandler` hứng tin nhắn rác bị lỗi để bắn Alert.

---

# 🧠 4. BỐN BÀI TOÁN KIẾN TRÚC KINH ĐIỂN ĐÃ ĐƯỢC GIẢI QUYẾT

---

### 🧠 Bài Toán 1: Lỗi Dual-Write (Ghi DB vs Bắn Kafka lệch nhau)
- **Sự cố:** Nếu ghi DB `auction.setStatus(ENDED)` nhưng chưa commit mà đã bắn Kafka ngay $\rightarrow$ Khi DB bị nổ lỗi Rollback, Kafka đã lỡ gửi tin nhắn đi rồi $\rightarrow$ Lệch dữ liệu 2 bên!
- **Giải pháp đã làm:** Dùng `TransactionSynchronizationManager.afterCommit(...)`. **Chờ DB Commit thành công 100% rồi mới cho phép bắn Kafka Event!**

---

### 🧠 Bài Toán 2: Lỗi Spring AOP Self-Invocation (`this.method`)
- **Sự cố:** Trong Spring, nếu 1 method gọi tới 1 method khác trong **cùng 1 class** (`this.process(...)`), Spring AOP Proxy bị bỏ qua $\rightarrow$ Annotation `@Transactional(REQUIRES_NEW)` bị mất hiệu lực hoàn toàn!
- **Giải pháp đã làm:** Tách logic chốt phiên sang Spring Component riêng [`AuctionEndedSettlementHelper`](file:///home/duong/Projects/Backend/DuAnTrainning/src/main/java/com/duong/auction/system/service/helper/AuctionEndedSettlementHelper.java). Lời gọi đi qua Spring Bean Proxy $\rightarrow$ Kích hoạt `REQUIRES_NEW` chuẩn 100%!

---

### 🧠 Bài Toán 3: Bán Kính Ảnh Hưởng Lỗi (Transaction Blast Radius)
- **Sự cố:** Nếu bọc `@Transactional` ở hàm cha cho toàn bộ 1.000 phiên thầu $\rightarrow$ 1 phiên bị lỗi sẽ làm **Rollback oan 999 phiên hợp lệ còn lại**.
- **Giải pháp đã làm:** Dùng `@Transactional(propagation = Propagation.REQUIRES_NEW)` cho từng phiên trong Helper. Phiên nào lỗi chỉ rollback đúng phiên đó, các phiên khác vẫn commit và bắn Kafka bình thường!

---

### 🧠 Bài Toán 4: Lỗi Thứ Tự Đánh Dấu Redis Chống Trùng (Idempotency Timing Bug)
- **Sự cố:** Nếu đánh dấu `PROCESSED` vào Redis TRƯỚC KHI tạo Order $\rightarrow$ Khi tạo Order bị lỗi mạng tạm thời, Kafka đẩy tin nhắn sang Retry Topic để thử lại $\rightarrow$ Lượt thử lại đọc Redis thấy key `PROCESSED` đã tồn tại nên **bỏ qua luôn**, dẫn đến mất đơn hàng ngầm!
- **Giải pháp đã làm (Check-Then-Mark Pattern):** 
  - Bước 1: Đọc `redisTemplate.hasKey` kiểm tra trước.
  - Bước 2: Thực hiện tạo Order DB.
  - Bước 3: **Chỉ khi Bước 2 tạo Order thành công hoàn hảo mới ghi `set(key, "PROCESSED", 24h)` vào Redis!**
