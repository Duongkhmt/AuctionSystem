package DuAnTrainning.AuctionSystem.dto.response;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class CategoryResponseDTO {
    private Long id;
    private Long parentId;
    private String name;
    private boolean active;
    private boolean requiresVerification;
    private boolean requiresDeposit;
}
