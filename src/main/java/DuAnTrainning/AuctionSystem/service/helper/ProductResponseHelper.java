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

/**
 * Helper chuyên trách đóng gói và chuyển đổi dữ liệu Sản phẩm (Product + Auction + ProductImages) 
 * thành ProductResponseDTO phẳng duy nhất trả về cho Frontend.
 * Chứa thuật toán Batch Query chống triệt để lỗi N+1 Query.
 */
@Component
@RequiredArgsConstructor
public class ProductResponseHelper {

    private final AuctionRepository auctionRepository;
    private final ProductImageRepository productImageRepository;
    private final ProductMapper productMapper;

    // =========================================================================
    // 1. HÀM CHÍNH (CORE): Đóng gói DTO khi đã có sẵn 3 đối tượng (Tránh query lại DB)
    // Dùng trực tiếp cho luồng tạo sản phẩm createProduct()
    // =========================================================================
    public ProductResponseDTO build(Product product, Auction auction, List<ProductImage> productImages) {
        // 1. Map danh sách ProductImage sang danh sách ProductImageResponseDTO
        List<ProductImageResponseDTO> images = productImages.stream()
                .map(img -> new ProductImageResponseDTO(img.getId(), img.getImageUrl(), img.getDisplayOrder()))
                .toList();

        // 2. Ánh xạ thuộc tính giữa Product và Auction sang ProductResponseDTO qua MapStruct
        ProductResponseDTO dto = productMapper.toDTO(product, auction);

        // 3. Đưa danh sách ảnh vào DTO và trả về kết quả
        dto.setImages(images);
        return dto;
    }

    // =========================================================================
    // 2. HÀM TIỆN ÍCH DÀNH CHO 1 SẢN PHẨM: Khi chỉ có đối tượng Product
    // Tự động single query Auction & ProductImages rồi gọi lại Hàm Chính 1
    // =========================================================================
    public ProductResponseDTO build(Product product) {
        // 1. Tìm thông tin phiên Auction đi kèm với sản phẩm này
        Auction auction = auctionRepository.findByProduct_Id(product.getId())
                .orElseThrow(() -> new ApplicationException(ErrorCode.AUCTION_NOT_FOUND));

        // 2. Tìm danh sách các hình ảnh ProductImage của sản phẩm này được sắp xếp thứ tự
        List<ProductImage> productImages = productImageRepository
                .findByProductIdOrderByDisplayOrderAsc(product.getId());

        // 3. Gọi Hàm Chính 1 để lắp ghép dữ liệu thành DTO
        return build(product, auction, productImages);
    }

    // =========================================================================
    // 3. HÀM BATCH DÀNH CHO DANH SÁCH: Thuật toán Gom Query chống N+1 Query
    // Gom tất cả ID sản phẩm -> Bắn 2 câu SQL duy nhất lấy hết Auction & Images -> Map trong RAM
    // =========================================================================
    public List<ProductResponseDTO> buildAll(List<Product> products) {
        // 1. Nếu danh sách sản phẩm rỗng -> Trả về danh sách rỗng ngay lập tức
        if (products.isEmpty()) {
            return List.of();
        }

        // 2. Trích xuất danh sách tất cả Product ID thành 1 List duy nhất
        List<Long> productIds = products.stream()
                .map(Product::getId)
                .toList();

        // 3. Bắn 1 câu SQL Batch (findByProduct_IdIn) lấy hết các Auction và nhóm thành Map<productId, Auction> trong RAM
        Map<Long, Auction> auctionByProductId = auctionRepository
                .findByProduct_IdIn(productIds)
                .stream()
                .collect(Collectors.toMap(
                        auction -> auction.getProduct().getId(),
                        Function.identity()
                ));

        // 4. Bắn 1 câu SQL Batch lấy hết các ProductImage và nhóm thành Map<productId, List<ProductImage>> trong RAM
        Map<Long, List<ProductImage>> imagesByProductId = productImageRepository
                .findByProductIdInOrderByProductIdAscDisplayOrderAsc(productIds)
                .stream()
                .collect(Collectors.groupingBy(
                        image -> image.getProduct().getId()
                ));

        // 5. Duyệt qua từng sản phẩm trong RAM, ghép Auction & Images tương ứng và gọi lại Hàm Chính 1
        return products.stream()
                .map(product -> {
                    Auction auction = auctionByProductId.get(product.getId());
                    if (auction == null) {
                        throw new ApplicationException(ErrorCode.AUCTION_NOT_FOUND);
                    }
                    List<ProductImage> productImages = imagesByProductId.getOrDefault(product.getId(), List.of());

                    return build(product, auction, productImages);
                })
                .toList();
    }
}
