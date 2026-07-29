package com.example.DuAnTrainning.repository;

import com.example.DuAnTrainning.entity.Product;
import com.example.DuAnTrainning.enums.ProductStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ProductRepository extends JpaRepository<Product, Long> {
    List<Product> findBySeller_IdOrderByCreatedAtDesc(Long sellerId);
    List<Product> findByStatusOrderByCreatedAtDesc(ProductStatus status);
}

