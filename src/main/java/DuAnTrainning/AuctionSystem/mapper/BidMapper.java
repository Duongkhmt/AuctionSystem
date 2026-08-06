package DuAnTrainning.AuctionSystem.mapper;

import DuAnTrainning.AuctionSystem.dto.response.BidHistoryResponseDTO;
import DuAnTrainning.AuctionSystem.entity.Bid;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

import java.util.List;

/**
 * Mapper MapStruct ánh xạ lịch sử đặt giá Bid sang DTO công khai kèm quy tắc mã hóa tên người dùng.
 */
@Mapper(componentModel = "spring")
public interface BidMapper {

    // 1. Ánh xạ 1 bản ghi Bid sang BidHistoryResponseDTO kèm gọi hàm mã hóa tên maskUsername
    @Mapping(target = "bidId", source = "id")
    @Mapping(target = "maskedBidderName", source = "bidder.username", qualifiedByName = "maskUsername")
    BidHistoryResponseDTO toHistoryDTO(Bid bid);

    // 2. Ánh xạ danh sách bản ghi Bid sang danh sách BidHistoryResponseDTO
    List<BidHistoryResponseDTO> toHistoryDTOList(List<Bid> bids);

    // 3. Quy tắc mã hóa tên ẩn danh người tham gia đấu giá (Ví dụ: "duong" -> "d***g")
    @Named("maskUsername")
    default String maskUsername(String username) {
        if (username == null || username.isBlank()) return "u***r";
        if (username.length() <= 2) return username.charAt(0) + "***";
        return username.charAt(0) + "***" + username.charAt(username.length() - 1);
    }
}
