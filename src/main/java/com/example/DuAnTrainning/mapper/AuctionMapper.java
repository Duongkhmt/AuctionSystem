package com.example.DuAnTrainning.mapper;

import com.example.DuAnTrainning.dto.request.ProductRequestDTO;
import com.example.DuAnTrainning.dto.request.ProductUpdateRequestDTO;
import com.example.DuAnTrainning.entity.Auction;
import com.example.DuAnTrainning.entity.Product;
import org.mapstruct.*;

@Mapper(componentModel = "spring")
public interface AuctionMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "product", source = "product")
    @Mapping(target = "currentPrice", source = "dto.startPrice")
    @Mapping(target = "status", constant = "PENDING_APPROVAL")
    @Mapping(target = "createdAt", ignore = true)
    Auction toEntity(Product product, ProductRequestDTO dto);

    // THÊM METHOD NÀY CHO CHỨC NĂNG UPDATE
    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "product", ignore = true)
    @Mapping(target = "currentPrice", source = "startPrice")
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    void updateAuctionFromDto(ProductUpdateRequestDTO dto, @MappingTarget Auction auction);
}
