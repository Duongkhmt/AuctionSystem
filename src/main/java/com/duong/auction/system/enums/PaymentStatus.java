package com.duong.auction.system.enums;

public enum PaymentStatus {
    SUCCESS,                // Thanh toán / Trừ tiền ví thành công
    INSUFFICIENT_BALANCE,   // Thiếu tiền trong ví
    PENDING_RETRY,          // Lỗi mạng/Service sập, đang chờ thử lại
    FAILED                  // Thất bại vĩnh viễn (Đơn bị hủy / Ví bị khóa)
}

