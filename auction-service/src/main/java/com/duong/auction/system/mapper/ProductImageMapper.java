package com.duong.auction.system.mapper;

import com.duong.auction.system.entity.Product;
import com.duong.auction.system.entity.ProductImage;
import com.duong.auction.system.service.CloudinaryService.UploadedImage;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.IntStream;

/**
 * Mapper ánh xạ danh sách ảnh vừa tải lên Cloudinary (UploadedImage) 
 * sang danh sách Entity ProductImage lưu xuống Database.
 */
@Component
public class ProductImageMapper {

    // 1. Overload dùng cho luồng Tạo Sản Phẩm (startDisplayOrder mặc định = 0)
    public List<ProductImage> toEntities(Product product, List<UploadedImage> uploadedImages) {
        return toEntities(product, uploadedImages, 0);
    }

    // 2. Chuyển đổi danh sách UploadedImage sang danh sách Entity ProductImage kèm thứ tự hiển thị displayOrder
    public List<ProductImage> toEntities(Product product, List<UploadedImage> uploadedImages, int startDisplayOrder) {
        return IntStream.range(0, uploadedImages.size())
                .mapToObj(index -> {
                    ProductImage image = new ProductImage();
                    image.setProduct(product);
                    image.setImageUrl(uploadedImages.get(index).secureUrl());
                    image.setPublicId(uploadedImages.get(index).publicId()); // Lưu publicId để phục vụ việc xóa trên Cloudinary sau này
                    image.setDisplayOrder(startDisplayOrder + index);
                    return image;
                })
                .toList();
    }
}