package com.example.DuAnTrainning.mapper;

import com.example.DuAnTrainning.entity.Product;
import com.example.DuAnTrainning.entity.ProductImage;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.IntStream;

@Component
public class ProductImageMapper {

    public List<ProductImage> toEntities(
            Product product,
            List<String> imageUrls
    ) {
        return IntStream.range(0, imageUrls.size())
                .mapToObj(index -> {
                    ProductImage image = new ProductImage();

                    image.setProduct(product);
                    image.setImageUrl(imageUrls.get(index));
                    image.setDisplayOrder(index);

                    return image;
                })
                .toList();
    }
}