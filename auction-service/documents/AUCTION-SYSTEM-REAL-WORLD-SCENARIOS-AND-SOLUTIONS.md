# BÁO CÁO CÁC KỊCH BẢN THỰC TẾ & GIẢI PHÁP TỐI ƯU HỆ THỐNG ĐẤU GIÁ (AUCTION PLATFORM REAL-WORLD SCENARIOS)

---

# PHẦN 1: BÀI TOÁN ĐẶT GIÁ CAO VẬN TỐC (PLACE BID HIGH-THROUGHPUT)

### 1. Vấn Đề Của Bài Toán Đặt Giá
Trong 10 giây cuối cùng của phiên đấu giá, hàng nghìn người dùng cùng bấm nút **Đặt Giá** tại đúng mốc 1 mili-giây.

Nếu dùng cơ chế ghi đĩa PostgreSQL truyền thống:
- Tất cả request cùng đâm xuống Database mở `@Transactional` và khóa dòng (Row-level Lock).
- **Hậu quả:** Request đứng chờ ➔ Bị ném lỗi **409 Lock Timeout** hoặc sập Database do cạn DB Connection Pool.

---

### ⚡ 2. Giải Pháp Tối Ưu: Redis Atomic Lua Script

Gộp 3 thao tác **Đọc ➔ Kiểm tra ➔ Cập nhật giá** thành một thao tác nguyên tử (Atomic) duy nhất, thực thi bằng Lua script trên RAM Redis. 

Vì Redis xử lý lệnh theo cơ chế **Đơn luồng (Single-thread)**, toàn bộ đoạn script chạy trọn vẹn mà không bị request khác chen vào giữa quá trình kiểm tra và ghi dữ liệu:
- Dù hàng trăm request đặt giá đồng thời, mỗi request vẫn được so sánh với giá hiện tại mới nhất tại đúng thời điểm nó được xử lý, tránh triệt để **Race Condition** và đảm bảo dữ liệu đấu giá luôn nhất quán.
- Những request có giá thấp hơn sẽ nhận kết quả **"thua giá"** ngay lập tức, không phải chờ đợi hay bị từ chối do lỗi tranh chấp hệ thống.

---

# PHẦN 2: BÀI TOÁN KẾT THÚC PHIÊN & GỬI EMAIL THÔNG BÁO THẮNG THẦU (KAFKA EVENT-DRIVEN & EMAIL)

### 1. Vấn Đề Của Bài Toán Kết Thúc Đấu Giá (`AUCTION_ENDED`)
Khi một phiên đấu giá kết thúc (do hết giờ hoặc người dùng Mua Ngay), hệ thống phải vừa đổi trạng thái phiên thầu, vừa đẻ đơn hàng `Order` UNPAID 48h, vừa gửi Email thông báo trúng thầu cho Winner.

- **Nghẽn dây chuyền:** Khi có 100 phiên đấu giá cùng hết hạn tại một thời điểm, nếu mỗi phiên phải gọi dịch vụ Email SMTP ➔ Robot bị nghẽn đứng trong vài phút.
- **Sập dây chuyền (Cascade Failure):** Nếu dịch vụ gửi Email bị ngắt mạng ➔ Lỗi ném ra làm **Rollback** toàn bộ giao dịch ➔ Winner thắng hợp lệ nhưng bị mất đơn hàng oan!

---

### 🟢 2. Giải Pháp Kafka Event-Driven & Email Notification

- Luồng chính (`AuctionScheduler.java`) chỉ làm đúng việc chốt trạng thái phiên thầu sang `ENDED` trong CSDL ➔ Đăng ký phát sự kiện `AuctionEndedEvent` lên Kafka qua `TransactionSynchronization.afterCommit`. Robot rảnh tay ngay lập tức để phục vụ các phiên khác.
- Phía Consumer ngầm (`AuctionEndedConsumer.java`) nhặt sự kiện từ Kafka và **tự động đẻ bản ghi `Order` 48h vào DB ngầm + Gọi EmailService phát Email chúc mừng cho Winner** phía sau.

---

# PHẦN 3: BẢNG TỔNG HỢP 5 KỊCH BẢN THỰC TẾ & GIẢI PHÁP ĐÃ XỬ LÝ TRONG CODEBASE

```
+---------------------------------------------------+---------------------------------------------------+---------------------------------------------------+
| KỊCH BẢN RỦI RO THỰC TẾ                           | NGUYÊN NHÂN NỐI TẠI                               | GIẢI PHÁP ĐÃ XỬ LÝ TRONG CODEBASE                |
+---------------------------------------------------+---------------------------------------------------+---------------------------------------------------+
| 1. Chốt phiên hàng loạt nghẽn Robot               | Xử lý tạo đơn & gửi Mail đồng bộ trong Thread chính| Tách luồng Kafka bất đồng bộ (Decoupling)          |
| 2. Mất đồng bộ DB vs Kafka (Dual-Write)           | Gửi Kafka trước khi DB commit thành công          | TransactionSynchronization.afterCommit            |
| 3. Mạng chập chờn phát lại tin nhắn Kafka         | Consumer rớt mạng chưa kịp gửi Ack                | Redis Idempotency Check (kafka:processed_event)   |
| 4. Tin nhắn độc / Hỏng mạng SMTP                  | Dịch vụ Mail/DB bị sập tạm thời                   | Non-blocking Retry 3 lần & Dead Letter Topic (DLT)|
| 5. Lệch trạng thái "SẮP DIỄN RA" / "ĐANG ĐẤU GIÁ" | Redis Cache auctions::id bị cũ không xóa          | @CacheEvict(value = "auctions") trong Scheduler   |
+---------------------------------------------------+---------------------------------------------------+---------------------------------------------------+
```
