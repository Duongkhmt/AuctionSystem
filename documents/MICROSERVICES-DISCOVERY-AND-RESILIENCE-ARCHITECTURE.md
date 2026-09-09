# DOCUMENTATION: KIẾN TRÚC MICROSERVICES, SERVICE DISCOVERY & RESILIENCE4J (TUẦN 7)

---

## 1. PHẠM VI CÔNG VIỆC & MỤC TIÊU HOÀN THÀNH

Tài liệu này quy chuẩn toàn bộ Kiến trúc **Service Discovery, Đa dịch vụ Microservices & Khả năng Phục hồi (Resilience4j)** cho Hệ thống Đấu Giá Trực tuyến, bao gồm 4 mục tiêu cốt lõi:

1. **Service Discovery với Spring Cloud Netflix Eureka Server**: Cấu hình trạm đăng ký dịch vụ trung tâm (Port `8761`), cho phép các dịch vụ tự động phát hiện IP/Port của nhau mà không hardcode URL.
2. **Phân tách Đa Dịch vụ (Microservices Architecture)**: Tách hệ thống thành 2 dịch vụ độc lập: **`AUCTION-SERVICE`** (Port `8080` - Sàn đấu giá chính) và **`PAYMENT-SERVICE`** (Port `8082` - Xử lý Ví tiền ảo & Thanh toán đơn hàng).
3. **Giao tiếp Liên Dịch vụ qua Spring Cloud OpenFeign**: Sử dụng `@FeignClient(name = "PAYMENT-SERVICE")` gọi API giữa các service thông qua tên dịch vụ định vị trên Eureka.
4. **Khả năng Phục hồi với Resilience4j (Circuit Breaker, Timeout & Fallback Mechanism)**: Giảm thiểu đáng kể rủi ro sập dây chuyền (Cascading Failure). Phân biệt rõ lỗi hạ tầng với lỗi nghiệp vụ; khi `PAYMENT-SERVICE` bị sập hoặc timeout > 3s, Circuit Breaker tự động ngắt mạch và kích hoạt **Hàm Fallback** để bảo vệ quyền lợi người mua và giữ hệ thống hoạt động mượt mà.

---

## 2. DANH MỤC TOÀN BỘ CÁC LỚP & THÀNH PHẦN (FULL COMPONENT CATALOG)

Dưới đây là danh sách đầy đủ tất cả các Class/Component cấu thành nên hạ tầng Microservices & Resilience:

| STT | Tên Lớp (Class Name) | Đường dẫn File (Location) | Vai trò & Nhiệm vụ chính trong Hệ thống |
| :---: | :---| :---| :---|
| **1** | `EurekaServerApplication` | `eureka-server/EurekaServerApplication.java` | **Trạm Đăng ký & Định vị Dịch vụ (Port 8761)**:<br>• Gắn nhãn `@EnableEurekaServer`.<br>• Quản lý danh sách IP/Port của tất cả Eureka Clients.<br>• Tự động kiểm tra sức khỏe (Heartbeat Health Check). |
| **2** | `PaymentServiceApplication` | `payment-service/PaymentServiceApplication.java` | **Microservice Quản lý Ví Tiền Ảo (Port 8082)**:<br>• Đăng ký tên `PAYMENT-SERVICE` lên Eureka.<br>• Trừ số dư Ví Ảo (Virtual Balance) thanh toán đơn hàng.<br>• Xử lý Unique Constraint `idempotency_key` triệt tiêu Race Condition trừ tiền 2 lần. |
| **3** | `PaymentController` | `payment-service/controller/PaymentController.java` | **Controller Xử lý Giao dịch Ví Tiền Ảo**:<br>• Endpoint `POST /v1/payments/process-order-payment`.<br>• Trả về HTTP 200 kèm `status = "INSUFFICIENT_BALANCE"` cho lỗi nghiệp vụ hết tiền (tránh kích hoạt nhầm Circuit Breaker). |
| **4** | [`AuctionServiceApplication`](file:///home/duong/Projects/Backend/DuAnTrainning/src/main/java/com/duong/auction/system/DuAnTrainningApplication.java) | `src/main/java/.../DuAnTrainningApplication.java` | **Microservice Đấu giá Chính (Port 8080)**:<br>• Gắn nhãn `@EnableFeignClients` *(Tự auto-configure Eureka Client)*.<br>• Quản lý Đấu giá, Đặt giá (Bid), Sản phẩm và Đơn hàng. |
| **5** | [`PaymentFeignClient`](file:///home/duong/Projects/Backend/DuAnTrainning/src/main/java/com/duong/auction/system/client/PaymentFeignClient.java) | `src/main/java/.../client/PaymentFeignClient.java` | **Interface Giao tiếp OpenFeign Client**:<br>• Khai báo `@FeignClient(name = "PAYMENT-SERVICE", fallback = PaymentFeignFallback.class)`.<br>• Truyền Header `Idempotency-Key` gọi API sang Service B qua tên Eureka. |
| **6** | [`PaymentFeignFallback`](file:///home/duong/Projects/Backend/DuAnTrainning/src/main/java/com/duong/auction/system/client/PaymentFeignFallback.java) | `src/main/java/.../client/PaymentFeignFallback.java` | **Xử lý Sự cố & Dự phòng (Fallback Mechanism)**:<br>• Implements `PaymentFeignClient`.<br>• Kích hoạt khi `PAYMENT-SERVICE` bị sập hoặc timeout quá 3s.<br>• Chuyển đơn hàng sang `PAYMENT_PENDING_RETRY` & gia hạn 24h. |
| **7** | [`ResilienceConfig`](file:///home/duong/Projects/Backend/DuAnTrainning/src/main/java/com/duong/auction/system/config/ResilienceConfig.java) | `src/main/java/.../config/ResilienceConfig.java` | **Cấu hình Resilience4j Circuit Breaker & Timeout**:<br>• Cấu hình `failure-rate-threshold: 50%` (Format kebab-case).<br>• Cấu hình Feign Timeout `connect-timeout: 3000ms` & `read-timeout: 3000ms`. |

---

## 3. BẢN ĐỒ KỊCH BẢN NGHIỆP VỤ & CODE MAPPING (CHI TIẾT MÔ TẢ & SOLUTION)

### Kịch bản 1: Đăng ký Dịch vụ Tự động (Service Discovery với Eureka)

* **Bài toán Nghiệp vụ**: Trong hệ thống phân tán, các Service có thể đổi IP hoặc mở rộng lên nhiều Instance. Nếu hardcode IP `192.168.1.5:8082` trong code thì khi đổi máy chủ hệ thống sẽ bị lỗi hàng loạt.
* **Giải pháp Solution**:
  1. Khởi chạy `eureka-server` ở Port `8761`.
  2. Cả `AUCTION-SERVICE` (`8080`) và `PAYMENT-SERVICE` (`8082`) khai báo `spring.application.name` và tự động phát nhịp tim (Heartbeat 30s/lần) gửi về Eureka.
  3. Khi Service A muốn gọi Service B, Service A chỉ cần hỏi Eureka: *"Service 'PAYMENT-SERVICE' đang ở IP/Port nào?"* ➔ Eureka tự động trả về vị trí chính xác.

---

### Kịch bản 2: Thanh toán Đơn hàng Thắng Đấu Giá qua OpenFeign (Phân biệt Lỗi Nghiệp vụ & Lỗi Hạ tầng)

* **Bài toán Nghiệp vụ**: Khi phiên đấu giá kết thúc, người thắng cuộc (Winner) bấm **"Thanh toán Đơn hàng bằng Số dư Ví Ảo"** (`POST /v1/bidders/orders/{orderId}/pay`).
* **Phân biệt Lỗi Nghiệp vụ vs Lỗi Hạ tầng**:
  - **Lỗi Nghiệp vụ (Business Error - Không đủ số dư ví ảo)**: `PAYMENT-SERVICE` hoạt động bình thường, kiểm tra thấy ví người dùng chỉ có 5 triệu nhưng đơn hàng 15 triệu. `PAYMENT-SERVICE` trả về **`HTTP 200 OK`** kèm `status = "INSUFFICIENT_BALANCE"`. `AUCTION-SERVICE` hiển thị lỗi rõ ràng cho người dùng: *"Số dư ví của bạn không đủ, vui lòng nạp thêm tiền!"* ➔ **KHÔNG KÍCH HOẠT FALLBACK GIA HẠN 24H**, không làm Circuit Breaker đếm lỗi nhầm.
  - **Lỗi Hạ tầng (Infrastructure Error - Service B sập/Timeout)**: Mất kết nối HTTP, `PAYMENT-SERVICE` đứt mạng hoặc timeout > 3s ➔ Lúc này mới kích hoạt Circuit Breaker & Fallback.

---

### Kịch bản 3: Resilience4j Circuit Breaker & Fallback khi `PAYMENT-SERVICE` Bị Sập / Timeout

* **Bài toán Nghiệp vụ**: Dịch vụ Ví Tiền Ảo (`PAYMENT-SERVICE`) bị ngắt kết nối hoặc tắt nguồn (`Port 8082` unreachable) hoặc phản hồi quá chậm (> 3s). Nếu không có cấu hình Timeout và Circuit Breaker, request của người mua bị treo xoay tròn 60s làm cạn kiệt Thread Pool của Server.
* **Giải pháp Solution (Cấu hình Feign Timeout + Fallback Gia hạn 24h)**:
  - Class: [`PaymentFeignFallback`](file:///home/duong/Projects/Backend/DuAnTrainning/src/main/java/com/duong/auction/system/client/PaymentFeignFallback.java)
  - Logic chi tiết:
    1. Cấu hình `connect-timeout: 3000ms` và `read-timeout: 3000ms` cắt đuôi ngay lập tức các request treo quá 3 giây.
    2. Khi `PAYMENT-SERVICE` bị sập hoặc timeout 3s quá $50\%$ số lượt gọi (`failure-rate-threshold: 50`), Resilience4j Circuit Breaker ngắt mạch sang **`OPEN`**.
    3. Resilience4j lập tức chuyển hướng luồng xử lý vào hàm Fallback `PaymentFeignFallback.processPayment(...)`.
    4. Hàm Fallback thực hiện **Ghi nhận Thanh toán Treo & Tự động Gia hạn 24h**:
       ```java
       return PaymentResponseDTO.builder()
               .status("PENDING_RETRY")
               .paymentDeadlineExtended(true)
               .message("Dịch vụ Ví tiền ảo hiện đang bảo trì. Đơn hàng của bạn đã được ghi nhận và tự động gia hạn thêm 24h để thanh toán lại!")
               .build();
       ```
    5. `OrderService` cập nhật trạng thái đơn hàng thành `PAYMENT_PENDING_RETRY`, tự động cộng thêm $24\text{ giờ}$ vào deadline thanh toán và phản hồi `HTTP 200` mượt mà cho Client.
    6. **Sau 10 giây (wait-duration-in-open-state)**: Circuit Breaker chuyển sang trạng thái **`HALF_OPEN`** để thử gửi lại request kiểm tra xem `PAYMENT-SERVICE` đã sống lại chưa.

---

## 4. BẢNG PHÂN TÍCH ƯU & NHƯỢC ĐIỂM CỦA GIẢI PHÁP (PROS & CONS ANALYSIS)

| Tiêu chí Phân tích | 🟢 ƯU ĐIỂM (PROS) | 🔴 NHƯỢC ĐIỂM & HẠN CHẾ KỸ THUẬT (CONS) |
| :--- | :--- | :--- |
| **1. Khả năng Chống Sập (Fault Tolerance)** | • **Giảm thiểu đáng kể rủi ro Sập dây chuyền (Cascading Failure)**: Ngắt mạch khẩn cấp khi `PAYMENT-SERVICE` quá tải hoặc nghẽn thread. | • **Vẫn tồn tại vùng biên (Edge Cases)**: Trong cửa sổ `minimum-number-of-calls` đầu tiên khi CB chưa kịp đếm đủ lỗi, hoặc nếu Service B bị chậm nhưng vẫn trả về HTTP 200 thì CB chưa ngắt mạch ngay. |
| **2. Trải nghiệm Người dùng (UX)** | • **Suy giảm êm ái (Graceful Degradation)**: Phân biệt rõ lỗi thiếu tiền (hiển thị thông báo nạp tiền ngay) với lỗi hạ tầng sập mạng (tự động gia hạn 24h). | • **Tính nhất quán chậm (Eventual Consistency)**: Đơn hàng khi sập dịch vụ ví không chuyển `PAID` ngay mà chuyển sang trạng thái chờ `PENDING_RETRY`. |
| **3. Định tuyến Dịch vụ (Discovery)** | • **Linh hoạt 100%**: Không hardcode IP/Port. Có thể bật/tắt hoặc đổi máy chủ Service B mà Service A không cần sửa $1\text{ dòng code}$. | • **Phụ thuộc Eureka Server**: Nếu Eureka Server bị sập (và không có cấu hình Caching Registry ở Client), các Service sẽ gặp khó khăn khi tìm thấy nhau. |
| **4. Tự trị Tiền tệ & An toàn (Security)** | • **Triệt tiêu Race Condition trừ tiền 2 lần**: Dùng ràng buộc `UNIQUE constraint` trên `idempotency_key` ở cấp CSDL Cổng Thanh Toán. | • **Cần quản lý Vòng đời Hết hạn 24h**: Phải có Scheduled Job quét tự động hủy đơn và ghi phạt gậy bùng đơn nếu quá 24h không thanh toán được. |

## 6. CẤU TRÚC PHÂN CHIA VÀ KHỞI CHẠY 2 SERVICE THỰC TẾ

```
Projects/
├── eureka-server/                     (PORT 8761 - Discovery Registry Server)
│   ├── src/main/java/.../EurekaServerApplication.java
│   └── src/main/resources/application.yml
│
├── payment-service/                  (PORT 8082 - Service B: Dịch vụ Ví Tiền Ảo)
│   ├── src/main/java/.../PaymentServiceApplication.java
│   ├── src/main/java/.../controller/PaymentController.java
│   └── src/main/resources/application.yml
│
└── DuAnTrainning/ (AUCTION-SERVICE)   (PORT 8080 - Service A: Sàn Đấu giá Chính)
    ├── src/main/java/.../DuAnTrainningApplication.java
    ├── src/main/java/.../client/PaymentFeignClient.java
    ├── src/main/java/.../client/PaymentFeignFallback.java
    ├── src/main/java/.../service/OrderService.java
    └── src/main/resources/application.yml
```

---

## 7. KẾ HOẠCH BẢN ĐỒ THỰC THI TỪNG BƯỚC (STEP-BY-STEP ROADMAP)

### 📍 BƯỚC 1: Dựng Eureka Server (`eureka-server` - Port 8761)
1. Khởi tạo ứng dụng Spring Boot với dependency `spring-cloud-starter-netflix-eureka-server`.
2. Trong file `EurekaServerApplication.java`, thêm annotation `@EnableEurekaServer`.
3. Trong `application.yml`, cấu hình `server.port = 8761`, `eureka.client.register-with-eureka = false` và `fetch-registry = false`.
4. Khởi chạy và kiểm tra Dashboard tại `http://localhost:8761`.

### 📍 BƯỚC 2: Dựng Payment Service (`payment-service` - Port 8082)
1. Khởi tạo ứng dụng Spring Boot với `spring-boot-starter-web` và `spring-cloud-starter-netflix-eureka-client`.
2. Trong `application.yml`, đặt `spring.application.name = PAYMENT-SERVICE` và `server.port = 8082`.
3. Viết `PaymentController.java` xử lý `POST /v1/payments/process-order-payment`:
   - Kiểm tra `Unique Constraint` cột `idempotency_key` trong DB. Nếu vi phạm trùng lặp do 2 request chạy đồng thời ➔ Trả lại kết quả thành công trước đó (Triệt tiêu Race Condition).
   - Nếu không đủ số dư ví ảo ➔ Trả HTTP 200 kèm `status = "INSUFFICIENT_BALANCE"`.
4. Khởi chạy service ➔ Kiểm tra Dashboard `http://localhost:8761` thấy `PAYMENT-SERVICE` báo `UP`.

### 📍 BƯỚC 3: Cấu hình `AUCTION-SERVICE` (`auction-service` - Port 8080)
1. Thêm các dependency vào `pom.xml`:
   - `spring-cloud-starter-netflix-eureka-client`
   - `spring-cloud-starter-openfeign`
   - `spring-cloud-starter-circuitbreaker-resilience4j`
2. Trong `DuAnTrainningApplication.java`, chỉ cần thêm `@EnableFeignClients` *(Từ Spring Boot 3.x / Spring Cloud 2020+, `@EnableEurekaClient` không còn cần thiết)*.
3. Trong `application.yml`, cấu hình `feign.client.config.PAYMENT-SERVICE` (connect-timeout 3s, read-timeout 3s) và chỉ số Resilience4j theo chuẩn kebab-case.

### 📍 BƯỚC 4: Viết OpenFeign Client & Fallback trong `AUCTION-SERVICE`
1. Tạo Interface `PaymentFeignClient.java` với `@FeignClient(name = "PAYMENT-SERVICE", fallback = PaymentFeignFallback.class)`.
2. Tạo Class `PaymentFeignFallback.java` implements `PaymentFeignClient`, trả về `PENDING_RETRY` kèm `paymentDeadlineExtended = true`.

### 📍 BƯỚC 5: Tích hợp Feign Client vào `OrderService.java`
1. Inject `PaymentFeignClient` vào [`OrderService`](file:///home/duong/Projects/Backend/DuAnTrainning/src/main/java/com/duong/auction/system/service/OrderService.java).
2. Khi người mua bấm nút Thanh toán đơn hàng:
   - Truyền `Idempotency-Key` duy nhất cho đơn hàng.
   - Gọi `paymentFeignClient.processPayment(idempotencyKey, request)`.
   - Nếu `SUCCESS` ➔ Chuyển đơn thành `PAID`.
   - Nếu `INSUFFICIENT_BALANCE` ➔ Hiển thị lỗi thiếu tiền ví ảo, **KHÔNG GIA HẠN 24H**.
   - Nếu `PENDING_RETRY` (Fallback) ➔ Chuyển đơn thành `PAYMENT_PENDING_RETRY`, tự động cộng 24h vào thời hạn thanh toán.

### 📍 BƯỚC 6: Kiểm thử & Vòng đời Hết hạn (Testing & Expiration Lifecycle)
1. **Test Thử lại Chủ động (Passive Retry)**: Người mua bấm "Thanh toán lại" trên UI ➔ Gửi lại cùng `Idempotency-Key`.
2. **Test Thử lại Tự động (Active Retry Scheduled Job)**: Thêm 1 `@Scheduled` Job chạy định kỳ 15m quét các đơn `PAYMENT_PENDING_RETRY` để thử lại khi `PAYMENT-SERVICE` sống lại.
3. **Test Hết hạn 24h (Order Expiration Job)**: Quét các đơn `PAYMENT_PENDING_RETRY` có `deadline < NOW()` ➔ Chuyển trạng thái `EXPIRED_CANCELLED`, phạt gậy bùng đơn người mua và mời người trả giá cao thứ 2 nhận quyền mua.

---

## 8. NÂNG CAO NGHIỆP VỤ: XỬ LÝ RACE CONDITION, PHÂN LOẠI LỖI & VÒNG ĐỜI HẾT HẠN 24H

Để bảo đảm tính tự trị và an toàn tài chính tuyệt đối trong hệ thống thanh toán ví tiền ảo, dự án giải quyết 3 bài toán nghiệp vụ nâng cao:

### 8.1. Triệt tiêu Race Condition khi kiểm tra Idempotency Key (Chống Trừ Số Dư Ví 2 Lần)
* **Vấn đề Chống Lặp (Check-then-act Vulnerability)**:
  Nếu 2 request (Ví dụ: Người dùng bấm "Thanh toán lại" trên giao diện cùng lúc Scheduled Job tự động quét lại đơn) gửi lên `PAYMENT-SERVICE` gần như đồng thời:
  - Nếu làm theo cách ngây thơ "Check DB xem có chưa ➔ Chưa có ➔ Trừ tiền ➔ Lưu DB": Cả 2 request sẽ cùng check thấy "Chưa có", và cả 2 đều trừ số dư ví ảo ➔ **User bị trừ tiền 2 lần!**
* **Giải pháp Tự trị 100% (Atomic Unique Constraint Enforcement)**:
  1. Trong CSDL của `PAYMENT-SERVICE`, cột `idempotency_key` trong bảng `payment_transactions` được thiết lập ràng buộc duy nhất: **`CONSTRAINT uk_idempotency_key UNIQUE (idempotency_key)`**.
  2. Khi 2 request nộp song song, CSDL PostgreSQL bắt buộc chỉ cho phép $1\text{ request}$ chèn thành công. Request thứ 2 sẽ bị nổ lỗi vi phạm khóa duy nhất (`DataIntegrityViolationException`).
  3. `PAYMENT-SERVICE` bắt lỗi này và lập tức chuyển hướng đọc lại kết quả của request thứ nhất ➔ **Triệt tiêu 100% rủi ro Race Condition trừ tiền trùng lặp!**

---

### 8.2. Phân biệt Lỗi Hạ Tầng (Service Sập) vs Lỗi Nghiệp Vụ Hợp Lệ (Không Đủ Số Dư Ví)
* **Vấn đề Nhầm lẫn Circuit Breaker**:
  Circuit Breaker chỉ được phép ngắt mạch khi có **Lỗi Hạ Tầng** (Sập service, đứt mạng, Timeout > 3s). Nếu người dùng không đủ số dư ví ảo mà `PAYMENT-SERVICE` ném Exception 500 ➔ Circuit Breaker sẽ đếm đây là $1\text{ lỗi hạ tầng}$, dẫn tới ngắt mạch nhầm và nhảy vào Fallback thông báo *"Dịch vụ bảo trì, đã gia hạn 24h"*. Điều này làm sai lệch sự thật nghiệp vụ!
* **Giải pháp Phân loại Lỗi**:
  - Khi người dùng **Không đủ số dư ví ảo**, `PAYMENT-SERVICE` trả về **`HTTP 200 OK`** kèm DTO:
    ```json
    {
      "status": "INSUFFICIENT_BALANCE",
      "message": "Số dư ví ảo không đủ để thanh toán đơn hàng!",
      "requiredAmount": 15000000,
      "currentBalance": 5000000
    }
    ```
  - Vì là `HTTP 200`, OpenFeign không coi đây là lỗi hạ tầng (không đếm vào failure rate của Circuit Breaker).
  - `AUCTION-SERVICE` nhận được `INSUFFICIENT_BALANCE` sẽ hiển thị ngay thông báo lỗi cho người dùng: *"Ví ảo của bạn không đủ tiền, vui lòng nạp thêm!"* ➔ **KHÔNG GIA HẠN 24H SAU SỰ THẬT!**

---

### 8.3. Vòng đời Hết hạn 24h & Xử lý Đơn hàng Hủy (`Order Expiration Lifecycle`)
* **Bài toán Đơn hàng Treo**: Nếu sau 24h gia hạn mà đơn hàng vẫn ở trạng thái `PAYMENT_PENDING_RETRY` (do `PAYMENT-SERVICE` sập kéo dài hoặc người dùng ngó lơ không nạp tiền ví ảo), hệ thống phải có quy trình giải phóng đơn hàng:
* **Quy trình Xử lý Tự động**:
  1. Một Scheduled Job `OrderExpirationScheduler` chạy định kỳ (ví dụ 1 tiếng/lần) trong `AUCTION-SERVICE` quét các đơn `PAYMENT_PENDING_RETRY` có `deadline < NOW()`.
  2. Chuyển trạng thái đơn hàng thành **`EXPIRED_CANCELLED`** (Đơn hàng bị hủy do hết hạn).
  3. Ghi nhận phạt **1 gậy bùng đơn** vào tài khoản người mua (`unpaid_strike_count + 1`).
  4. **Chính sách Mở lại Đấu Giá**: Đẩy sự kiện Kafka / Notification mời **Người trả giá cao thứ nhì (Second-Highest Bidder)** nhận quyền mua sản phẩm hoặc mở lại phiên đấu giá mới.
