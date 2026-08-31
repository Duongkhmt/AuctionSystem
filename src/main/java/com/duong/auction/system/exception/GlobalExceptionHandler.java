package com.duong.auction.system.exception;

import lombok.RequiredArgsConstructor;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Locale;
import java.util.stream.Collectors;

@RestControllerAdvice
@RequiredArgsConstructor // Inject tự động MessageSource thông qua constructor
public class GlobalExceptionHandler {

    private final MessageSource messageSource;

    /**
     * Xử lý ngoại lệ nghiệp vụ chính của ứng dụng (ApplicationException)
     */
    @ExceptionHandler(ApplicationException.class)
    public ResponseEntity<ErrorResponse> handleApplicationException(ApplicationException ex) {
        ErrorCode errorCode = ex.getErrorCode();

        // 1. Tự động lấy Locale (ngôn ngữ) của Request hiện tại do Client gửi lên
        Locale locale = LocaleContextHolder.getLocale();

        // 2. Tra cứu câu thông báo đã được dịch từ file properties
        // - errorCode.name(): Key cần tìm (ví dụ: "USER_NOT_FOUND")
        // - null: Tham số truyền vào câu (nếu có)
        // - errorCode.getMessage(): Câu tiếng Việt mặc định làm phương án dự phòng (fallback)
        // - locale: Ngôn ngữ yêu cầu (vi hoặc en)
        String localizedMessage = messageSource.getMessage(
                errorCode.name(),
                null,
                errorCode.getMessage(),
                locale
        );

        // 3. Đóng gói response với message đã dịch
        ErrorResponse errorResponse = ErrorResponse.builder()
                .code(errorCode.getCode())
                .message(localizedMessage)
                .status(errorCode.getHttpStatusCode().value())
                .build();

        return ResponseEntity.status(errorCode.getHttpStatusCode().value())
                .body(errorResponse);
    }

    /**
     * Xử lý lỗi validate dữ liệu đầu vào (@Valid / @NotNull / @NotBlank...)
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationExceptions(MethodArgumentNotValidException ex) {
        // Lấy Locale từ HTTP Request hiện tại
        Locale locale = LocaleContextHolder.getLocale();
        // Dùng messageSource.getMessage(error, locale) để Spring tự động giải mã FieldError
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + messageSource.getMessage(error, locale))
                .collect(Collectors.joining("; "));
        ErrorResponse errorResponse = ErrorResponse.builder()
                .code(ErrorCode.INVALID_REQUEST.getCode())
                .message(message)
                .status(HttpStatus.BAD_REQUEST.value())
                .build();
        return ResponseEntity.badRequest().body(errorResponse);
    }

    /**
     * Xử lý các lỗi hệ thống không xác định (Exception chung)
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGenericException(Exception ex) {
        Locale locale = LocaleContextHolder.getLocale();

        String localizedMessage = messageSource.getMessage(
                ErrorCode.UNCATEGORIZED_EXCEPTION.name(),
                null,
                ErrorCode.UNCATEGORIZED_EXCEPTION.getMessage(),
                locale
        );

        ErrorResponse errorResponse = ErrorResponse.builder()
                .code(ErrorCode.UNCATEGORIZED_EXCEPTION.getCode())
                .message(localizedMessage)
                .status(HttpStatus.INTERNAL_SERVER_ERROR.value())
                .build();

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
    }

    /**
     *  Xử lý lỗi Đăng nhập sai Email hoặc Mật khẩu từ Spring Security (BadCredentialsException)
     */
    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ErrorResponse> handleBadCredentialsException(BadCredentialsException ex) {
        ErrorResponse errorResponse = ErrorResponse.builder()
                .code(ErrorCode.UNAUTHENTICATED.getCode())
                .message("Email hoặc mật khẩu không chính xác!")
                .status(HttpStatus.UNAUTHORIZED.value())
                .build();
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(errorResponse);
    }

    /**
     * 🟢 Xử lý lỗi Tài khoản bị Khóa / Ban khi Đăng nhập (DisabledException & LockedException)
     */
    @ExceptionHandler({DisabledException.class, LockedException.class})
    public ResponseEntity<ErrorResponse> handleDisabledException(Exception ex) {
        ErrorResponse errorResponse = ErrorResponse.builder()
                .code(ErrorCode.USER_BANNED_FROM_BIDDING.getCode())
                .message(ErrorCode.USER_BANNED_FROM_BIDDING.getMessage())
                .status(HttpStatus.FORBIDDEN.value())
                .build();
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(errorResponse);
    }

}
