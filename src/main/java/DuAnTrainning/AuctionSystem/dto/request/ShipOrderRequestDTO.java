package DuAnTrainning.AuctionSystem.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ShipOrderRequestDTO {

    @NotBlank(message = "Tên đơn vị vận chuyển không được để rỗng")
    private String courierName;

    @NotBlank(message = "Mã vận đơn tra cứu không được để rỗng")
    private String trackingNumber;
}
