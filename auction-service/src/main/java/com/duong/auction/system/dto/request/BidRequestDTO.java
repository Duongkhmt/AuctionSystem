package com.duong.auction.system.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;
import java.math.BigDecimal;

@Getter
@Setter
public class BidRequestDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    @NotNull(message = "Vui lòng nhập giá đặt")
    @DecimalMin(value = "0.01", message = "Giá đặt phải lớn hơn 0")
    private BigDecimal bidAmount;

    @DecimalMin(value = "0.01", message = "Giá Auto Bid phải lớn hơn 0")
    private BigDecimal maxAutoBidAmount;
}