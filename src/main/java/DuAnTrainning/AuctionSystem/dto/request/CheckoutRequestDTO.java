package DuAnTrainning.AuctionSystem.dto.request;

import DuAnTrainning.AuctionSystem.enums.PaymentMethod;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CheckoutRequestDTO {

    @NotBlank(message = "Địa chỉ giao hàng không được để rỗng")
    private String shippingAddress;

    @NotBlank(message = "Số điện thoại nhận hàng không được để rỗng")
    @Pattern(regexp = "^(0[3|5|7|8|9])+([0-9]{8})$", message = "Số điện thoại giao hàng không đúng định dạng Việt Nam")
    private String phoneNumber;

    @NotNull(message = "Vui lòng chọn phương thức thanh toán")
    private PaymentMethod paymentMethod;
}
