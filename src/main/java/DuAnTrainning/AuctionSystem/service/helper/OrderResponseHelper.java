package DuAnTrainning.AuctionSystem.service.helper;

import DuAnTrainning.AuctionSystem.dto.response.CheckoutResponseDTO;
import DuAnTrainning.AuctionSystem.dto.response.SellerOrderResponseDTO;
import DuAnTrainning.AuctionSystem.dto.response.WonAuctionResponseDTO;
import DuAnTrainning.AuctionSystem.entity.Order;
import DuAnTrainning.AuctionSystem.entity.ProductImage;
import DuAnTrainning.AuctionSystem.mapper.OrderMapper;
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
        // 1. Nhờ MapStruct chép các thuộc tính thô (orderId, winningPrice, status, productTitle...)
        WonAuctionResponseDTO dto = orderMapper.toWonAuctionDTO(order);

        // 2. Tự xử lý logic lấy ảnh đại diện Thumbnail (Ảnh đầu tiên trong danh sách)
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

    // 4. Đóng gói DTO cho danh sách đơn hàng phía Người Bán
    public List<SellerOrderResponseDTO> buildSellerOrderDTOList(List<Order> orders) {
        return orders.stream().map(orderMapper::toSellerOrderDTO).toList();
    }
}
