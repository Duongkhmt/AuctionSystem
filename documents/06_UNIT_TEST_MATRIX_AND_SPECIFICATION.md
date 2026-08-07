# Tài Liệu Quản Lý Kịch Bản Kiểm Thử Unit Test (PO & Developer Test Matrix)

> **Mục tiêu tài liệu**: 
> 1. Dành cho **Product Owner (PO)**: Theo dõi tiến độ phủ test, đảm bảo 100% quy tắc nghiệp vụ (Business Rules) được kiểm thử tự động.
> 2. Dành cho **Developer mới Onboarding**: Nắm rõ toàn bộ kịch bản test cần viết, quy chuẩn mã nguồn và hiện trạng triển khai của toàn bộ dự án.

---

## 📊 1. Báo Cáo Tiến Độ Phủ Kịch Bản Test Của Toàn Bộ Dự Án (PO Dashboard)

| Phân Hệ / Thành Phần                            | Tổng Kịch Bản | Đã Hoàn Thành `[x]` | Chưa Thực Hiện `[ ]` | Tỷ Lệ Hoàn Thành |
|:------------------------------------------------|:-------------:|:-------------------:|:--------------------:|:----------------:|
| **A. BiddingService** (Đấu giá & Mua ngay)      |       9       |          9          |          0           |       100%       |
| **B. ProductService** (Sản phẩm & Duyệt bài)    |       8       |          8          |          0           |       100%       |
| **C. OrderService** (Đơn hàng & Thanh toán)     |       7       |          7          |          0           |       100%       |
| **D. CategoryService** (Danh mục hoạt động)     |       1       |          1          |          0           |       100%       |
| **E. AuctionScheduler** (Tự động mở/đóng thầu)  |       4       |          3          |          1           |       75%        |
| **F. CloudinaryService** (Quản lý ảnh)          |       4       |          0          |          4           |        0%        |
| **G. Core Engine & Helpers** (`service/helper`) |      10       |          5          |          5           |       50%        |
| **H. Validators** (`validator`)                 |       9       |          0          |          9           |        0%        |
| **TỔNG CỘNG TOÀN DỰ ÁN**                        |    **52**     |        **33**       |        **19**        |    **63.5%**     |

---

## 📋 2. Ma Trận Kịch Bản Kiểm Thử Chi Tiết (Test Scenarios Matrix)

### A. Phân Hệ Đấu Giá & Mua Ngay (`BiddingService`)

#### 1. Phương thức `getAuctionBidHistory(Long auctionId)` - Xem lịch sử đấu giá
- [x] **TS-BID-01** (Negative): Lấy lịch sử thất bại khi `auctionId` không tồn tại -> Ném `ApplicationException` (`AUCTION_NOT_FOUND`), verify không gọi DB lấy Bid.
- [x] **TS-BID-02** (Positive): Lấy lịch sử thành công khi `auctionId` hợp lệ -> Trả về danh sách `BidHistoryResponseDTO` tương ứng.

#### 2. Phương thức `placeBid(Long bidderId, Long auctionId, BidRequestDTO requestDTO)` - Đặt giá thầu
- [x] **TS-BID-03** (Negative): Đặt giá thất bại do người dùng (`bidderId`) không tồn tại trong hệ thống.
- [x] **TS-BID-04** (Negative): Đặt giá thất bại do phiên đấu giá (`auctionId`) không tồn tại.
- [x] **TS-BID-05** (Positive): Đặt giá thành công bình thường (Không nằm trong cửa sổ Anti-sniping) -> Giá thầu cập nhật, lưu vết `Bid`.
- [x] **TS-BID-06** (Positive - Soft Close): Đặt giá thành công trong 3 phút cuối (Anti-sniping window) -> Tự động gia hạn thời gian kết thúc (`endTime`) thêm 3 phút.
- [x] **TS-BID-07** (Positive - Proxy Bidding): Đặt giá với `maxAutoBidAmount` -> Gọi Proxy Bidding Engine tự động tính toán giá thầu chiến thắng mới.

#### 3. Phương thức `executeBuyNow(Long bidderId, Long auctionId)` - Mua ngay
- [x] **TS-BID-08** (Negative): Mua ngay thất bại do User hoặc Auction không tồn tại.
- [x] **TS-BID-09** (Positive): Mua ngay thành công -> Trạng thái phiên thầu đổi sang `ENDED`, gán `winner` cho `bidder`, chốt giá bằng `buyNowPrice`.

--- 

### B. Phân Hệ Quản Lý Sản Phẩm & Duyệt Bài (`ProductService`)

#### 1. Phương thức `createProduct(...)` - Đăng bán sản phẩm
- [x] **TS-PROD-01** (Negative): Đăng bán thất bại do Danh mục (`Category`) không tồn tại.
- [x] **TS-PROD-02** (Negative): Đăng bán thất bại do Người bán (`Seller`) không tồn tại hoặc bị khóa tài khoản.
- [x] **TS-PROD-03** (Positive): Đăng bán thành công -> Sản phẩm lưu DB với trạng thái chờ duyệt (`PENDING`), lưu danh sách ảnh tương ứng.

#### 2. Phương thức `approveProduct(Long productId)` - Duyệt bài (Admin/Mod)
- [x] **TS-PROD-04** (Negative): Duyệt bài thất bại do Sản phẩm không tồn tại.
- [x] **TS-PROD-05** (Negative): Duyệt bài thất bại do Sản phẩm không ở trạng thái `PENDING`.
- [x] **TS-PROD-06** (Positive): Duyệt bài thành công -> Trạng thái đổi sang `ACTIVE`, khởi tạo phiên thầu tương ứng.

#### 3. Phương thức `rejectProduct(Long productId, String reason)` - Từ chối bài đăng
- [x] **TS-PROD-07** (Negative): Từ chối thất bại do Sản phẩm không tồn tại.
- [x] **TS-PROD-08** (Positive): Từ chối thành công -> Trạng thái đổi sang `REJECTED`, lưu lý do từ chối.

---

### C. Phân Hệ Tự Động Hóa Phiên Đấu Giá (`AuctionScheduler`)

- [ ] **TS-SCHED-01** (Positive): Tự động mở các phiên thầu đã đến giờ bắt đầu (`startPendingAuctions`) -> Chuyển từ `WAITING` sang `ACTIVE`.
- [ ] **TS-SCHED-02** (Positive): Tự động đóng các phiên thầu đã hết giờ (`closeExpiredAuctions`) -> Chuyển từ `ACTIVE` sang `ENDED`.
- [ ] **TS-SCHED-03** (Positive): Tự động xác định người chiến thắng khi kết thúc thầu.
- [ ] **TS-SCHED-04** (Edge Case): Tự động đóng phiên thầu không có ai đặt giá -> Chuyển sang `ENDED` với `winner = null`.

---

### D. Phân Hệ Dịch Vụ Lưu Trữ Hình Ảnh (`CloudinaryService`)

- [ ] **TS-IMG-01** (Positive): Tải lên danh sách ảnh thành công -> Trả về danh sách `secure_url` và `public_id`.
- [ ] **TS-IMG-02** (Negative): Tải ảnh thất bại do lỗi I/O hoặc API Cloudinary -> Ném `ApplicationException` (`IMAGE_UPLOAD_FAILED`) và tự động dọn dẹp (delete) các ảnh đã tải dở.
- [ ] **TS-IMG-03** (Positive): Xóa ảnh theo `publicId` hợp lệ -> Gọi Cloudinary API destroy.
- [ ] **TS-IMG-04** (Edge Case): Xóa ảnh với `publicId` null hoặc rỗng -> Bỏ qua không gọi Cloudinary API.

---

### E. Phân Hệ Thuật Toán & Helper Core Engine (`service/helper`)

#### 1. `ProxyBiddingEngineHelper` (Thuật toán nhảy giá tự động)
- [ ] **TS-HELP-01**: Đặt giá thủ công bình thường (không cài Max Auto Bid) -> Giá thầu mới = Giá hiện tại + Bước giá.
- [ ] **TS-HELP-02**: Đặt giá thủ công lớn hơn Max Auto Bid của người trước -> Người mới trở thành Winner với giá mới = Max Auto Bid của người cũ + Bước giá.
- [ ] **TS-HELP-03**: Đặt giá Auto Bid mới cao hơn Auto Bid cũ -> Người mới trở thành Winner, hệ thống sinh ra 2 bản ghi Bid Audit Trail tương ứng.
- [ ] **TS-HELP-04**: Đặt giá Auto Bid mới bằng hoặc thấp hơn Auto Bid cũ -> Người cũ tiếp tục giữ vị trí Winner với giá = Max Auto Bid mới + Bước giá.

#### 2. `BidStepCalculatorHelper` (Tính toán bước giá tối thiểu)
- [ ] **TS-HELP-05**: Tính bước giá cho khung sản phẩm giá thấp (< 1.000.000 VNĐ).
- [ ] **TS-HELP-06**: Tính bước giá cho khung sản phẩm giá trung bình (1.000.000 - 10.000.000 VNĐ).
- [ ] **TS-HELP-07**: Tính bước giá cho khung sản phẩm giá trị cao (> 10.000.000 VNĐ).

#### 3. `ProductAuctionLookupHelper` & Response Helpers
- [ ] **TS-HELP-08**: Tìm kiếm thông tin Auction theo cả Auction ID và Product ID thành công.
- [ ] **TS-HELP-09**: Map dữ liệu `ProductResponseHelper` đầy đủ thông tin ảnh, giá hiện tại, đếm số lượt thầu.
- [ ] **TS-HELP-10**: Map dữ liệu `BidResponseHelper` hiển thị đúng trạng thái thắng/thua và cảnh báo hết giờ.

---

### F. Phân Hệ Kiểm Trả Ràng Buộc Nghiệp Vụ (`validator`)

#### 1. `BidValidator` (Quy tắc đặt giá)
- [ ] **TS-VAL-01** (Negative): Chặn người bán tự đặt giá cho sản phẩm của chính mình -> Ném `SELF_BIDDING_NOT_ALLOWED`.
- [ ] **TS-VAL-02** (Negative): Chặn đặt giá cho phiên thầu chưa bắt đầu hoặc đã kết thúc -> Ném `AUCTION_NOT_ACTIVE`.
- [ ] **TS-VAL-03** (Negative): Chặn đặt giá nhỏ hơn (Giá hiện tại + Bước giá tối thiểu) -> Ném `INVALID_BID_AMOUNT`.
- [ ] **TS-VAL-04** (Negative): Chặn Mua Ngay đối với phiên thầu không hỗ trợ Mua Ngay (`buyNowPrice == null`).

#### 2. `AuctionValidator` (Quy tắc tạo phiên thầu)
- [ ] **TS-VAL-05** (Negative): Chặn thời gian bắt đầu nằm trong quá khứ -> Ném `INVALID_START_TIME`.
- [ ] **TS-VAL-06** (Negative): Chặn thời gian kết thúc nhỏ hơn hoặc bằng thời gian bắt đầu -> Ném `INVALID_END_TIME`.
- [ ] **TS-VAL-07** (Negative): Chặn giá Mua Ngay nhỏ hơn giá khởi điểm -> Ném `INVALID_BUY_NOW_PRICE`.

#### 3. `ProductImageValidator` (Quy tắc hình ảnh)
- [ ] **TS-VAL-08** (Negative): Chặn danh sách ảnh rỗng hoặc vượt quá 5 ảnh -> Ném `INVALID_IMAGE_COUNT`.
- [ ] **TS-VAL-09** (Negative): Chặn file ảnh không đúng định dạng (chỉ cho phép JPG, PNG, WEBP) -> Ném `UNSUPPORTED_IMAGE_FORMAT`.

---

## 🛠️ 3. Quy Chuẩn Viết Unit Test Cho Developer Mới

Khi viết thêm bất kỳ test case nào vào dự án, Developer **bắt buộc tuân thủ 3 quy tắc**:

### 1. Quy chuẩn đặt tên Test Method
Cấu trúc: `given[ĐiềuKiện]_when[HànhĐộng]_then[KếtQuảKỳVọng]()`
* *Ví dụ*: `givenInvalidAuctionId_whenGetHistory_shouldThrowException()`

### 2. Quy chuẩn cấu trúc code trong bài Test (BDD Pattern)
Mỗi test method phải chia làm 3 phần rõ ràng có comment:
```java
@Test
void exampleTest() {
    // 1. Given: Giả lập dữ liệu & quy định phản hồi của @Mock
    given(repository.findById(1L)).willReturn(Optional.of(entity));

    // 2. When: Thực thi phương thức cần kiểm thử
    var result = service.doSomething(1L);

    // 3. Then: Kiểm tra kết quả trả về & verify số lần tương tác
    assertThat(result).isNotNull();
    then(repository).should(times(1)).findById(1L);
}
```

### 3. Quy chuẩn cô lập (Test Isolation)
* **KHÔNG DÙNG `@SpringBootTest`** cho Unit Test của tầng Service/Validator/Helper.
* Sử dụng `@ExtendWith(MockitoExtension.class)`, dùng `@Mock` cho tất cả các Dependency và `@InjectMocks` cho class cần test.
