package com.duong.auction.system.repository;

import com.duong.auction.system.entity.ProductImage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface ProductImageRepository extends JpaRepository<ProductImage, Long>{
    //Lấy danh sách thứ tự các ảnh của 1 sản phầm sắp xeeos tăng dần
    List<ProductImage> findByProductIdOrderByDisplayOrderAsc(Long productId);
    //lấy danh sách các ảnh của các sản phẩm rồi sắp xếp theo id sản phầm tăng dần và thứ tự các ảnh tăng dần
    List<ProductImage> findByProductIdInOrderByProductIdAscDisplayOrderAsc(
            Collection<Long> productIds
    );
    //kiểm tra ảnh có thực sự thuộc Product đó không.
    List<ProductImage> findByIdInAndProductId(Collection<Long> ids, Long productId);

    void deleteByProductId(Long productId);
}
