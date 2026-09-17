package com.duong.auction.system.service.helper;

import com.duong.auction.system.dto.response.CheckoutResponseDTO;
import com.duong.auction.system.dto.response.SellerOrderResponseDTO;
import com.duong.auction.system.dto.response.WonAuctionResponseDTO;
import com.duong.auction.system.entity.Order;
import com.duong.auction.system.entity.ProductImage;
import com.duong.auction.system.mapper.OrderMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Helper chuyên trách đóng gói và xử lý ảnh đại diện DTO cho Đơn hàng trúng thầu.
 */
@Component
@RequiredArgsConstructor
public class OrderResponseHelper {

    private final OrderMapper orderMapper;

    // 1. Đóng gói DTO cho 1 đơn hàng trúng thầu của Người Mua
    public WonAuctionResponseDTO buildWonAuctionDTO(Order order) {
        //Gọi mapstruct map thuộc tính cơ bản
        WonAuctionResponseDTO dto = orderMapper.toWonAuctionDTO(order);
        //Tự lấy ảnh đại diện cho sản phẩm
        List<ProductImage> images = order.getProduct().getImages();
        if (images != null && !images.isEmpty()) {
            dto.setProductImage(images.get(0).getImageUrl());
        }

        return dto;
    }

    // 2. Đóng gói DTO cho danh sách đơn hàng trúng thầu
    public List<WonAuctionResponseDTO> buildWonAuctionDTOList(List<Order> orders) {
        return orders.stream()
                .map(this::buildWonAuctionDTO)
                .toList();
    }

    // 3. Đóng gói DTO kết quả thanh toán Checkout thành công
    public CheckoutResponseDTO buildCheckoutDTO(Order order, String transactionCode) {
        return CheckoutResponseDTO.builder()
                .orderId(order.getId())
                .status(order.getStatus())
                .winningPrice(order.getWinningPrice())
                .transactionCode(transactionCode)
                .build();
    }

    // 4. Đóng gói DTO cho 1 đơn hàng phía Người Bán
    public SellerOrderResponseDTO buildSellerOrderDTO(Order order) {
        SellerOrderResponseDTO dto = orderMapper.toSellerOrderDTO(order);
        List<ProductImage> images = order.getProduct().getImages();
        if (images != null && !images.isEmpty()) {
            dto.setProductImage(images.get(0).getImageUrl());
        }
        return dto;
    }

    // 5. Đóng gói DTO cho danh sách đơn hàng phía Người Bán
    public List<SellerOrderResponseDTO> buildSellerOrderDTOList(List<Order> orders) {
        return orders.stream()
                .map(this::buildSellerOrderDTO)
                .toList();
    }
}
