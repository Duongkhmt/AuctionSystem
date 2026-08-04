package DuAnTrainning.AuctionSystem.mapper;

import DuAnTrainning.AuctionSystem.dto.response.BidHistoryResponseDTO;
import DuAnTrainning.AuctionSystem.entity.Bid;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

import java.util.List;

@Mapper(componentModel = "spring")
public interface BidMapper {

    @Mapping(target = "bidId", source = "id")
    @Mapping(target = "maskedBidderName", source = "bidder.username", qualifiedByName = "maskUsername")
    BidHistoryResponseDTO toHistoryDTO(Bid bid);

    List<BidHistoryResponseDTO> toHistoryDTOList(List<Bid> bids);

    @Named("maskUsername")
    default String maskUsername(String username) {
        if (username == null || username.isBlank()) return "u***r";
        if (username.length() <= 2) return username.charAt(0) + "***";
        return username.charAt(0) + "***" + username.charAt(username.length() - 1);
    }
}
