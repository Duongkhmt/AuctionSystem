package com.example.DuAnTrainning.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
@Getter
public enum ErrorCode {
    UNCATEGORIZED_EXCEPTION(9999, "Lỗi không xác định", HttpStatus.INTERNAL_SERVER_ERROR),
    INVALID_REQUEST(1000, "Dữ liệu gửi lên không hợp lệ", HttpStatus.BAD_REQUEST),

    // ===== USER: 1000 - 1099 =====
    USER_NOT_FOUND(1001, "Người dùng không tồn tại", HttpStatus.NOT_FOUND),

    // ===== PRODUCT: 1100 - 1199 =====
    PRODUCT_NOT_FOUND(1101, "Sản phẩm không tồn tại", HttpStatus.NOT_FOUND),
    IMAGE_REQUIRED(1102, "Sản phẩm cần tối thiểu 1 ảnh", HttpStatus.BAD_REQUEST),
    TOO_MANY_IMAGES(1103, "Sản phẩm không được vượt quá 20 ảnh", HttpStatus.BAD_REQUEST),
    INVALID_IMAGE_URL(1104, "Đường dẫn ảnh không hợp lệ", HttpStatus.BAD_REQUEST),

    // ===== CATEGORY: 1200 - 1299 =====
    CATEGORY_NOT_FOUND(1201, "Danh mục không tồn tại", HttpStatus.NOT_FOUND),
    CATEGORY_INACTIVE(1202, "Danh mục hiện không còn hoạt động", HttpStatus.BAD_REQUEST),

    // ===== AUCTION: 1300 - 1399 =====
    AUCTION_NOT_FOUND(1301, "Phiên đấu giá không tồn tại", HttpStatus.NOT_FOUND),
    AUCTION_TYPE_REQUIRED(1302, "Loại đấu giá không được để trống", HttpStatus.BAD_REQUEST),
    START_PRICE_REQUIRED(1303, "Giá khởi điểm không được để trống", HttpStatus.BAD_REQUEST),
    BID_STEP_REQUIRED(1304, "Bước giá không được để trống", HttpStatus.BAD_REQUEST),
    START_TIME_REQUIRED(1305, "Thời gian bắt đầu không được để trống", HttpStatus.BAD_REQUEST),
    END_TIME_REQUIRED(1306, "Thời gian kết thúc không được để trống", HttpStatus.BAD_REQUEST),
    RESERVE_PRICE_REQUIRED(1307, "Loại đấu giá RESERVE bắt buộc phải có giá bảo lưu", HttpStatus.BAD_REQUEST),
    RESERVE_PRICE_NOT_ALLOWED(1308, "Loại đấu giá này không được phép có giá bảo lưu", HttpStatus.BAD_REQUEST),
    RESERVE_PRICE_TOO_LOW(1309, "Giá bảo lưu không được thấp hơn giá khởi điểm", HttpStatus.BAD_REQUEST),
    INVALID_AUCTION_TIME(1310, "Thời gian kết thúc phải sau thời gian bắt đầu", HttpStatus.BAD_REQUEST),
    START_TIME_IN_PAST(1311, "Thời gian bắt đầu không được ở quá khứ", HttpStatus.BAD_REQUEST),
    AUCTION_DURATION_TOO_SHORT(1312, "Phiên đấu giá phải kéo dài tối thiểu 30 phút", HttpStatus.BAD_REQUEST);


    ErrorCode(int code, String message, HttpStatusCode httpStatusCode) {
        this.message = message;
        this.code = code;
        this.httpStatusCode = httpStatusCode;
    }

    private int code;
    private String message;
    private HttpStatusCode httpStatusCode;
}
