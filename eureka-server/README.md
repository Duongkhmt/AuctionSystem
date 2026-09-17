# 🌐 EUREKA SERVER (SERVICE DISCOVERY REGISTRY)

## 1. Tổng Quan Dịch Vụ
`eureka-server` là **Trạm Đăng Ký & Định Vị Dịch Vụ Trung Tâm (Service Discovery Registry)** thuộc hệ thống Backend Đấu Giá Microservices, xây dựng trên nền tảng **Spring Cloud Netflix Eureka Server**.

Nhiệm vụ cốt lõi của `eureka-server`:
- Quản lý danh bạ địa chỉ IP/Port động của các microservice (`AUCTION-SERVICE`, `PAYMENT-SERVICE`).
- Theo dõi trạng thái sống/chết (Healthcheck) của từng service thông qua nhịp tim **Heartbeat 30 giây/lần**.
- Giúp `AUCTION-SERVICE` tự động tra cứu địa chỉ của `PAYMENT-SERVICE` mà không cần hardcode địa chỉ IP hay Cổng Port tĩnh.

---

## ⚙️ 2. Thông Số Cấu Hình

- **Cổng hoạt động (Port):** `8761`
- **Eureka Dashboard URL:** `http://localhost:8761`
- **Mã nguồn:** `Backend/eureka-server`
- **File cấu hình chính (`application.yml`):**
  ```yaml
  server:
    port: 8761

  eureka:
    instance:
      hostname: localhost
    client:
      register-with-eureka: false # Tắt tự đăng ký chính mình (Stand-alone Server)
      fetch-registry: false       # Tắt tự tải danh bạ chính mình
  ```

---

## 🚀 3. Hướng Dẫn Khởi Chạy

```bash
# Di chuyển vào thư mục eureka-server
cd Backend/eureka-server

# Biên dịch và khởi chạy bằng Maven Wrapper
./mvnw spring-boot:run
```

Sau khi chạy thành công, truy cập `http://localhost:8761` trên trình duyệt. Bạn sẽ thấy giao diện **System Status** và danh sách **Instances currently registered with Eureka**.
