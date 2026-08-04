package DuAnTrainning.AuctionSystem.service.helper;

import DuAnTrainning.AuctionSystem.dto.response.ProductImageResponseDTO;
import DuAnTrainning.AuctionSystem.dto.response.ProductResponseDTO;
import DuAnTrainning.AuctionSystem.entity.Auction;
import DuAnTrainning.AuctionSystem.entity.Product;
import DuAnTrainning.AuctionSystem.entity.ProductImage;
import DuAnTrainning.AuctionSystem.exception.ApplicationException;
import DuAnTrainning.AuctionSystem.exception.ErrorCode;
import DuAnTrainning.AuctionSystem.mapper.ProductMapper;
import DuAnTrainning.AuctionSystem.repository.AuctionRepository;
import DuAnTrainning.AuctionSystem.repository.ProductImageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class ProductResponseHelper {

    private final AuctionRepository auctionRepository;
    private final ProductImageRepository productImageRepository;
    private final ProductMapper productMapper;

    // =========================================================================
    // 1. HÀM CHÍNH (Core): Nhận đủ 3 dữ liệu -> Convert ra DTO
    // Dùng trực tiếp cho createProduct() để KHÔNG QUERY LẠI DB!
    // =========================================================================
    public ProductResponseDTO build(Product product, Auction auction, List<ProductImage> productImages) {

        List<ProductImageResponseDTO> images = productImages.stream()
                .map(img -> new ProductImageResponseDTO(img.getId(), img.getImageUrl(), img.getDisplayOrder()))
                .toList();

        ProductResponseDTO dto = productMapper.toDTO(product, auction);
        dto.setImages(images);
        return dto;
    }

    // =========================================================================
    // 2. HÀM TIỆN ÍCH CHO 1 SẢN PHẨM: Khi chỉ có 'Product'
    // Single Query DB -> Gọi lại Hàm Chính 1
    // =========================================================================
    public ProductResponseDTO build(Product product) {
        Auction auction = auctionRepository.findByProduct_Id(product.getId())
                .orElseThrow(() -> new ApplicationException(ErrorCode.AUCTION_NOT_FOUND));

        List<ProductImage> productImages = productImageRepository
                .findByProductIdOrderByDisplayOrderAsc(product.getId());

        return build(product, auction, productImages);
    }

    // =========================================================================
    // 3. HÀM BATCH DÀNH CHO DANH SÁCH: Giữ nguyên thuật toán tối ưu chống N+1
    // Batch Query DB -> Gọi lại Hàm Chính 1 cho từng phần tử!
    // =========================================================================
    public List<ProductResponseDTO> buildAll(List<Product> products) {
        if (products.isEmpty()) {
            return List.of();
        }

        List<Long> productIds = products.stream()
                .map(Product::getId)
                .toList();

        Map<Long, Auction> auctionByProductId = auctionRepository
                .findByProduct_IdIn(productIds)
                .stream()
                .collect(Collectors.toMap(
                        auction -> auction.getProduct().getId(),
                        Function.identity()
                ));

        Map<Long, List<ProductImage>> imagesByProductId = productImageRepository
                .findByProductIdInOrderByProductIdAscDisplayOrderAsc(productIds)
                .stream()
                .collect(Collectors.groupingBy(
                        image -> image.getProduct().getId()
                ));

        return products.stream()
                .map(product -> {
                    Auction auction = auctionByProductId.get(product.getId());
                    if (auction == null) {
                        throw new ApplicationException(ErrorCode.AUCTION_NOT_FOUND);
                    }
                    List<ProductImage> productImages = imagesByProductId.getOrDefault(product.getId(), List.of());

                    // TÁI SỬ DỤNG HÀM CHÍNH 1 ➔ Không lặp lại đoạn convert!
                    return build(product, auction, productImages);
                })
                .toList();
    }
}
