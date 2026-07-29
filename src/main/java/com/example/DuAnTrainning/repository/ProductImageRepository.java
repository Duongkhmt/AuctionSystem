package com.example.DuAnTrainning.repository;

import com.example.DuAnTrainning.entity.ProductImage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface ProductImageRepository extends JpaRepository<ProductImage, Long>{
    List<ProductImage> findByProductIdOrderByDisplayOrderAsc(Long productId);
    List<ProductImage> findByProductIdInOrderByProductIdAscDisplayOrderAsc(
            Collection<Long> productIds
    );
    List<ProductImage> findByIdInAndProductId(Collection<Long> ids, Long productId);
    void deleteByProductId(Long productId);
}
