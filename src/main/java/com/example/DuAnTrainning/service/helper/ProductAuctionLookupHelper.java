package com.example.DuAnTrainning.service.helper;

import com.example.DuAnTrainning.entity.Auction;
import com.example.DuAnTrainning.entity.Product;
import com.example.DuAnTrainning.enums.ProductStatus;
import com.example.DuAnTrainning.exception.ApplicationException;
import com.example.DuAnTrainning.exception.ErrorCode;
import com.example.DuAnTrainning.repository.AuctionRepository;
import com.example.DuAnTrainning.repository.ProductRepository;
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
