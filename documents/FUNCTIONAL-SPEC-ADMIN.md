# ĐẶC TẢ CHỨC NĂNG: DÀNH CHO QUẢN TRỊ VIÊN (ADMIN)

**Hệ thống:** Sàn Đấu Giá Trực Tuyến Đa Ngành Hàng

## 📊 BẢNG TÓM TẮT DANH SÁCH CHỨC NĂNG

| STT   | Tên chức năng                         | Đối tượng             | Mục tiêu chính                                                              |
|:------|:--------------------------------------|:----------------------|:----------------------------------------------------------------------------|
| 1     | Xem danh sách bài đăng chờ kiểm duyệt | Quản trị viên (Admin) | Rà soát toàn bộ sản phẩm mới đăng trước khi cho phép xuất bản               |
| 2     | Phê duyệt xuất bản bài đăng (Approve) | Quản trị viên (Admin) | Xác nhận sản phẩm hợp lệ và kích hoạt phiên đấu giá lên sàn                 |
| 3     | Từ chối xuất bản bài đăng (Reject)    | Quản trị viên (Admin) | Loại bỏ các bài đăng vi phạm chính sách và gửi phản hồi lý do cho người bán |

---

## 🔍 CHI TIẾT ĐẶC TẢ TỪNG CHỨC NĂNG

---

### Xem danh sách bài đăng chờ kiểm duyệt

**Bài toán kinh doanh**  
Mỗi ngày có hàng ngàn bài đăng từ nhiều người bán khác nhau. Nếu không có một khu vực tập trung cho Ban quản trị rà soát, các bài đăng chứa thông tin sai lệch, hình ảnh phản cảm hoặc hàng cấm có thể lọt ra ngoài, làm sụp đổ uy tín của sàn.

**Mục tiêu**  
Cung cấp màn hình quản lý tập trung toàn bộ các sản phẩm đang chờ duyệt, xếp theo thứ tự ưu tiên thời gian nạp bài.

**Đối tượng sử dụng / Điều kiện kích hoạt**  
Quản trị viên (Admin) đã đăng nhập hệ thống.

**Luồng thực hiện**  
1. Quản trị viên truy cập khu vực "Duyệt bài đăng".
2. Hệ thống lọc và lấy ra tất cả các sản phẩm đang ở trạng thái **CHỜ DUYỆT**.
3. Hệ thống tổng hợp đầy đủ hồ sơ bài đăng: Bộ ảnh, thuộc tính kỹ thuật động, giá khởi điểm, hình thức đấu giá và thông tin người bán.
4. Giao diện hiển thị danh sách xếp bài đăng gửi sớm nhất lên đầu để xử lý theo quy trình.

**Quy tắc nghiệp vụ**  
- [Chỉ hiển thị các bài đăng ở trạng thái CHỜ DUYỆT] — vì giúp Admin tập trung vào khối lượng công việc tồn đọng chưa xử lý, không bị phân tâm bởi bài đã duyệt hay đã hủy.
- [Truy xuất tối ưu theo lô (Batch Processing)] — vì đảm bảo tốc độ tải trang nhanh chóng cho Admin ngay cả khi có hàng nghìn bài chờ duyệt.

**Trường hợp đặc biệt**  
- Không có bài đăng nào chờ duyệt: Hiển thị màn hình sạch "Đã kiểm duyệt xong tất cả bài đăng".

**Liên quan tới**  
- [Phê duyệt xuất bản bài đăng (Approve)](#phe-duyet-xuat-ban-bai-dang-approve)
- [Từ chối xuất bản bài đăng (Reject)](#tu-choi-xuat-ban-bai-dang-reject)

---

### Phê duyệt xuất bản bài đăng (Approve)

**Bài toán kinh doanh**  
Sản phẩm hợp lệ cần được đưa lên sàn nhanh chóng để người bán kịp kinh doanh. Tuy nhiên, thời gian chờ duyệt có thể kéo dài khiến thời gian dự kiến 
mở phiên bị trễ hoặc thậm chí đã trôi qua mốc kết thúc. Hệ thống cần tự động điều phối trạng thái phiên sao cho hợp lý nhất với thực tế.

**Mục tiêu**  
Chính thức phát hành sản phẩm ra công khai và tự động kích hoạt trạng thái phiên đấu giá phù hợp với mốc thời gian thực tế.

**Đối tượng sử dụng / Điều kiện kích hoạt**  
Quản trị viên khi kiểm tra bài đăng hợp lệ và bấm chọn "Chấp nhận duyệt (Approve)".

**Luồng thực hiện**  
1. Quản trị viên bấm nút "Duyệt bài".
2. Hệ thống tiến hành kiểm tra mốc thời gian đấu giá đã được cấu hình trước đó đối với thời điểm hiện tại:
   - **Tình huống A (Quá hạn):** NẾU Thời gian kết thúc (`endTime`) của phiên đã trôi qua trong quá khứ trước khi Admin kịp duyệt 
   ➔ Hệ thống chặn và ném lỗi "Phiên đấu giá đã hết hạn trong lúc chờ duyệt, không thể chấp thuận".
   
   - **Tình huống B (Đến giờ mở):** NẾU Thời gian bắt đầu (`startTime`) đã trôi qua hoặc đúng bằng hiện tại 
   ➔ Hệ thống đổi trạng thái phiên sang **DIỄN RA (RUNNING)** ngay lập tức!
   
   - **Tình huống C (Chưa đến giờ):** NẾU Thời gian bắt đầu (`startTime`) vẫn nằm trong tương lai 
   ➔ Hệ thống đổi trạng thái phiên sang **ĐÃ LÊN LỊCH (SCHEDULED)**.
   
3. Hệ thống đổi trạng thái sản phẩm sang **ĐÃ DUYỆT (APPROVED)**.
4. Bài đăng chính thức xuất hiện trên sàn công khai.

**Quy tắc nghiệp vụ**  
- [Chặn duyệt nếu phiên đã hết hạn] — vì không thể đưa lên sàn một cuộc đấu giá mà thời gian chốt sổ đã trôi qua từ trước, gây lỗi dữ liệu hiển thị.
- [Chuyển thẳng sang DIỄN RA nếu trễ giờ mở] — vì nếu Admin duyệt trễ hơn thời điểm người bán muốn mở phiên, việc đưa thẳng sang trạng thái DIỄN RA 
                                               giúp phiên đấu giá không bị lỡ mất thời gian giao dịch của người dùng.
- [Đưa vào ĐÃ LÊN LỊCH nếu chưa tới giờ] — vì tôn trọng thời điểm mở phiên mà người bán đã chủ động lựa chọn trong tương lai.

**Trường hợp đặc biệt**  
- Bảng quyết định trạng thái chuyển đổi khi Admin phê duyệt:

| Thời điểm `startTime`     | Thời điểm `endTime`       | Trạng thái Sản phẩm sau duyệt | Trạng thái Phiên đấu giá sau duyệt         |
|:--------------------------|:--------------------------|:------------------------------|:-------------------------------------------|
| Trong tương lai (`> now`) | Trong tương lai (`> now`) | **ĐÃ DUYỆT (APPROVED)**       | **ĐÃ LÊN LỊCH (SCHEDULED)**                |
| Trong quá khứ (`<= now`)  | Trong tương lai (`> now`) | **ĐÃ DUYỆT (APPROVED)**       | **DIỄN RA (RUNNING)**                      |
| Trong quá khứ (`< now`)   | Trong quá khứ (`< now`)   | *TỪ CHỐI DUYỆT (Lỗi hết hạn)* | *Giữ nguyên CHỜ DUYỆT (Báo lỗi cho Admin)* |

**Liên quan tới**  
- [SYSTEM-BEHAVIOR.md](./SYSTEM-BEHAVIOR.md#vong-doi-trang-thai-san-pham-va-phien-dau-gia)
- [FUNCTIONAL-SPEC-GUEST-BIDDER.md](./FUNCTIONAL-SPEC-GUEST-BIDDER.md#xem-danh-sach-san-pham-dau-gia-cong-khai)

---

### Từ chối xuất bản bài đăng (Reject)

**Bài toán kinh doanh**  
Khi phát hiện bài đăng vi phạm (ảnh mờ, sai danh mục, giá bất hợp lý hoặc chứa thông tin cấm), Admin cần từ chối. Tuy nhiên, nếu từ chối mà không cho người bán biết lý do cụ thể, họ sẽ hoang mang, khiếu nại hoặc tiếp tục lặp lại vi phạm đó ở các bài đăng sau.

**Mục tiêu**  
Loại bỏ bài vi phạm khỏi quy trình lên sàn, đồng thời cung cấp phản hồi rõ ràng để người bán biết đường khắc phục.

**Đối tượng sử dụng / Điều kiện kích hoạt**  
Quản trị viên khi phát hiện bài đăng không đạt yêu cầu và chọn "Từ chối (Reject)".

**Luồng thực hiện**  
1. Quản trị viên bấm chọn nút "Từ chối".
2. Hệ thống hiển thị hộp thoại yêu cầu nhập **Lý do từ chối**.
3. Quản trị viên nhập phản hồi (Ví dụ: "Hình ảnh bị mờ, thông số kỹ thuật không đúng thực tế") và bấm xác nhận.
4. Hệ thống kiểm tra lý do không được để trống và không quá 255 ký tự.
5. Hệ thống chuyển trạng thái sản phẩm sang **BỊ TỪ CHỐI (REJECTED)** và ghi nhận lý do vào hồ sơ.
6. Hệ thống chuyển trạng thái phiên đấu giá sang **ĐÃ HỦY (CANCELLED)**.

**Quy tắc nghiệp vụ**  
- [Bắt buộc phải nhập Lý do từ chối] — vì bảo đảm tính minh bạch trong quản lý, giúp người bán biết chính xác vi phạm để sửa đổi.
- [Hủy lập tức phiên đấu giá đi kèm] — vì sản phẩm đã bị từ chối thì phiên đấu giá không còn căn cứ hợp pháp để tồn tại hay lên lịch mở.

**Trường hợp đặc biệt**  
- Admin bấm từ chối nhưng bỏ trống ô lý do: Hệ thống báo lỗi và không cho phép hoàn tất thao tác từ chối.

**Liên quan tới**  
- [FUNCTIONAL-SPEC-SELLER.md](./FUNCTIONAL-SPEC-SELLER.md#xem-danh-sach-san-pham-ca-nhan)
- [SYSTEM-BEHAVIOR.md](./SYSTEM-BEHAVIOR.md#vong-doi-trang-thai-san-pham-va-phien-dau-gia)
