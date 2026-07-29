package com.example.DuAnTrainning.mapper;


import com.example.DuAnTrainning.entity.Product;
import com.example.DuAnTrainning.entity.ProductImage;
import com.example.DuAnTrainning.service.CloudinaryService.UploadedImage;
import org.springframework.stereotype.Component;
import java.util.List;
import java.util.stream.IntStream;

@Component
public class ProductImageMapper {

    // Overload dùng cho CreateProduct (startDisplayOrder mặc định = 0)
    public List<ProductImage> toEntities(Product product, List<UploadedImage> uploadedImages) {
        return toEntities(product, uploadedImages, 0);
    }
    public List<ProductImage> toEntities(Product product, List<UploadedImage> uploadedImages, int startDisplayOrder) {
        return IntStream.range(0, uploadedImages.size())
                .mapToObj(index -> {
                    ProductImage image = new ProductImage();
                    image.setProduct(product);
                    image.setImageUrl(uploadedImages.get(index).secureUrl());
                    image.setPublicId(uploadedImages.get(index).publicId()); // Lưu publicId
                    image.setDisplayOrder(startDisplayOrder + index);
                    return image;
                })
                .toList();
    }
}