# ĐẶC TẢ CHỨC NĂNG: DÀNH CHO KHÁCH VẮNG LAI & NGƯỜI MUA (GUEST & BIDDER)

**Hệ thống:** Sàn Đấu Giá Trực Tuyến Đa Ngành Hàng  
**Phiên bản đặc tả:** 1.0  
**Tác giả:** Product Owner (PO) Team  

---

## 📊 BẢNG TÓM TẮT DANH SÁCH CHỨC NĂNG

| STT   | Tên chức năng                                  | Đối tượng               | Mục tiêu chính                                          |
|:------|:-----------------------------------------------|:------------------------|:--------------------------------------------------------|
| 1     | Xem danh mục sản phẩm                          | Khách vãng lai / Bidder | Khám phá cây danh mục hàng hóa đang mở thưởng           |
| 2     | Xem danh sách sản phẩm đấu giá công khai       | Khách vãng lai / Bidder | Tiếp cận các sản phẩm đã kiểm duyệt và đang lên sàn     |
| 3     | Xem chi tiết sản phẩm & thông số đấu giá       | Khách vãng lai / Bidder | Thẩm định tài sản, hình ảnh và diễn biến giá hiện tại   |
| 4     | Đặt giá thủ công & Đặt giá tự động (Proxy Bid) | Người mua (Bidder)      | Tham gia cạnh tranh mua tài sản hoặc ủy quyền đấu giá   |
| 5     | Mua ngay sản phẩm với giá cố định (Buy Now)    | Người mua (Bidder)      | Sở hữu lập tức tài sản mà không cần chờ đấu giá hết giờ |
| 6     | Xem lịch sử đặt giá công khai                  | Khách vãng lai / Bidder | Theo dõi độ minh bạch của phiên và thứ tự các bước giá  |

---

## 🔍 CHI TIẾT ĐẶC TẢ TỪNG CHỨC NĂNG

---

### Xem danh mục sản phẩm

**Bài toán kinh doanh**  
Nếu không có danh mục sản phẩm, hàng ngàn tài sản đấu giá (từ bất động sản, xe hơi, đồ điện tử đến tranh nghệ thuật) sẽ bị trộn lẫn. Khách hàng không thể tìm kiếm theo nhu cầu, dẫn đến tỷ lệ rời bỏ trang cao và giảm khả năng thanh khoản tài sản trên sàn.

**Mục tiêu**  
Cung cấp cho người dùng cấu trúc phân loại sản phẩm rõ ràng, giúp định hướng hành vi tìm kiếm nhanh chóng và chính xác.

**Đối tượng sử dụng / Điều kiện kích hoạt**  
Tất cả người dùng (Khách vãng lai và Người mua đã đăng nhập). Kích hoạt khi truy cập vào trang chủ hoặc khu vực tìm kiếm.

**Luồng thực hiện**  
1. Người dùng yêu cầu xem danh sách danh mục sản phẩm.
2. Hệ thống kiểm tra và truy xuất toàn bộ các danh mục sản phẩm đang ở trạng thái hoạt động.
3. Hệ thống trả về cấu trúc danh mục (bao gồm thông tin danh mục cha - con và các yêu cầu phụ như xác minh tài khoản hay đặt cọc trước nếu có).
4. Người dùng chọn danh mục mong muốn để lọc sản phẩm.

**Quy tắc nghiệp vụ**  
- [Chỉ hiển thị danh mục đang hoạt động] — vì việc hiển thị danh mục đã bị vô hiệu hóa sẽ khiến người dùng nhầm lẫn và chọn phải những nhóm hàng 
                                           không còn được giao dịch.

- [Hiển thị cờ báo yêu cầu xác minh / đặt cọc theo danh mục] — vì đối với các ngành hàng giá trị cao (như xe hơi, bất động sản), sàn cần cảnh báo trước 
để người mua chuẩn bị điều kiện pháp lý và tài chính trước khi tham gia.

**Trường hợp đặc biệt**  
- Hệ thống chưa có danh mục nào hoạt động: Trả về danh sách rỗng và hiển thị thông báo "Chưa có danh mục khả dụng", tránh gây lỗi giao diện phía người dùng.

**Liên quan tới**  
- [Xem danh sách sản phẩm đấu giá công khai](#xem-danh-sach-san-pham-dau-gia-cong-khai)

---

### Xem danh sách sản phẩm đấu giá công khai

**Bài toán kinh doanh**  
Nếu hiển thị cả các sản phẩm chưa qua kiểm duyệt, người mua có thể gặp phải tài sản giả, thông tin sai lệch hoặc hình ảnh phản cảm. Điều này gây mất uy tín nghiêm trọng cho sàn đấu giá và có thể dẫn tới các tranh chấp pháp lý.

**Mục tiêu**  
Hiển thị danh sách sản phẩm an toàn, đã được duyệt bởi Quản trị viên, sắp xếp theo thời gian mới nhất để tối ưu cơ hội tiếp cận cho các phiên đấu giá hot.

**Đối tượng sử dụng / Điều kiện kích hoạt**  
Tất cả người dùng trên hệ thống khi truy cập trang danh sách sản phẩm công khai.

**Luồng thực hiện**  
1. Người dùng yêu cầu xem sàn đấu giá công khai.
2. Hệ thống tiến hành lọc và lấy danh sách các sản phẩm đã được Ban quản trị phê duyệt xuất bản.
3. Hệ thống tổng hợp hình ảnh đại diện, thông tin phiên đấu giá (giá khởi điểm, giá hiện tại, thời gian còn lại) của từng sản phẩm.
4. Giao diện hiển thị danh sách sản phẩm theo thứ tự bài mới nhất lên đầu.

**Quy tắc nghiệp vụ**  
- [Chỉ sản phẩm có trạng thái ĐÃ DUYỆT mới được xuất hiện công khai] — vì đảm bảo mọi mặt hàng trên sàn đều đã qua kiểm định nội dung, tránh gian lận.
- [Bài đăng mới tạo được ưu tiên hiển thị trước] — vì tạo sự tươi mới cho trang web và giúp các sản phẩm mới lên sàn sớm thu hút lượt quan tâm.
- [Truy xuất dữ liệu tối ưu theo lô (Batch Processing)] — vì tránh việc hệ thống truy vấn lặp đi lặp lại nhiều lần gây giật lag khi hàng ngàn người cùng truy cập xem danh sách.

**Trường hợp đặc biệt**  
- Sản phẩm đã được duyệt nhưng phiên đấu giá đã kết thúc: Vẫn hiển thị thông tin kết quả công khai để tạo dữ liệu tham khảo giá thị trường cho người dùng.

**Liên quan tới**  
- [Xem chi tiết sản phẩm & thông số đấu giá](#xem-chi-tiet-san-pham-thong-so-dau-gia)
- [FUNCTIONAL-SPEC-ADMIN.md](./FUNCTIONAL-SPEC-ADMIN.md#phe-duyet-xuat-ban-bai-dang)

---

### Xem chi tiết sản phẩm & thông số đấu giá

**Bài toán kinh doanh**  
Khác với mua sắm thương mại điện tử thông thường, đấu giá đòi hỏi người mua phải thẩm định tài sản cực kỳ kỹ lưỡng (xem bộ ảnh chi tiết, mô tả tình trạng, các thông số kỹ thuật động và mốc thời gian chốt sổ). Nếu thiếu thông tin chi tiết, người mua không đủ tin tưởng để đưa ra mức giá cao.

**Mục tiêu**  
Cung cấp cái nhìn toàn diện 360 độ về sản phẩm và diễn biến giá thời gian thực, thúc đẩy quyết định ra giá của người mua.

**Đối tượng sử dụng / Điều kiện kích hoạt**  
Tất cả người dùng khi bấm vào một sản phẩm cụ thể.

**Luồng thực hiện**  
1. Người dùng bấm chọn một sản phẩm trong danh sách.
2. Hệ thống tải thông tin chi tiết sản phẩm: Tiêu đề, mô tả, bộ ảnh theo đúng thứ tự hiển thị.
3. Hệ thống tải thuộc tính kỹ thuật động (dạng thông số chuyên biệt như số km xe chạy, chất liệu khung tranh...).
4. Hệ thống tải trạng thái phiên đấu giá: Loại hình đấu giá, giá hiện tại, bước giá tối thiểu, thời gian bắt đầu và kết thúc.
5. Giao diện hiển thị đầy đủ thông tin cho người dùng thẩm định.

**Quy tắc nghiệp vụ**  
- [Hình ảnh phải hiển thị chuẩn xác theo thứ tự sắp xếp] — vì hình ảnh đầu tiên là góc nhìn tổng thể, các ảnh tiếp theo tả chi tiết vết xước/tem nhãn; sai thứ tự sẽ làm sai lệch đánh giá của người mua.
- [Công khai thuộc tính động linh hoạt theo chủng loại] — vì mỗi ngành hàng có bộ tiêu chuẩn định giá riêng (ví dụ: ô tô cần năm sản xuất, tranh cần tên họa sĩ).

**Trường hợp đặc biệt**  
- Yêu cầu xem sản phẩm không tồn tại hoặc bị xóa: Hệ thống thông báo lỗi "Sản phẩm không tồn tại" và chuyển hướng về danh sách công khai.

**Liên quan tới**  
- [Đặt giá thủ công & Đặt giá tự động (Proxy Bid)](#dat-gia-thu-cong-dat-gia-tu-dong-proxy-bid)

---

### Đặt giá thủ công & Đặt giá tự động (Proxy Bid)

**Bài toán kinh doanh**  
Trong đấu giá truyền thống, người mua phải dán mắt vào màn hình liên tục để bấm nâng giá từng nấc, rất tốn thời gian và dễ mất cơ hội vì gián đoạn mạng. 
Mặt khác, các hành vi tự gài giá của người bán (gà nhà tự đẩy giá) hoặc người dẫn đầu tự đè giá mình sẽ phá hỏng tính minh bạch của sàn.

**Mục tiêu**  
Cung cấp công cụ đặt giá thông minh (Proxy Bidding) cho phép người mua cài đặt giá trần mong muốn, hệ thống sẽ tự động canh giá và bảo vệ vị trí dẫn đầu cho họ với chi phí thấp nhất có thể. Đồng thời áp dụng các bộ lọc chống gian lận tuyệt đối.

**Đối tượng sử dụng / Điều kiện kích hoạt**  
Người mua (Bidder) đã đăng nhập khi phiên đấu giá đang trong thời gian DIỄN RA.

**Luồng thực hiện**  
1. Người mua nhập mức giá muốn đặt trực tiếp (hoặc nhập thêm mức giá trần tối đa muốn ủy quyền Auto-bid).
2. Hệ thống thực hiện chuỗi kiểm tra an toàn toàn diện (xem Quy tắc nghiệp vụ).
3. NẾU không cài Auto-bid (hoặc giá trần = giá đặt): Hệ thống ghi nhận lượt đặt giá mới của Người mua.
4. NẾU có đối thủ cũ đang cài giá trần Auto-bid:
   - Hệ thống tự động kích hoạt **Thuật toán Đấu giá Tự động**.
   - So sánh hai mức giá trần. Người có giá trần cao hơn sẽ giữ vị trí dẫn đầu.
   - Giá hiện tại của sản phẩm được điều chỉnh tự động nhảy lên = `Mức giá trần của người thua + 1 bước giá động` (nhưng không vượt quá trần của người thắng).
5. Hệ thống kiểm tra thời gian còn lại của phiên: NẾU lượt đặt giá nằm trong **3 phút cuối cùng**, hệ thống **tự động gia hạn thêm 3 phút** vào thời gian kết thúc (Soft-close Anti-sniping).
6. Hệ thống cập nhật giá hiện tại mới của phiên và thông báo kết quả cho người mua.

**Quy tắc nghiệp vụ**  
- [Phiên phải đang ở trạng thái DIỄN RA] — vì không thể đặt giá khi phiên chưa mở hoặc đã chốt sổ.
- [Chống gài giá từ Người bán (Anti-Shill Bidding)] — người bán không được phép dùng tài khoản của mình để đặt giá cho sản phẩm của chính mình, nhằm bảo vệ người mua khỏi bẫy đẩy giá ảo.
- [Chống đè giá chính mình (Anti-Self-Outbid)] — người đang nắm giữ giá cao nhất không được tự đặt giá nâng lên tiếp, nhằm tránh việc người mua bấm nhầm tốn thêm tiền không cần thiết.
- [Mức giá đặt phải lớn hơn hoặc bằng Giá hiện tại + Bước giá động] — vì đảm bảo mỗi lượt đấu giá phải mang lại sự tăng trưởng giá trị thực sự cho tài sản (chi tiết bậc giá xem tại [SYSTEM-BEHAVIOR.md](./SYSTEM-BEHAVIOR.md#quy-tac-tinh-toan-buoc-gia-dong-theo-gia-tri-san-pham)).
- [Giá trần Auto-bid phải lớn hơn hoặc bằng giá đặt ban đầu] — vì không thể cài đặt giới hạn tối đa thấp hơn mức giá khởi điểm trả ra.
- [Gia hạn phút chót (Anti-Sniping)] — lượt đặt giá trong 3 phút cuối sẽ tự động cộng thêm 3 phút vào thời gian kết thúc, nhằm triệt phá thủ đoạn dùng bot tự động bắn tỉa ở millisecond cuối cùng, tạo sự bình đẳng cho tất cả người tham gia.

**Trường hợp đặc biệt**  
- Hai người cùng cài mức giá trần Auto-bid bằng nhau: Người cài đặt trước sẽ được ưu tiên giữ vị trí dẫn đầu (theo nguyên tắc First-Come, First-Served).
- Chi tiết thuật toán cạnh tranh giá tự động và bảng so sánh các tình huống Auto-bid xem tại [SYSTEM-BEHAVIOR.md](./SYSTEM-BEHAVIOR.md#thuat-toan-tu-dong-gia-tang-gia-canh-tranh-proxy-bidding-engine).

**Liên quan tới**  
- [Xem lịch sử đặt giá công khai](#xem-lich-su-dat-gia-cong-khai)
- [SYSTEM-BEHAVIOR.md](./SYSTEM-BEHAVIOR.md#thuat-toan-tu-dong-gia-tang-gia-canh-tranh-proxy-bidding-engine)

---

### Mua ngay sản phẩm với giá cố định (Buy Now)

**Bài toán kinh doanh**  
Một số người mua cần sở hữu tài sản gấp và chấp nhận trả mức giá cao mà người bán kỳ vọng, thay vì phải chờ đợi nhiều ngày cho đến khi phiên đấu giá chốt sổ. Nếu bắt họ chờ đợi, sàn có thể mất đi những giao dịch đứt điểm nhanh chóng.

**Mục tiêu**  
Cho phép chốt giao dịch lập tức khi người mua đồng ý với mức giá Mua Ngay do người bán thiết lập trước.

**Đối tượng sử dụng / Điều kiện kích hoạt**  
Người mua khi sản phẩm có cấu hình tùy chọn Mua Ngay và phiên đang DIỄN RA.

**Luồng thực hiện**  
1. Người mua bấm chọn nút "Mua Ngay" trên trang chi tiết sản phẩm.
2. Hệ thống kiểm tra người bán không được tự mua sản phẩm của mình.
3. Hệ thống lập tức cập nhật giá hiện tại bằng mức giá Mua Ngay.
4. Hệ thống ghi nhận Người mua là Người chiến thắng chung cuộc.
5. Hệ thống lập tức đóng phiên đấu giá và chuyển trạng thái phiên sang KẾT THÚC.

**Quy tắc nghiệp vụ**  
- [Khóa lập tức phiên đấu giá ngay khi có người Mua Ngay] — vì giao dịch đã hoàn tất ở mức giá tối đa người bán kỳ vọng, không cho phép ai đặt giá thêm.
- [Ghi nhận tư cách Người chiến thắng tức thì] — vì bảo đảm quyền sở hữu hợp pháp tài sản cho người đã xuống tiền Mua Ngay.

**Trường hợp đặc biệt**  
- Người mua bấm Mua Ngay đúng lúc có một lượt đặt giá thường khác vừa tới: Hệ thống ưu tiên xử lý giao dịch Mua Ngay và từ chối các lượt đặt giá thường tiếp theo.

**Liên quan tới**  
- [SYSTEM-BEHAVIOR.md](./SYSTEM-BEHAVIOR.md#vong-doi-trang-thai-san-pham-va-phien-dau-gia)

---

### Xem lịch sử đặt giá công khai

**Bài toán kinh doanh**  
Trong đấu giá trực tuyến, mối lo lớn nhất của người mua là nghi ngờ sàn cấy dữ liệu ảo hoặc có sự can thiệp bất hợp pháp vào giá. Nếu không công khai lịch sử, người mua sẽ thiếu niềm tin. Tuy nhiên, nếu công khai nguyên văn thông tin người đặt (email, tên thật), người dùng sẽ bị lộ quyền riêng tư và đối mặt với rủi ro bị quấy rối ngoài đời thực.

**Mục tiêu**  
Bảo đảm tính minh bạch 100% của phiên đấu giá bằng cách công khai toàn bộ các bước giá, đồng thời mã hóa ẩn danh thông tin người tham gia để bảo vệ dữ liệu cá nhân.

**Đối tượng sử dụng / Điều kiện kích hoạt**  
Tất cả người dùng khi xem thông tin phiên đấu giá.

**Luồng thực hiện**  
1. Người dùng yêu cầu xem lịch sử đặt giá của phiên.
2. Hệ thống truy xuất danh sách các lượt đặt giá theo thứ tự thời gian mới nhất lên đầu.
3. Hệ thống chạy cơ chế mã hóa ẩn danh tên người đặt giá (Ví dụ: `duong` biến thành `d***g`).
4. Giao diện hiển thị danh sách gồm: Tên ẩn danh, số tiền đặt, thời gian đặt và cờ đánh dấu lượt đặt là do người hay Robot Auto-bid thực hiện.

**Quy tắc nghiệp vụ**  
- [Mã hóa ẩn danh bắt buộc đối với tên người đặt giá] — vì bảo vệ an toàn thông tin cá nhân (GDPR / Luật An ninh mạng), tránh việc đối thủ dòm ngó hoặc tiếp cận ngầm ngoài đời thực.
- [Hiển thị cờ phân biệt lượt đặt thủ công và Auto-bid] — vì giúp người xem hiểu rõ lý do giá nhảy tự động là do máy tính đại diện thực thi theo ủy quyền hợp pháp.

**Trường hợp đặc biệt**  
- Tên người dùng quá ngắn (dưới 2 ký tự): Hệ thống áp dụng quy tắc mã hóa đặc thù để vẫn đảm bảo tính ẩn danh (Ví dụ: `a` biến thành `a***`).

**Liên quan tới**  
- [SYSTEM-BEHAVIOR.md](./SYSTEM-BEHAVIOR.md#quy-tac-bao-mat-va-ma-hoa-an-danh-nguoi-tham-gia)
