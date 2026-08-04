package DuAnTrainning.AuctionSystem.repository;

import DuAnTrainning.AuctionSystem.entity.Product;
import DuAnTrainning.AuctionSystem.enums.ProductStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ProductRepository extends JpaRepository<Product, Long> {
    List<Product> findBySeller_IdOrderByCreatedAtDesc(Long sellerId);
    List<Product> findByStatusOrderByCreatedAtDesc(ProductStatus status);
}

