package com.example.DuAnTrainning.mapper;


import com.example.DuAnTrainning.dto.request.ProductRequestDTO;
import com.example.DuAnTrainning.dto.request.ProductUpdateRequestDTO;
import com.example.DuAnTrainning.dto.response.ProductResponseDTO;
import com.example.DuAnTrainning.entity.Auction;
import com.example.DuAnTrainning.entity.Product;
import org.mapstruct.*;

@Mapper(componentModel = "spring")
public interface ProductMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "images", ignore = true)
    @Mapping(target = "seller", ignore = true)
    @Mapping(target = "category", ignore = true)
    @Mapping(target = "status", ignore = true) // set tường minh ở Service = PENDING, không map từ request
    Product toEntity(ProductRequestDTO requestDTO);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "seller", ignore = true)
    @Mapping(target = "category", ignore = true)
    @Mapping(target = "images", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    void updateProductFromDto(ProductUpdateRequestDTO dto, @MappingTarget Product product);

    @Mapping(target = "productId", source = "product.id")
    @Mapping(target = "sellerId", source = "product.seller.id")
    @Mapping(target = "categoryId", source = "product.category.id")
    @Mapping(target = "status", source = "product.status")
    @Mapping(target = "createdAt", source = "product.createdAt")
    @Mapping(target = "rejectionReason", source = "product.rejectionReason")
    @Mapping(target = "imageUrls", ignore = true)
    @Mapping(target = "images", ignore = true)
    @Mapping(target = "auctionId", source = "auction.id")
    @Mapping(target = "auctionType", source = "auction.auctionType")
    @Mapping(target = "startPrice", source = "auction.startPrice")
    @Mapping(target = "currentPrice", source = "auction.currentPrice")
    @Mapping(target = "bidStep", source = "auction.bidStep")
    @Mapping(target = "reservePrice", source = "auction.reservePrice")
    @Mapping(target = "startTime", source = "auction.startTime")
    @Mapping(target = "endTime", source = "auction.endTime")
    @Mapping(target = "auctionStatus", source = "auction.status")
    ProductResponseDTO toDTO(Product product, Auction auction);
}