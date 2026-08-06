package DuAnTrainning.AuctionSystem.service.helper;

import DuAnTrainning.AuctionSystem.entity.Auction;
import DuAnTrainning.AuctionSystem.entity.Product;
import DuAnTrainning.AuctionSystem.enums.ProductStatus;
import DuAnTrainning.AuctionSystem.exception.ApplicationException;
import DuAnTrainning.AuctionSystem.exception.ErrorCode;
import DuAnTrainning.AuctionSystem.repository.AuctionRepository;
import DuAnTrainning.AuctionSystem.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Helper tìm kiếm và gom cặp thông tin Sản phẩm (Product) + Phiên đấu giá (Auction) 
 * phục vụ cho quy trình kiểm duyệt bài đăng của Admin.
 */
@Component
@RequiredArgsConstructor
public class ProductAuctionLookupHelper {
    
    private final ProductRepository productRepository;
    private final AuctionRepository auctionRepository;

    // Record gom bộ đôi Product và Auction thành 1 đối tượng duy nhất
    public record PendingProductAuctionHolder(Product product, Auction auction) {}

    // Tìm bài đăng đang ở trạng thái PENDING (Chờ duyệt) và trả về bộ đôi đối tượng
    public PendingProductAuctionHolder findPendingProductAndAuction(Long productId) {
        // 1. Tìm thông tin Sản phẩm theo productId
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ApplicationException(ErrorCode.PRODUCT_NOT_FOUND));

        // 2. Tìm phiên Auction gắn liền với productId này
        Auction auction = auctionRepository.findByProduct_Id(productId)
                .orElseThrow(() -> new ApplicationException(ErrorCode.AUCTION_NOT_FOUND));

        // 3. Kiểm tra bài đăng có đang đúng ở trạng thái PENDING chờ Admin duyệt hay không
        if (product.getStatus() != ProductStatus.PENDING) {
            throw new ApplicationException(ErrorCode.PRODUCT_NOT_PENDING_APPROVAL);
        }

        // 4. Trả về đối tượng chứa bộ đôi Product và Auction
        return new PendingProductAuctionHolder(product, auction);
    }
}
