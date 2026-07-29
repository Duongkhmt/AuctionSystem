package com.example.DuAnTrainning.mapper;

import com.example.DuAnTrainning.dto.request.ProductRequestDTO;
import com.example.DuAnTrainning.entity.Auction;
import com.example.DuAnTrainning.entity.Product;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface AuctionMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "product", source = "product")
    @Mapping(target = "currentPrice", source = "dto.startPrice")
    @Mapping(target = "status", constant = "SCHEDULED")
    @Mapping(target = "createdAt", ignore = true)
    Auction toEntity(Product product, ProductRequestDTO dto);
}
