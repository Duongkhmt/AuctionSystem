package com.duong.auction.system.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;

@Getter
@Setter
public class ShipOrderRequestDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    @NotBlank(message = "Tên đơn vị vận chuyển không được để rỗng")
    private String courierName;

    @NotBlank(message = "Mã vận đơn tra cứu không được để rỗng")
    private String trackingNumber;
}
