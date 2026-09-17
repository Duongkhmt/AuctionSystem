package com.duong.auction.system.mapper;

import com.duong.auction.system.dto.request.ProductRequestDTO;
import com.duong.auction.system.dto.request.ProductUpdateRequestDTO;
import com.duong.auction.system.entity.Auction;
import com.duong.auction.system.entity.Product;
import org.mapstruct.*;

/**
 * Mapper MapStruct ánh xạ giữa thông tin cấu hình Đấu Giá trong Request DTO và Auction Entity.
 */
@Mapper(componentModel = "spring")
public interface AuctionMapper {

    // 1. Ánh xạ từ ProductRequestDTO sang Auction Entity mới (Tự động gán currentPrice = startPrice, status = PENDING_APPROVAL)
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "product", source = "product")
    @Mapping(target = "currentPrice", source = "dto.startPrice")
    @Mapping(target = "status", constant = "PENDING_APPROVAL")
    @Mapping(target = "createdAt", ignore = true)
    Auction toEntity(Product product, ProductRequestDTO dto);

    // 2. Cập nhật thông tin Auction Entity hiện tại từ ProductUpdateRequestDTO khi chỉnh sửa bài đăng
    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "product", ignore = true)
    @Mapping(target = "currentPrice", source = "startPrice")
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    void updateAuctionFromDto(ProductUpdateRequestDTO dto, @MappingTarget Auction auction);
}
