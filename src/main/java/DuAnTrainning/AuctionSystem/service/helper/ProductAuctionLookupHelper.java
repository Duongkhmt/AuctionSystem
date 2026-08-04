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

@Component
@RequiredArgsConstructor
public class ProductAuctionLookupHelper {
    
    private final ProductRepository productRepository;
    private final AuctionRepository auctionRepository;

    // GỘP RECORD VÀO ĐÂY -> Không tạo file rác bên ngoài!
    public record PendingProductAuctionHolder(Product product, Auction auction) {}

    public PendingProductAuctionHolder findPendingProductAndAuction(Long productId) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ApplicationException(ErrorCode.PRODUCT_NOT_FOUND));

        Auction auction = auctionRepository.findByProduct_Id(productId)
                .orElseThrow(() -> new ApplicationException(ErrorCode.AUCTION_NOT_FOUND));

        if (product.getStatus() != ProductStatus.PENDING) {
            throw new ApplicationException(ErrorCode.PRODUCT_NOT_PENDING_APPROVAL);
        }

        return new PendingProductAuctionHolder(product, auction);
    }
}
