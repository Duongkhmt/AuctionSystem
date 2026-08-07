package DuAnTrainning.AuctionSystem.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
@Getter
public enum ErrorCode {
    UNCATEGORIZED_EXCEPTION(9999, "Lỗi không xác định", HttpStatus.INTERNAL_SERVER_ERROR),
    INVALID_REQUEST(1000, "Dữ liệu gửi lên không hợp lệ", HttpStatus.BAD_REQUEST),

    // ===== USER: 1000 - 1099 =====
    USER_NOT_FOUND(1001, "Người dùng không tồn tại", HttpStatus.NOT_FOUND),
    UNAUTHORIZED_ACCESS(1002, "Bạn không có quyền chỉnh sửa sản phẩm này", HttpStatus.FORBIDDEN),

    // ===== PRODUCT: 1100 - 1199 =====
    PRODUCT_NOT_FOUND(1101, "Sản phẩm không tồn tại", HttpStatus.NOT_FOUND),
    IMAGE_REQUIRED(1102, "Sản phẩm cần tối thiểu 1 ảnh", HttpStatus.BAD_REQUEST),
    TOO_MANY_IMAGES(1103, "Sản phẩm không được vượt quá 20 ảnh", HttpStatus.BAD_REQUEST),
    INVALID_IMAGE_FILE(1104, "Ảnh phải là JPG, PNG hoặc WebP và không vượt quá 5MB", HttpStatus.BAD_REQUEST),
    IMAGE_UPLOAD_FAILED(1105, "Không thể tải ảnh sản phẩm lên Cloudinary", HttpStatus.INTERNAL_SERVER_ERROR),
    PRODUCT_NOT_PENDING_APPROVAL(1106, "Bài đăng không ở trạng thái chờ duyệt", HttpStatus.BAD_REQUEST),

    // ===== CATEGORY: 1200 - 1299 =====
    CATEGORY_NOT_FOUND(1201, "Danh mục không tồn tại", HttpStatus.NOT_FOUND),
    CATEGORY_INACTIVE(1202, "Danh mục hiện không còn hoạt động", HttpStatus.BAD_REQUEST),

    // ===== AUCTION: 1300 - 1399 =====
    AUCTION_NOT_FOUND(1301, "Phiên đấu giá không tồn tại", HttpStatus.NOT_FOUND),
    RESERVE_PRICE_REQUIRED(1302, "Loại đấu giá RESERVE bắt buộc phải có giá bảo lưu", HttpStatus.BAD_REQUEST),
    RESERVE_PRICE_NOT_ALLOWED(1303, "Loại đấu giá này không được phép có giá bảo lưu", HttpStatus.BAD_REQUEST),
    RESERVE_PRICE_TOO_LOW(1304, "Giá bảo lưu không được thấp hơn giá khởi điểm", HttpStatus.BAD_REQUEST),
    INVALID_AUCTION_TIME(1305, "Thời gian kết thúc phải sau thời gian bắt đầu", HttpStatus.BAD_REQUEST),
    START_TIME_IN_PAST(1306, "Thời gian bắt đầu không được ở quá khứ", HttpStatus.BAD_REQUEST),
    AUCTION_DURATION_TOO_SHORT(1307, "Phiên đấu giá phải kéo dài tối thiểu 30 phút", HttpStatus.BAD_REQUEST),
    AUCTION_ALREADY_STARTED(1308, "Phiên đấu giá đã bắt đầu hoặc kết thúc, không được phép chỉnh sửa", HttpStatus.BAD_REQUEST),
    CANNOT_DELETE_ACTIVE_AUCTION(1309, "Không thể xóa sản phẩm khi phiên đấu giá đang diễn ra hoặc đã hoàn tất", HttpStatus.BAD_REQUEST),
    CANNOT_CANCEL_STARTED_AUCTION(1310, "Phiên đấu giá đã bắt đầu hoặc đã kết thúc, người bán không thể tự hủy", HttpStatus.BAD_REQUEST),
    AUCTION_EXPIRED_BEFORE_APPROVAL(1311, "Thời gian đấu giá đã trôi qua trong lúc chờ duyệt, không thể chấp thuận", HttpStatus.BAD_REQUEST),
    AUCTION_NOT_RUNNING(1312, "Phiên đấu giá hiện không ở trạng thái diễn ra", HttpStatus.BAD_REQUEST),
    AUCTION_NOT_STARTED(1313, "Phiên đấu giá chưa đến giờ bắt đầu", HttpStatus.BAD_REQUEST),
    AUCTION_ENDED(1314, "Phiên đấu giá đã kết thúc", HttpStatus.BAD_REQUEST),
    BUY_NOW_NOT_SUPPORTED(1315, "Sản phẩm này không thuộc loại hình Mua Ngay", HttpStatus.BAD_REQUEST),
    BUY_NOW_PRICE_NOT_SET(1316, "Lỗi dữ liệu: Sản phẩm Mua Ngay nhưng không có giá Buy Now niêm yết", HttpStatus.INTERNAL_SERVER_ERROR),
    AUCTION_NOT_RELISTABLE(1317, "Phiên đấu giá chỉ được phép Đăng Lại khi ở trạng thái Hết Hạn hoặc Đã Hủy", HttpStatus.BAD_REQUEST),
    BUY_NOW_PRICE_REQUIRED(1318, "Loại hình BUY_NOW bắt buộc phải nhập giá mua ngay", HttpStatus.BAD_REQUEST),


    // ===== BID (THAO TÁC ĐẶT GIÁ CỦA BIDDER): 1400 - 1499 =====
    CANNOT_BID_OWN_PRODUCT(1401, "Người bán không được phép tự đặt giá sản phẩm của chính mình", HttpStatus.BAD_REQUEST),
    BID_AMOUNT_TOO_LOW(1402, "Mức giá đặt phải lớn hơn hoặc bằng giá hiện tại + bước giá tối thiểu", HttpStatus.BAD_REQUEST),
    ALREADY_HIGHEST_BIDDER(1403, "Bạn đang là người dẫn đầu giá cao nhất, không thể tự đè giá chính mình", HttpStatus.BAD_REQUEST),
    MAX_AUTO_BID_TOO_LOW(1404, "Giá Auto-bid tối đa phải lớn hơn hoặc bằng giá đặt ban đầu", HttpStatus.BAD_REQUEST),



    // ===== ORDER & PAYMENT: 1500 - 1599 =====
    ORDER_NOT_FOUND(1501, "Không tìm thấy thông tin đơn hàng trúng thầu", HttpStatus.NOT_FOUND),
    ORDER_ALREADY_PAID(1502, "Đơn hàng này đã được thanh toán trước đó", HttpStatus.BAD_REQUEST),
    CANNOT_SHIP_UNPAID_ORDER(1503, "Không thể giao hàng cho đơn chưa được người mua thanh toán", HttpStatus.BAD_REQUEST),
    ORDER_NOT_IN_SHIPPING_STATE(1504, "Đơn hàng chưa ở trạng thái đang vận chuyển", HttpStatus.BAD_REQUEST),
    INVALID_PHONE_NUMBER(1505, "Số điện thoại giao hàng không hợp lệ", HttpStatus.BAD_REQUEST);




    ErrorCode(int code, String message, HttpStatusCode httpStatusCode) {
        this.message = message;
        this.code = code;
        this.httpStatusCode = httpStatusCode;
    }

    private int code;
    private String message;
    private HttpStatusCode httpStatusCode;
}
