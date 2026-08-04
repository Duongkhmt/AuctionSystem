package DuAnTrainning.AuctionSystem.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ProductImageResponseDTO {
    private Long id;
    private String imageUrl;
    private int displayOrder;
}
