package DuAnTrainning.AuctionSystem.enums;

public enum OrderStatus {
    UNPAID,    // Mới khởi tạo khi hết giờ/mua ngay, Chờ người mua nhập địa chỉ và hoàn tất thanh toán
    PAID,      // Người mua đã thanh toán tiền vào ví ký quỹ Escrow, chờ seller đóng gói
    SHIPPING,  // Seller đã giao hàng cho đơn vị vận chuyển (GHTK) và nhập mã vận đơn
    COMPLETED, // Người mua xác nhận đã nhận hàng thành công, giải ngân cho seller
    CANCELLED  // Đơn hàng bị hủy do quá hạn thanh toán 48h hoặc tranh chấp
}
