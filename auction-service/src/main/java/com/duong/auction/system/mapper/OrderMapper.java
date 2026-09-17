package com.duong.auction.system.mapper;

import com.duong.auction.system.dto.response.SellerOrderResponseDTO;
import com.duong.auction.system.dto.response.WonAuctionResponseDTO;
import com.duong.auction.system.entity.Auction;
import com.duong.auction.system.entity.Order;
import com.duong.auction.system.entity.User;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.math.BigDecimal;

/**
 * Mapper MapStruct ánh xạ khởi tạo Đơn hàng trúng thầu (Order Entity).
 */
@Mapper(componentModel = "spring")
public interface OrderMapper {

    // 1. Ánh xạ từ thông tin Auction, Buyer và mức giá thắng thầu sang Order Entity (Tự động set status = UNPAID)
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "auction", source = "auction")
    @Mapping(target = "product", source = "auction.product")
    @Mapping(target = "buyer", source = "buyer")
    @Mapping(target = "seller", source = "auction.product.seller")
    @Mapping(target = "winningPrice", source = "winningPrice")
    @Mapping(target = "shippingAddress", ignore = true)
    @Mapping(target = "phoneNumber", ignore = true)
    @Mapping(target = "courierName", ignore = true)
    @Mapping(target = "trackingNumber", ignore = true)
    @Mapping(target = "paymentDeadline", ignore = true)
    @Mapping(target = "status", constant = "UNPAID") // 👈 Gán status = UNPAID bằng MapStruct
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    Order toEntity(Auction auction, User buyer, BigDecimal winningPrice);

    // 2. Ánh xạ cơ bản từ Order Entity sang WonAuctionResponseDTO (Ảnh sẽ do Helper gán)
    @Mapping(target = "orderId", source = "id")
    @Mapping(target = "auctionId", source = "auction.id")
    @Mapping(target = "productId", source = "product.id")
    @Mapping(target = "productTitle", source = "product.title")
    @Mapping(target = "productImage", ignore = true) // 👈 Để Helper xử lý gán ảnh!
    @Mapping(target = "winningPrice", source = "winningPrice")
    @Mapping(target = "status", source = "status")
    WonAuctionResponseDTO toWonAuctionDTO(Order order);

    // 3. Ánh xạ từ Order Entity sang SellerOrderResponseDTO cho Người Bán
    @Mapping(target = "orderId", source = "id")
    @Mapping(target = "productId", source = "product.id")
    @Mapping(target = "productTitle", source = "product.title")
    @Mapping(target = "winningPrice", source = "winningPrice")
    @Mapping(target = "buyerName", source = "buyer.username")
    @Mapping(target = "buyerPhone", source = "phoneNumber")
    @Mapping(target = "shippingAddress", source = "shippingAddress")
    @Mapping(target = "status", source = "status")
    SellerOrderResponseDTO toSellerOrderDTO(Order order);
}

