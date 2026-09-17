package com.duong.auction.system.dto.request;

import com.duong.auction.system.enums.PaymentMethod;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;

@Getter
@Setter
public class CheckoutRequestDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    @NotBlank(message = "Địa chỉ giao hàng không được để rỗng")
    private String shippingAddress;

    @NotBlank(message = "Số điện thoại nhận hàng không được để rỗng")
    @Pattern(regexp = "^(0[35789])[0-9]{8}$", message = "Số điện thoại giao hàng không đúng định dạng Việt Nam (10 chữ số)")
    private String phoneNumber;

    @NotNull(message = "Vui lòng chọn phương thức thanh toán")
    private PaymentMethod paymentMethod;
}
