package DuAnTrainning.AuctionSystem.mapper;

import DuAnTrainning.AuctionSystem.dto.request.ProductRequestDTO;
import DuAnTrainning.AuctionSystem.dto.request.ProductUpdateRequestDTO;
import DuAnTrainning.AuctionSystem.dto.response.ProductResponseDTO;
import DuAnTrainning.AuctionSystem.entity.Auction;
import DuAnTrainning.AuctionSystem.entity.Product;
import org.mapstruct.*;

/**
 * Mapper MapStruct ánh xạ giữa Product/Auction Entity và các đối tượng DTO.
 * Tự động kết nối với BidMapper để sử dụng hàm mã hóa tên người thắng (qualifiedByName = "maskUsername").
 */
@Mapper(componentModel = "spring", uses = {BidMapper.class})
public interface ProductMapper {

    // 1. Ánh xạ từ ProductRequestDTO sang Product Entity (Tự động set status = PENDING ở Service)
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "images", ignore = true)
    @Mapping(target = "seller", ignore = true)
    @Mapping(target = "category", ignore = true)
    @Mapping(target = "status", ignore = true)
    Product toEntity(ProductRequestDTO requestDTO);

    // 2. Cập nhật thông tin Product Entity từ ProductUpdateRequestDTO (Bỏ qua các trường null)
    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "seller", ignore = true)
    @Mapping(target = "category", ignore = true)
    @Mapping(target = "images", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    void updateProductFromDto(ProductUpdateRequestDTO dto, @MappingTarget Product product);

    // 3. Phẳng hóa dữ liệu từ 2 Entity (Product + Auction) sang 1 ProductResponseDTO duy nhất
    @Mapping(target = "productId", source = "product.id")
    @Mapping(target = "sellerId", source = "product.seller.id")
    @Mapping(target = "categoryId", source = "product.category.id")
    @Mapping(target = "status", source = "product.status")
    @Mapping(target = "createdAt", source = "product.createdAt")
    @Mapping(target = "rejectionReason", source = "product.rejectionReason")
    @Mapping(target = "images", ignore = true)
    @Mapping(target = "auctionId", source = "auction.id")
    @Mapping(target = "auctionType", source = "auction.auctionType")
    @Mapping(target = "startPrice", source = "auction.startPrice")
    @Mapping(target = "currentPrice", source = "auction.currentPrice")
    @Mapping(target = "bidStep", source = "auction.bidStep")
    @Mapping(target = "reservePrice", source = "auction.reservePrice")
    @Mapping(target = "buyNowPrice", source = "auction.buyNowPrice")
    
    // Mapping thông tin Người Thắng Cuộc (Winner ID và Tên mã hóa)
    @Mapping(target = "winnerId", source = "auction.winner.id")
    @Mapping(target = "maskedWinnerName", source = "auction.winner.username", qualifiedByName = "maskUsername")

    @Mapping(target = "startTime", source = "auction.startTime")
    @Mapping(target = "endTime", source = "auction.endTime")
    @Mapping(target = "auctionStatus", source = "auction.status")
    ProductResponseDTO toDTO(Product product, Auction auction);
}