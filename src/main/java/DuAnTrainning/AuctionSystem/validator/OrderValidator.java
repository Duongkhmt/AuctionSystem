package DuAnTrainning.AuctionSystem.validator;

import DuAnTrainning.AuctionSystem.entity.Order;
import DuAnTrainning.AuctionSystem.enums.OrderStatus;
import DuAnTrainning.AuctionSystem.exception.ApplicationException;
import DuAnTrainning.AuctionSystem.exception.ErrorCode;
import org.springframework.stereotype.Component;

/**
 * Validator chuyên trách kiểm tra các quy tắc an toàn nghiệp vụ và quyền sở hữu Đơn Hàng.
 */
@Component
public class OrderValidator {

    // 1. Validate điều kiện khi Người Mua bấm Checkout (Bắt buộc đúng buyer và đơn hàng phải là UNPAID)
    public void validateCheckout(Long buyerId, Order order) {
        if (!order.getBuyer().getId().equals(buyerId)) {
            throw new ApplicationException(ErrorCode.UNAUTHORIZED_ACCESS);
        }
        if (order.getStatus() != OrderStatus.UNPAID) {
            throw new ApplicationException(ErrorCode.ORDER_ALREADY_PAID);
        }
    }

    // 2. Validate điều kiện khi Người Bán bấm Xuất Hàng (Bắt buộc đúng seller và đơn hàng phải là PAID)
    public void validateShipOrder(Long sellerId, Order order) {
        if (!order.getSeller().getId().equals(sellerId)) {
            throw new ApplicationException(ErrorCode.UNAUTHORIZED_ACCESS);
        }
        if (order.getStatus() != OrderStatus.PAID) {
            throw new ApplicationException(ErrorCode.CANNOT_SHIP_UNPAID_ORDER);
        }
    }

    // 3. Validate điều kiện khi Người Mua bấm Xác Nhận Đã Nhận Hàng (Bắt buộc đúng buyer và đơn hàng phải là SHIPPING)
    public void validateConfirmReceived(Long buyerId, Order order) {
        if (!order.getBuyer().getId().equals(buyerId)) {
            throw new ApplicationException(ErrorCode.UNAUTHORIZED_ACCESS);
        }
        if (order.getStatus() != OrderStatus.SHIPPING) {
            throw new ApplicationException(ErrorCode.ORDER_NOT_IN_SHIPPING_STATE);
        }
    }
}
