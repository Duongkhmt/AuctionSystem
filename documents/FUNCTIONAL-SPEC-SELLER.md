# ĐẶC TẢ CHỨC NĂNG: DÀNH CHO NGƯỜI BÁN (SELLER)

**Hệ thống:** Sàn Đấu Giá Trực Tuyến Đa Ngành Hàng  
**Phiên bản đặc tả:** 1.0  
**Tác giả:** Product Owner (PO) Team  

---

## 📊 BẢNG TÓM TẮT DANH SÁCH CHỨC NĂNG

| STT   | Tên chức năng                              | Đối tượng          | Mục tiêu chính                                                              |
|:------|:-------------------------------------------|:-------------------|:----------------------------------------------------------------------------|
| 1     | Đăng sản phẩm mới & Cấu hình phiên đấu giá | Người bán (Seller) | Tạo bài đăng bán tài sản với bộ ảnh và thông số đấu giá đầy đủ              |
| 2     | Xem danh sách sản phẩm cá nhân             | Người bán (Seller) | Theo dõi toàn bộ kho hàng và trạng thái kiểm duyệt/đấu giá                  |
| 3     | Chỉnh sửa thông tin bài đăng & Bộ ảnh      | Người bán (Seller) | Cập nhật thông tin sai sót trước khi phiên chính thức bắt đầu               |
| 4     | Xóa sản phẩm & Gỡ bài đăng                 | Người bán (Seller) | Xóa bỏ các bài đăng chưa mở đấu giá khỏi hệ thống                           |
| 5     | Chủ động hủy phiên đấu giá trước giờ G     | Người bán (Seller) | Dừng việc đấu giá tài sản khi có sự cố phát sinh trước thời điểm mở         |
| 6     | Đăng lại phiên đấu giá đã hết hạn (Relist) | Người bán (Seller) | Tái khởi tạo phiên đấu giá cho sản phẩm không bán được để tìm người mua mới |

---

## 🔍 CHI TIẾT ĐẶC TẢ TỪNG CHỨC NĂNG

---

### Đăng sản phẩm mới & Cấu hình phiên đấu giá

**Bài toán kinh doanh**  
Người bán cần một công cụ niêm yết tài sản linh hoạt, cho phép khai báo thông số kỹ thuật đa dạng (từ ô tô, nhà đất đến đồ điện tử) và thiết lập hình thức đấu giá mong muốn. Thiếu công cụ này, sàn không thể thu hút được nguồn cung hàng hóa phong phú.

**Mục tiêu**  
Thu thập đầy đủ mô tả, thuộc tính động, bộ ảnh minh họa chuẩn mực và cấu hình tài chính cho phiên đấu giá, đưa bài đăng vào quy trình kiểm duyệt an toàn.

**Đối tượng sử dụng / Điều kiện kích hoạt**  
Người bán (Seller) đã có tài khoản trên hệ thống.

**Luồng thực hiện**  
1. Người bán chọn Danh mục sản phẩm phù hợp.
2. Người bán nhập Tiêu đề, Mô tả và chọn/điền các Thuộc tính động theo ngành hàng (Ví dụ: Số km xe chạy, năm sản xuất, chất liệu...).
3. Người bán tải lên bộ ảnh minh họa sản phẩm (từ 1 đến 20 ảnh).
4. Người bán cấu hình thông số đấu giá:
   - Chọn loại hình đấu giá (`ENGLISH`, `RESERVE`, hoặc `BUY_NOW`).
   - Nhập Giá khởi điểm, Bước giá tối thiểu, Thời gian bắt đầu và Thời gian kết thúc.
   - NẾU chọn loại hình `RESERVE`: Nhập thêm Mức giá bảo lưu (giá sàn ẩn).
   - NẾU chọn loại hình `BUY_NOW`: Nhập thêm Mức giá mua ngay.
5. Người bán xác nhận gửi bài.
6. Hệ thống thực hiện chuỗi kiểm tra tính hợp lệ của ảnh và quy tắc thời gian/tiền tệ.
7. Hệ thống tải ảnh lên hạ tầng mây, tạo sản phẩm ở trạng thái **CHỜ DUYỆT** và phiên đấu giá ở trạng thái **CHỜ CHẤP THUẬN**.

**Quy tắc nghiệp vụ**  
- [Danh mục chọn bắt buộc phải đang ở trạng thái Hoạt động] — vì tránh việc nạp sản phẩm vào nhóm danh mục đã bị vô hiệu hóa hoặc ngưng kinh doanh.
- [Bắt buộc có từ 1 đến 20 ảnh, định dạng JPG/PNG/WebP, dung lượng <= 5MB mỗi ảnh] — vì hình ảnh là căn cứ thẩm định duy nhất trực tuyến; thiếu ảnh hoặc ảnh quá nặng làm hỏng trải nghiệm người mua.
- [Loại hình đấu giá RESERVE bắt buộc có Giá bảo lưu >= Giá khởi điểm] — vì giá bảo lưu là mức giá tối thiểu người bán kỳ vọng thu về; không thể cài đặt giá bảo lưu thấp hơn giá khởi điểm rao bán.
- [Loại hình đấu giá không phải RESERVE không được chứa Giá bảo lưu] — vì tránh gây nhầm lẫn cấu hình giữa các loại hình đấu giá khác nhau.
- [Thời gian bắt đầu không được ở quá khứ] — vì không thể mở một phiên đấu giá retro cho mốc thời gian đã trôi qua.
- [Thời gian kết thúc phải sau thời gian bắt đầu tối thiểu 30 phút] — vì một phiên đấu giá cần có khung thời gian đủ dài để người mua tiếp cận và cạnh tranh giá.
- [Đồng bộ giao dịch hạ tầng mây] — NẾU quá trình lưu dữ liệu DB thất bại, hệ thống phải tự động xóa sạch các ảnh vừa up lên mây, nhằm tránh rác bộ nhớ CDN và tiết kiệm chi phí vận hành cho sàn.

**Trường hợp đặc biệt**  
- Bảng so sánh điều kiện các loại hình đấu giá:

| Loại hình đấu giá                   | Giá khởi điểm | Giá bảo lưu (Reserve)       | Giá mua ngay (Buy Now)      |
|:------------------------------------|:--------------|:----------------------------|:----------------------------|
| **ENGLISH** (Đấu giá tăng dần)      | Bắt buộc      | KHÔNG được phép             | Tùy chọn                    |
| **RESERVE** (Đấu giá có giá sàn ẩn) | Bắt buộc      | BẮT BUỘC (>= Giá khởi điểm) | Tùy chọn                    |
| **BUY_NOW** (Mua ngay giá cố định)  | Bắt buộc      | KHÔNG được phép             | BẮT BUỘC (>= Giá khởi điểm) |

**Liên quan tới**  
- [FUNCTIONAL-SPEC-ADMIN.md](./FUNCTIONAL-SPEC-ADMIN.md#phe-duyet-xuat-ban-bai-dang)
- [SYSTEM-BEHAVIOR.md](./SYSTEM-BEHAVIOR.md#bao-ve-toan-ven-tai-nguyen-may-va-dong-bo-giao-dich)

---

### Xem danh sách sản phẩm cá nhân

**Bài toán kinh doanh**  
Người bán có nhiều tài sản đăng bán ở các giai đoạn khác nhau (đang chờ duyệt, bị từ chối, đang chạy hay đã kết thúc). Thiếu trang quản lý trung tâm khiến họ bị mất kiểm soát hàng tồn và không theo dõi được doanh số.

**Mục tiêu**  
Cung cấp bảng điều khiển trung tâm giúp người bán theo dõi toàn bộ trạng thái bài đăng và phản hồi của Ban quản trị.

**Đối tượng sử dụng / Điều kiện kích hoạt**  
Người bán đã đăng nhập.

**Luồng thực hiện**  
1. Người bán truy cập khu vực "Quản lý bài đăng của tôi".
2. Hệ thống truy xuất toàn bộ danh sách sản phẩm thuộc quyền sở hữu của Người bán, sắp xếp bài đăng mới nhất lên đầu.
3. Hệ thống trả về danh sách kèm trạng thái chi tiết của sản phẩm (Chờ duyệt, Đã duyệt, Bị từ chối kèm lý do) và trạng thái phiên đấu giá tương ứng.

**Quy tắc nghiệp vụ**  
- [Chỉ hiển thị bài đăng do chính người bán đó sở hữu] — vì đảm bảo tính bảo mật kinh doanh giữa các nhà bán hàng độc lập.
- [Hiển thị lý do từ chối công khai cho bài đăng BỊ TỪ CHỐI] — vì giúp người bán hiểu rõ điểm vi phạm để sửa đổi bài đăng ở lần sau.

**Trường hợp đặc biệt**  
- Người bán chưa từng đăng sản phẩm nào: Trả về danh sách rỗng kèm gợi ý "Tạo bài đăng đầu tiên".

**Liên quan tới**  
- [Chỉnh sửa thông tin bài đăng & Bộ ảnh](#chinh-sua-thong-tin-bai-dang-bo-anh)

---

### Chỉnh sửa thông tin bài đăng & Bộ ảnh

**Bài toán kinh doanh**  
Sau khi nạp bài, người bán có thể phát hiện sai sót trong mô tả hoặc muốn bổ sung thêm ảnh sắc nét hơn. Tuy nhiên, nếu cho phép sửa bài khi đấu giá đang diễn ra, người bán có thể tráo đổi thông số hàng hóa (ví dụ: đăng xe máy 150cc rồi sửa thành 100cc), gây lừa đảo người mua đang đặt giá.

**Mục tiêu**  
Cho phép linh hoạt sửa chữa thông tin trước khi mở phiên, đồng thời khóa chặt dữ liệu ngay khi phiên đấu giá đã đi vào hoạt động.

**Đối tượng sử dụng / Điều kiện kích hoạt**  
Người bán đối với sản phẩm của mình khi phiên đấu giá đang ở trạng thái **CHỜ DUYỆT** hoặc **ĐÃ LÊN LỊCH**.

**Luồng thực hiện**  
1. Người bán chọn nút "Chỉnh sửa" trên bài đăng hợp lệ.
2. Người bán cập nhật Tiêu đề, Mô tả, Danh mục, Thuộc tính động hoặc Thông số đấu giá.
3. Người bán có thể chọn xóa bớt một số ảnh cũ và/hoặc tải thêm các file ảnh mới.
4. Hệ thống kiểm tra điều kiện bảo vệ (xem Quy tắc nghiệp vụ).
5. NẾU hợp lệ:
   - Hệ thống xóa các dòng ảnh được chọn trong DB và **chỉ phát lệnh xóa trên mây SAU KHI DB lưu thành công** (Post-commit Hook).
   - Hệ thống tải ảnh mới lên mây và **đánh lại thứ tự hiển thị (displayOrder)** từ 0 đến N cho bộ ảnh mới.
   - Hệ thống lưu các thông tin cập nhật vào DB.

**Quy tắc nghiệp vụ**  
- [Tuyệt đối CHẶN chỉnh sửa khi phiên đấu giá đã BẮT ĐẦU hoặc KẾT THÚC] — vì bảo vệ tính toàn vẹn của hợp đồng đấu giá; người mua đặt giá dựa trên thông tin ban đầu, không được tự ý sửa thông số tài sản khi cuộc chơi đã chạy.
- [Tổng số ảnh sau khi xóa và thêm mới bắt buộc nằm trong khoảng [1, 20]] — vì không được để sản phẩm rơi vào tình trạng không có ảnh nào hoặc vượt quá giới hạn tải trang.
- [Kiểm tra chính xác quyền sở hữu đối với các ảnh yêu cầu xóa] — vì tránh lỗ hổng bảo mật người bán xóa nhầm ID ảnh của người bán khác.
- [Đánh lại thứ tự hiển thị ảnh tự động] — vì đảm bảo không có lỗ hổng đứt gãy chỉ số hiển thị sau khi xóa bớt ảnh giữa chừng.

**Trường hợp đặc biệt**  
- Người bán gửi danh sách xóa ảnh chứa các ID trùng lặp (Ví dụ: `[1, 1, 1]`): Hệ thống tự động lọc trùng bằng tập hợp (Set) để chỉ xử lý xóa duy nhất 1 lần, tránh lỗi DB.

**Liên quan tới**  
- [SYSTEM-BEHAVIOR.md](./SYSTEM-BEHAVIOR.md#bao-ve-toan-ven-tai-nguyen-may-va-dong-bo-giao-dich)

---

### Xóa sản phẩm & Gỡ bài đăng

**Bài toán kinh doanh**  
Người bán nhập nhầm bài hoặc đổi ý không muốn bán tài sản nữa. Tuy nhiên, nếu cho phép xóa khi phiên đấu giá đang chạy hoặc đã kết thúc có người thắng, người bán có thể xù hàng (bùng kèo) khi giá đấu không đạt như kỳ vọng.

**Mục tiêu**  
Cho phép giải phóng kho hàng cho người bán nhưng vẫn bảo đảm tính ràng buộc trách nhiệm khi cuộc đấu giá đã khởi chạy.

**Đối tượng sử dụng / Điều kiện kích hoạt**  
Người bán đối với sản phẩm của mình khi phiên đấu giá **CHƯA BẮT ĐẦU** (Không ở trạng thái DIỄN RA hay KẾT THÚC).

**Luồng thực hiện**  
1. Người bán chọn lệnh "Xóa bài đăng".
2. Hệ thống kiểm tra điều kiện trạng thái phiên.
3. NẾU phiên chưa diễn ra:
   - Hệ thống quét và gom toàn bộ mã ảnh mây của sản phẩm.
   - Hệ thống tiến hành xóa các dữ liệu liên quan trong DB.
   - Hệ thống phát lệnh xóa sạch bộ ảnh sản phẩm trên mây SAU KHI DB xóa thành công.
4. Hệ thống thông báo xóa thành công.

**Quy tắc nghiệp vụ**  
- [Tuyệt đối KHÔNG ĐƯỢC XÓA khi phiên đấu giá đang DIỄN RA hoặc đã KẾT THÚC] — vì bảo vệ pháp lý cho cuộc đấu giá và quyền lợi của người mua đang trả giá hoặc đã thắng đấu giá.

**Trường hợp đặc biệt**  
- Xóa sản phẩm đang chờ duyệt: Hệ thống dọn dẹp toàn bộ dữ liệu kèm ảnh mây mà không ảnh hưởng tới người dùng khác.

**Liên quan tới**  
- [SYSTEM-BEHAVIOR.md](./SYSTEM-BEHAVIOR.md#bao-ve-toan-ven-tai-nguyen-may-va-dong-bo-giao-dich)

---

### Chủ động hủy phiên đấu giá trước giờ G

**Bài toán kinh doanh**  
Trong khoảng thời gian bài đang chờ duyệt hoặc đã lên lịch chờ mở phiên, tài sản có thể gặp sự cố hỏng hóc hoặc bị mất. Người bán cần một cơ chế chủ động báo hủy phiên trước khi người mua vào đặt giá.

**Mục tiêu**  
Cung cấp nút hủy an toàn trước giờ mở phiên để tránh các khiếu nại phát sinh sau này.

**Đối tượng sử dụng / Điều kiện kích hoạt**  
Người bán đối với phiên đấu giá chưa diễn ra.

**Luồng thực hiện**  
1. Người bán bấm chọn "Hủy phiên đấu giá".
2. Hệ thống kiểm tra phiên chưa diễn ra.
3. Hệ thống cập nhật trạng thái phiên đấu giá sang **ĐÃ HỦY**.

**Quy tắc nghiệp vụ**  
- [Chỉ cho phép hủy trước giờ mở phiên] — vì khi phiên đã chạy, việc tự ý hủy ngang sẽ làm tổn hại đến niềm tin của cộng đồng người mua.

**Trường hợp đặc biệt**  
- Phiên đã đến giờ mở: Nút hủy bị vô hiệu hóa hoàn toàn.

**Liên quan tới**  
- [SYSTEM-BEHAVIOR.md](./SYSTEM-BEHAVIOR.md#vong-doi-trang-thai-san-pham-va-phien-dau-gia)

---

### Đăng lại phiên đấu giá đã hết hạn (Relist)

**Bài toán kinh doanh**  
Nhiều phiên đấu giá kết thúc nhưng không có người tham gia hoặc không đạt giá kỳ vọng (trở thành trạng thái HẾT HẠN). Nếu bắt người bán phải nhập lại toàn bộ thông tin và tải lại bộ ảnh từ đầu, họ sẽ mất rất nhiều thời gian.

**Mục tiêu**  
Cho phép người bán tái sử dụng toàn bộ thông tin sản phẩm có sẵn để nhanh chóng mở một phiên đấu giá mới.

**Đối tượng sử dụng / Điều kiện kích hoạt**  
Người bán đối với các phiên đấu giá có trạng thái **HẾT HẠN**.

**Luồng thực hiện**  
1. Người bán vào danh sách sản phẩm, chọn phiên đã Hết hạn và bấm "Đăng lại (Relist)".
2. Hệ thống kiểm tra tư cách người bán và trạng thái phiên phải là HẾT HẠN.
3. Hệ thống làm mới mốc thời gian đấu giá (Ví dụ: Khởi tạo thời gian bắt đầu từ thời điểm hiện tại và kéo dài chu kỳ mới).
4. Hệ thống đổi trạng thái phiên sang **DIỄN RA** để người mua tiếp tục vào đặt giá.

**Quy tắc nghiệp vụ**  
- [Chỉ cho phép đăng lại đối với phiên đã HẾT HẠN] — vì không thể tái đăng một phiên đang chạy hoặc đang chờ duyệt.
- [Reset chu kỳ thời gian mới hoàn toàn] — vì phiên đăng lại phải có thời hạn tươi mới để thu hút lượt mua mới.

**Trường hợp đặc biệt**  
- Đăng lại nhưng sản phẩm gốc đã bị xóa: Hệ thống thông báo lỗi và từ chối thao tác.

**Liên quan tới**  
- [SYSTEM-BEHAVIOR.md](./SYSTEM-BEHAVIOR.md#vong-doi-trang-thai-san-pham-va-phien-dau-gia)
