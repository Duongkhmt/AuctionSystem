# ĐẶC TẢ HÀNH VI HỆ THỐNG & CƠ CHẾ TỰ ĐỘNG (SYSTEM BEHAVIOR & AUTOMATED SCHEDULER)

**Hệ thống:** Sàn Đấu Giá Trực Tuyến Đa Ngành Hàng  
**Phiên bản đặc tả:** 1.0  
**Tác giả:** Product Owner (PO) Team  

---

## 📊 BẢNG TÓM TẮT CÁC CƠ CHẾ TỰ ĐỘNG

| STT   | Tên cơ chế                                  | Tác nhân vận hành       | Mục tiêu chính                                                               |
|:------|:--------------------------------------------|:------------------------|:-----------------------------------------------------------------------------|
| 1     | Vòng đời trạng thái dữ liệu (State Machine) | Quy tắc hệ thống        | Đảm bảo tính nhất quán và toàn vẹn trạng thái giữa Sản phẩm và Phiên đấu giá |
| 2     | Robot quét trạng thái ngầm (Scheduler)      | Robot tự động (10s/lần) | Tự động kích hoạt mở phiên và đóng phiên chốt sổ đúng từng giây              |
| 3     | Engine tự động đấu giá (Proxy Bidding)      | Thuật toán máy tính     | Đại diện người mua trả giá thông minh theo ủy quyền                          |
| 4     | Thang bước giá tăng dần tự động             | Quy tắc tính toán       | Tự nâng bước giá tối thiểu tương thích với giá trị tài sản                   |
| 5     | Chống bắn tỉa phút chót (Anti-Sniping)      | Quy tắc thời gian       | Kéo dài thêm 3 phút nếu có lượt bid ở phút chót để tạo sự bình đẳng          |
| 6     | Cơ chế ẩn danh người tham gia (Masking)     | Quy tắc bảo mật         | Mã hóa tên người đặt giá để bảo vệ dữ liệu cá nhân                           |
| 7     | Đồng bộ giao dịch tài nguyên mây (CDN Sync) | Quy tắc giao dịch       | Tự động dọn ảnh rác trên mây khi hỏng giao dịch DB                           |

---

## 🔍 CHI TIẾT ĐẶC TẢ TỪNG CƠ CHẾ HỆ THỐNG

---

### Vòng đời trạng thái dữ liệu (State Machine Matrix)

**Bài toán kinh doanh**  
Trong hệ thống đấu giá, sản phẩm và phiên đấu giá là hai đối tượng đi liền với nhau. Nếu trạng thái của sản phẩm (Ví dụ: Bị từ chối) bất bất đồng bộ với trạng thái của phiên đấu giá (Ví dụ: Vẫn đang diễn ra), hệ thống sẽ bị lỗi logic nghiêm trọng, khiến người mua đặt giá vào một sản phẩm bị cấm.

**Mục tiêu**  
Định nghĩa ma trận chuyển đổi trạng thái nghiêm ngặt, đảm bảo mọi biến động của sản phẩm luôn kéo theo sự chuyển đổi tương ứng của phiên đấu giá.

**Đối tượng sử dụng / Điều kiện kích hoạt**  
Tự động kích hoạt khi có sự kiện tác động từ Người bán, Quản trị viên hoặc Robot thời gian.

**Luồng thực hiện**  
1. Sự kiện thay đổi trạng thái phát sinh (Ví dụ: Admin duyệt bài, người bán hủy bài, hết giờ).
2. Hệ thống đối chiếu ma trận trạng thái hợp lệ.
3. Hệ thống cập nhật đồng thời cả hai trạng thái của Sản phẩm và Phiên đấu giá trong một giao dịch duy nhất.

**Quy tắc nghiệp vụ**  
- [Bảng ma trận ánh xạ trạng thái giữa Sản phẩm và Phiên đấu giá]:

| Trạng thái Sản phẩm (`ProductStatus`) | Trạng thái Phiên tương ứng (`AuctionStatus`) | Ý nghĩa nghiệp vụ                                           |
|:--------------------------------------|:---------------------------------------------|:------------------------------------------------------------|
| **CHỜ DUYỆT (PENDING)**               | **CHỜ CHẤP THUẬN (PENDING_APPROVAL)**        | Bài đăng mới tạo, chưa ai xem được trên sàn công khai       |
| **ĐÃ DUYỆT (APPROVED)**               | **ĐÃ LÊN LỊCH (SCHEDULED)**                  | Đã kiểm duyệt xong, đang chờ đến mốc `startTime` để mở      |
| **ĐÃ DUYỆT (APPROVED)**               | **DIỄN RA (RUNNING)**                        | Đã kiểm duyệt xong và đang trong khung giờ cho phép đặt giá |
| **ĐÃ DUYỆT (APPROVED)**               | **KẾT THÚC (ENDED)**                         | Đấu giá xong thành công, có người thắng hoặc chốt Mua Ngay  |
| **ĐÃ DUYỆT (APPROVED)**               | **HẾT HẠN (EXPIRED)**                        | Hết giờ đấu giá nhưng không bán được (không có ai trả giá)  |
| **BỊ TỪ CHỐI (REJECTED)**             | **ĐÃ HỦY (CANCELLED)**                       | Admin từ chối bài đăng, phiên đấu giá bị hủy bỏ             |

- [Khóa biến đổi trạng thái một chiều đối với phiên KẾT THÚC / ĐÃ HỦY] — vì khi phiên đã kết thúc hoặc bị hủy, không thể đảo ngược trạng thái về chờ duyệt để tránh làm gãy dữ liệu lịch sử.

**Trường hợp đặc biệt**  
- Sản phẩm HẾT HẠN được người bán bấm Đăng lại (Relist): Trạng thái phiên chuyển từ `EXPIRED` trở lại `RUNNING` và thời gian được gia hạn chu kỳ mới.

**Liên quan tới**  
- [FUNCTIONAL-SPEC-ADMIN.md](./FUNCTIONAL-SPEC-ADMIN.md#phe-duyet-xuat-ban-bai-dang-approve)
- [FUNCTIONAL-SPEC-SELLER.md](./FUNCTIONAL-SPEC-SELLER.md#dang-lai-phien-dau-gia-da-het-han-relist)

---

### Robot quét trạng thái ngầm (Scheduler)

**Bài toán kinh doanh**  
Hàng ngàn phiên đấu giá có mốc mở phiên và chốt phiên lẻ đến từng giây. Nếu dựa vào con người bấm nút mở/đóng thủ công, các phiên đấu giá sẽ bị mở trễ hoặc đóng trễ, gây khiếu nại về tính chính xác của thời gian.

**Mục tiêu**  
Sử dụng Robot ngầm quét liên tục 24/7 để tự động kích hoạt phiên sang trạng thái DIỄN RA và chốt sổ sang KẾT THÚC đúng từng milisecond.

**Đối tượng sử dụng / Điều kiện kích hoạt**  
Bộ đếm thời gian tự động của hệ thống, định kỳ kích hoạt **10 giây một lần (fixedRate = 10,000ms)**.

**Luồng thực hiện**  
1. Cứ mỗi 10 giây, Robot ngầm thức dậy và lấy mốc thời gian hiện tại (`now`).
2. **Nhiệm vụ 1 (Tự động Mở phiên):** Robot tìm tất cả các phiên đấu giá đang ở trạng thái `SCHEDULED` mà có `startTime <= now` VÀ sản phẩm tương ứng có trạng thái `APPROVED`. Robot phát lệnh cập nhật tất cả các phiên này sang `RUNNING` trong 1 câu lệnh duy nhất.
3. **Nhiệm vụ 2 (Tự động Khóa phiên):** Robot tìm tất cả các phiên đấu giá đang ở trạng thái `RUNNING` mà có `endTime <= now`. Robot phát lệnh cập nhật tất cả các phiên này sang `ENDED` trong 1 câu lệnh duy nhất.
4. Robot hoàn thành nhiệm vụ và nghỉ ngơi chờ chu kỳ 10 giây tiếp theo.

**Quy tắc nghiệp vụ**  
- [Cập nhật theo lô trực tiếp trong DB (Bulk Update)] — vì nếu load từng phiên đấu giá lên bộ nhớ rồi mới lưu, hệ thống sẽ bị treo RAM khi có hàng ngàn phiên kết thúc cùng lúc.
- [Đảm bảo sản phẩm phải ĐÃ DUYỆT mới cho Mở phiên] — vì ngăn chặn sự cố hy hữu một sản phẩm chưa được Admin duyệt mà Robot đã tự tiện mở đấu giá.

**Trường hợp đặc biệt**  
- Thời gian hệ thống server bị trễ mất vài giây do nghẽn mạng: Robot khi chạy lại sẽ quét gom toàn bộ các phiên lẽ ra phải mở/đóng trong khoảng thời gian trễ đó để xử lý bù lập tức.

**Liên quan tới**  
- [Vòng đời trạng thái dữ liệu (State Machine Matrix)](#vong-doi-trang-thai-du-lieu-state-machine-matrix)

---

### Thuật toán tự động gia tăng giá cạnh tranh (Proxy Bidding Engine)

**Bài toán kinh doanh**  
Trong đấu giá trực tuyến, khi người mua A muốn trả tối đa 10 triệu cho một món đồ đang có giá 1 triệu, họ không muốn đẩy giá lên 10 triệu ngay (vì sẽ bị mua đắt). Họ muốn máy tính tự động nâng giá lên 1.1 triệu, 1.2 triệu... và chỉ nâng vừa đủ để thắng người khác.

**Mục tiêu**  
Đại diện người mua cạnh tranh giá thông minh, đảm bảo họ luôn dẫn đầu với chi phí tiết kiệm nhất có thể.

**Đối tượng sử dụng / Điều kiện kích hoạt**  
Tự động kích hoạt khi có một lượt đặt giá mới gửi vào phiên đấu giá đang DIỄN RA.

**Luồng thực hiện**  
1. Người dùng B gửi lượt đặt giá với số tiền `bidAmount` và mức trần Auto-bid `maxAutoBidAmount`.
2. Hệ thống ghi nhận lượt bid của người B vào lịch sử (để lưu Audit Trail).
3. Hệ thống tìm đối thủ A đang cài mức trần Auto-bid cao nhất hiện tại trong phiên.
4. NẾU chưa có ai cài Auto-bid (hoặc A chính là B): Mức giá hiện tại của phiên chính là `bidAmount` của B.
5. NẾU có đối thủ A đã cài Auto-bid từ trước:
   - Hệ thống tiến hành so sánh hai mức giá trần: `Max(A)` vs `Max(B)`.
   - **Kịch bản 1 (Người cũ A thắng - Max A >= Max B):**
     - Robot của A tự động sinh tiếp 1 lượt đặt giá phản công.
     - Mức giá mới của phiên nhảy lên = `Min( Max(B) + 1 bước giá, Max(A) )`.
     - Người A tiếp tục giữ vị trí dẫn đầu.
   - **Kịch bản 2 (Người mới B thắng - Max B > Max A):**
     - Mức giá mới của người B được điều chỉnh nhảy lên = `Min( Max(A) + 1 bước giá, Max(B) )`.
     - Người B vươn lên giữ vị trí dẫn đầu.
6. Hệ thống lưu lại toàn bộ các lượt bid tự động phát sinh vào DB để phục vụ kiểm toán minh bạch.

**Quy tắc nghiệp vụ**  
- [Luôn lưu dấu vết 100% các lượt bid sinh tự động] — vì bảo đảm tính kiểm toán pháp lý, chứng minh giá tăng là do đại diện hợp pháp của người dùng trả giá chứ không phải sàn gian lận.
- [Mức giá nhảy tự động không bao giờ được vượt quá giá trần của người đặt] — vì bảo vệ ngân sách tối đa mà người mua đã ủy quyền.

**Trường hợp đặc biệt**  
- Bảng kịch bản so sánh Auto-bid giữa Người cũ (A) và Người mới (B):

| Mức trần `Max(A)` | Mức trần `Max(B)` | Người chiến thắng tạm thời                 | Mức giá hiện tại mới của phiên (`currentPrice`) |
|:------------------|:------------------|:-------------------------------------------|:------------------------------------------------|
| 10,000,000đ       | 5,000,000đ        | **Người cũ A**                             | **5,100,000đ** *(Max B + 1 bước giá 100k)*      |
| 5,000,000đ        | 10,000,000đ       | **Người mới B**                            | **5,100,000đ** *(Max A + 1 bước giá 100k)*      |
| 10,000,000đ       | 10,000,000đ       | **Người cũ A** *(Ưu tiên người đến trước)* | **10,000,000đ** *(Chạm trần cả hai)*            |

**Liên quan tới**  
- [FUNCTIONAL-SPEC-GUEST-BIDDER.md](./FUNCTIONAL-SPEC-GUEST-BIDDER.md#dat-gia-thu-cong-dat-gia-tu-dong-proxy-bid)

---

### Quy tắc tính toán bước giá động theo giá trị sản phẩm

**Bài toán kinh doanh**  
Nếu áp dụng cố định một bước giá (Ví dụ: 10.000đ) cho tất cả sản phẩm, thì đối với một căn nhà hay ô tô trị giá 2 tỷ đồng, các lượt đặt giá 10.000đ sẽ kéo dài cuộc đấu giá vô tận. Ngược lại, nếu áp bước giá 500.000đ cho chiếc áo thun 50.000đ, không ai có thể tham gia đấu giá được.

**Mục tiêu**  
Tự động tăng/giảm bước giá tối thiểu tương thích với quy mô giá trị hiện tại của tài sản.

**Đối tượng sử dụng / Điều kiện kích hoạt**  
Tự động áp dụng mỗi khi kiểm tra tính hợp lệ của lượt đặt giá hoặc tính toán mức giá nhảy Auto-bid.

**Luồng thực hiện**  
1. Hệ thống lấy mức giá hiện tại (`currentPrice`) của phiên đấu giá.
2. Hệ thống đối chiếu bảng phân bậc giá trị.
3. Hệ thống trả về Bước giá tối thiểu tương ứng và cộng vào giá hiện tại để ra `MinValidBid`.

**Quy tắc nghiệp vụ**  
- [Bảng phân bậc bước giá động]:

| Khoảng giá hiện tại của tài sản (`currentPrice`) | Bước giá tối thiểu áp dụng (`bidStep`) | Lý do nghiệp vụ |
| :--- | :--- | :--- |
| **Dưới 1,000,000đ** | **10,000đ** | Phù hợp với sản phẩm giá trị nhỏ, tạo bước nhảy nhẹ nhàng |
| **Từ 1,000,000đ đến 10,000,000đ** | **100,000đ** | Phù hợp với hàng điện tử, gia dụng средний |
| **Trên 10,000,000đ** | **500,000đ** | Tối ưu thời gian đấu giá cho tài sản giá trị cao (xe cộ, đồ xa xỉ) |

**Trường hợp đặc biệt**  
- Sản phẩm chưa có lượt bid nào (`currentPrice` null hoặc bằng giá khởi điểm): Áp dụng bước giá của bậc tương ứng với giá khởi điểm đó.

**Liên quan tới**  
- [Thuật toán tự động gia tăng giá cạnh tranh (Proxy Bidding Engine)](#thuat-toan-tu-dong-gia-tang-gia-canh-tranh-proxy-bidding-engine)

---

### Cơ chế chống gài giá phút chót / Chống bắn tỉa (Soft-Close Anti-Sniping Window)

**Bài toán kinh doanh**  
Thủ đoạn "Sniping" (Bắn tỉa) là việc người mua dùng công cụ tự động canh gạt giá vào đúng 1 millisecond cuối cùng trước khi hết giờ. Việc này khiến những người mua thật sự khác không kịp phản ứng bấm phím, dẫn tới sản phẩm bị mua hớ với giá rẻ và gây ức chế cho cộng đồng.

**Mục tiêu**  
Triệt phá thủ đoạn bắn tỉa, tạo cơ hội phản công công bằng cho tất cả các bên quan tâm đến tài sản.

**Đối tượng sử dụng / Điều kiện kích hoạt**  
Tự động kích hoạt khi có một lượt đặt giá hợp lệ gửi vào phiên đấu giá trong khung thời gian sát giờ G.

**Luồng thực hiện**  
1. Hệ thống nhận một lượt đặt giá hợp lệ.
2. Hệ thống lấy thời gian hiện tại (`now`) và thời gian kết thúc của phiên (`endTime`).
3. Hệ thống kiểm tra: NẾU `(endTime - 3 phút) <= now` (tức là lượt bid nằm trong **3 phút cuối cùng**).
4. Hệ thống phát lệnh tự động cộng thêm **+3 phút** vào `endTime` của phiên đấu giá!
5. Hệ thống trả về cờ báo `timeExtended = true` và thời gian kết thúc mới cho giao diện cập nhật.

**Quy tắc nghiệp vụ**  
- [Cửa sổ kích hoạt là 3 phút cuối (`ANTI_SNIPING_WINDOW_MINUTES = 3`)] — vì 3 phút là khoảng thời gian đủ cho một con người bình thường nhận thông báo và đưa ra quyết định bấm nâng giá.
- [Mỗi lần kích hoạt cộng thêm đúng 3 phút (`EXTENSION_MINUTES = 3`)] — vì kéo dài vừa đủ để cạnh tranh tiếp mà không làm phiền những người tham gia khác.

**Trường hợp đặc biệt**  
- Lượt bid vào ở phút thứ 4 trước khi kết thúc: Không kích hoạt gia hạn (vì nằm ngoài cửa sổ 3 phút).

**Liên quan tới**  
- [FUNCTIONAL-SPEC-GUEST-BIDDER.md](./FUNCTIONAL-SPEC-GUEST-BIDDER.md#dat-gia-thu-cong-dat-gia-tu-dong-proxy-bid)

---

### Quy tắc bảo mật và mã hóa ẩn danh người tham gia (Bidder Identity Masking)

**Bài toán kinh doanh**  
Nếu công khai tên thật hoặc tài khoản của người đặt giá, đối thủ có thể soi tiểu sử, dùng thủ đoạn đe dọa ngoài đời hoặc liên hệ ngầm thương lượng thông đồng (Collusion). Ngược lại nếu giấu nhẹm hoàn toàn thì không chứng minh được độ minh bạch.

**Mục tiêu**  
Ẩn danh tên người đặt giá đối với công chúng nhưng vẫn đảm bảo tính phân biệt giữa các người tham gia khác nhau.

**Đối tượng sử dụng / Điều kiện kích hoạt**  
Tự động áp dụng khi trả về dữ liệu lịch sử thầu cho công chúng hoặc phản hồi kết quả đặt giá.

**Luồng thực hiện**  
1. Hệ thống nhận tên tài khoản nguyên bản (Ví dụ: `duong`).
2. Hệ thống kiểm tra độ dài tên.
3. Hệ thống giữ lại ký tự đầu và ký tự cuối, thay toàn bộ phần giữa bằng dấu `***`.
4. Trả về tên đã mã hóa (Ví dụ: `d***g`).

**Quy tắc nghiệp vụ**  
- [Bảng quy tắc ẩn danh mã hóa tên]:

| Tên tài khoản gốc (`username`) | Tên mã hóa hiển thị công khai (`maskedBidderName`) | Ghi chú |
| :--- | :--- | :--- |
| `duong` | `d***g` | Giữ ký tự đầu `d` và cuối `g` |
| `admin_seller` | `a***r` | Giữ ký tự đầu `a` và cuối `r` |
| `ab` *(<= 2 ký tự)* | `a***` | Giữ ký tự đầu `a` và thêm `***` |
| `null` hoặc rỗng | `u***r` | Tên mặc định đại diện người dùng |

**Trường hợp đặc biệt**  
- Tài khoản đấu giá bị null dữ liệu: Hệ thống gán tên ẩn danh mặc định `u***r`.

**Liên quan tới**  
- [FUNCTIONAL-SPEC-GUEST-BIDDER.md](./FUNCTIONAL-SPEC-GUEST-BIDDER.md#xem-lich-su-dat-gia-cong-khai)

---

### Bảo vệ toàn vẹn tài nguyên mây và đồng bộ giao dịch (CDN Image Transaction Synchronization)

**Bài toán kinh doanh**  
Hình ảnh tải lên Cloudinary CDN nằm ngoài hệ thống DB. NẾU lưu DB thất bại mà không dọn ảnh mây ➔ Tốn tiền bộ nhớ mây và rác CDN. NẾU xóa ảnh mây trước mà DB lưu lỗi ➔ Trực tiếp gây chết link ảnh (Broken Link) trên website.

**Mục tiêu**  
Bảo đảm đồng bộ 100% giữa giao dịch Database và bộ nhớ lưu trữ Cloudinary CDN.

**Đối tượng sử dụng / Điều kiện kích hoạt**  
Tự động kích hoạt trong các thao tác Tạo sản phẩm, Sửa ảnh và Xóa bài đăng tại tầng xử lý nghiệp vụ.

**Luồng thực hiện**  
1. **Kịch bản Upload ảnh mới:**
   - Đăng ký Sự kiện Rollback (`afterCompletion`).
   - NẾU DB bị Rollback do lỗi ➔ Lập tức phát lệnh xóa toàn bộ các ảnh vừa tải lên Cloudinary.
2. **Kịch bản Xóa ảnh cũ:**
   - Đăng ký Sự kiện Sau Commit (`afterCommit`).
   - Xóa dòng dữ liệu ảnh trong DB trước.
   - NẾU DB Commit thành công 100% ➔ Mới phát lệnh gọi Cloudinary xóa ảnh trên mây.

**Quy tắc nghiệp vụ**  
- [Chỉ xóa ảnh mây cũ SAU KHI DB commit thành công (`afterCommit`)] — vì nếu xóa trên mây trước mà DB bị rollback, sản phẩm sẽ bị dính link ảnh chết trên trang chủ.
- [Dọn dẹp ảnh mây mới LẬP TỨC nếu DB rollback (`afterCompletion`)] — vì không để lại các file ảnh rác mây không thuộc sở hữu của dòng sản phẩm nào trong DB.

**Trường hợp đặc biệt**  
- Đợt xóa ảnh bị đứt mạng Cloudinary: Hệ thống ghi log lỗi và giữ nguyên luồngDB đã commit an toàn, tài nguyên rác sẽ được bộ dọn dẹp quản trị quét lại sau.

**Liên quan tới**  
- [FUNCTIONAL-SPEC-SELLER.md](./FUNCTIONAL-SPEC-SELLER.md#dang-san-pham-moi-cau-hinh-phien-dau-gia)
