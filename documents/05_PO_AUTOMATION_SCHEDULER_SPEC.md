# 📋 PRD 05: CHI TIẾT TỰ ĐỘNG HÓA HỆ THỐNG NGẦM (AUTOMATION SCHEDULER SPECIFICATION)

> **Role**: Product Owner (PO) / Database Performance Architect  
> **Module**: Automation Scheduler & Background Workers  
> **Version**: 2.0.0 (Enterprise Specification)

---

## ⚙️ 1. MÔ TẢ QUY TRÌNH CHỦ DỘNG CỦA ROBOT SCHEDULER

Robot `AuctionScheduler` đóng vai trò là **người điều hành thời gian hệ thống**, vận hành ngầm **10 giây 1 lần (`fixedRate = 10000ms`)** để thực thi 3 câu lệnh SQL Bulk Update trực tiếp dưới Database:

```sql
-- 1. Kích hoạt bài đấu giá hẹn giờ đã đến phút khởi tranh
UPDATE auctions SET status = 'RUNNING' 
WHERE status = 'SCHEDULED' 
  AND start_time <= NOW() 
  AND product_id IN (SELECT id FROM products WHERE status = 'APPROVED');

-- 2. Đóng các phiên đấu giá thường đã hết giờ
UPDATE auctions SET status = 'ENDED' 
WHERE status = 'RUNNING' 
  AND end_time <= NOW();

-- 3. Đánh nhãn EXPIRED các phiên Mua Ngay hết hạn 30 ngày mà không ai mua
UPDATE auctions SET status = 'EXPIRED' 
WHERE status = 'RUNNING' 
  AND auction_type = 'BUY_NOW' 
  AND end_time <= NOW();
```

---

## 🚀 2. TIÊU CHUẨN HIỆU NĂNG VÀ BENCHMARK (NON-FUNCTIONAL SPECIFICATIONS)

### A. Chuẩn Tối Ưu Hóa Tránh Phình RAM Heap (Zero Memory Footprint)
- **Cấm tuyệt đối**: Không được dùng JPA Repository `findAll()` kéo hàng ngàn Entity lên bộ nhớ RAM rồi lặp Java `for-each` để thay đổi trạng thái từng bài.
- **Tiêu chuẩn đạt được**: 
  - Tốc độ thực thi SQL Bulk Update: **~1ms - 5ms**.
  - Dung lượng RAM cấp phát cho Scheduler: **0 MB**.

### B. Ma Trận Kịch Bản Chuyển Trạng Thái Tự Động (Automation Decision Matrix)

| Loại hình bài | Trạng thái ban đầu | Điều kiện thời gian kích hoạt | Trạng thái đích | Ghi chú nghiệp vụ |
| :--- | :---: | :--- | :---: | :--- |
| `ENGLISH` / `RESERVE` | `SCHEDULED` | `startTime <= NOW()` | `RUNNING` | Bắt đầu cho phép Bidder nhảy vào đặt giá |
| `ENGLISH` / `RESERVE` | `RUNNING` | `endTime <= NOW()` | `ENDED` | Chốt người bid cao nhất thắng cuộc |
| `BUY_NOW` | `RUNNING` | `endTime <= NOW()` (Tròn 30 ngày) | `EXPIRED` | Không có ai mua ➔ Chờ Seller bấm Relist |
