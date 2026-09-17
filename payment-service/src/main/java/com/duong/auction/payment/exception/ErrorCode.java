package com.duong.auction.payment.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;

@Getter
public enum ErrorCode {
    UNCATEGORIZED_EXCEPTION(9999, "Lỗi hệ thống thanh toán không xác định", HttpStatus.INTERNAL_SERVER_ERROR),
    INVALID_REQUEST(1000, "Dữ liệu gửi lên không hợp lệ", HttpStatus.BAD_REQUEST),
    UNAUTHORIZED_ACCESS(1002, "Không có quyền truy cập dịch vụ nội bộ", HttpStatus.FORBIDDEN),
    UNAUTHENTICATED(1003, "Xác thực không hợp lệ hoặc đã hết hạn", HttpStatus.UNAUTHORIZED),

    // ===== PAYMENT & WALLET ERRORS (2000 - 2099) =====
    WALLET_NOT_FOUND(2001, "Ví tiền của người dùng không tồn tại", HttpStatus.NOT_FOUND),
    INSUFFICIENT_BALANCE(2002, "Số dư ví tiền ảo không đủ để thực hiện giao dịch", HttpStatus.BAD_REQUEST),
    IDEMPOTENCY_KEY_REQUIRED(2003, "Thiếu Idempotency-Key trong Header yêu cầu", HttpStatus.BAD_REQUEST);

    private final int code;
    private final String message;
    private final HttpStatusCode httpStatusCode;

    ErrorCode(int code, String message, HttpStatusCode httpStatusCode) {
        this.code = code;
        this.message = message;
        this.httpStatusCode = httpStatusCode;
    }
}
