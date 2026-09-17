package com.duong.auction.system.validator;

import com.duong.auction.system.config.CloudinaryProperties;
import com.duong.auction.system.exception.ApplicationException;
import com.duong.auction.system.exception.ErrorCode;
import com.duong.auction.system.service.helper.CloudinarySdkHelper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * Validator kiểm tra quy định tải ảnh dựa theo các chỉ số đọc động từ CloudinaryProperties.
 */
@Component
@RequiredArgsConstructor // 🟢 Tiêm dependency tự động qua Lombok
public class ProductImageValidator {

    private final CloudinaryProperties cloudinaryProperties;

    public void validate(List<MultipartFile> images) {
        if (images == null || images.isEmpty()) {
            throw new ApplicationException(ErrorCode.IMAGE_REQUIRED);
        }

        // 🟢 1. Đọc số lượng ảnh tối đa động (Max = 20)
        if (images.size() > cloudinaryProperties.getMaxImageCount()) {
            throw new ApplicationException(ErrorCode.TOO_MANY_IMAGES);
        }

        // 🟢 2. Đọc định dạng cho phép và dung lượng tối đa (5MB) động
        boolean hasBlankUrl = images.stream()
                .anyMatch(image -> image == null
                        || image.isEmpty()
                        || image.getContentType() == null
                        || !CloudinarySdkHelper.getAllowedContentTypes(cloudinaryProperties).contains(image.getContentType().toLowerCase())
                        || image.getSize() > CloudinarySdkHelper.getMaxFileSizeBytes(cloudinaryProperties));
        if (hasBlankUrl) {
            throw new ApplicationException(ErrorCode.INVALID_IMAGE_FILE);
        }
    }
}
