package com.example.DuAnTrainning.validator;

import com.example.DuAnTrainning.exception.ApplicationException;
import com.example.DuAnTrainning.exception.ErrorCode;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ProductImageValidator {

    private static final int MAX_IMAGE_COUNT = 20;

    public void validate(List<String> imageUrls) {
        if (imageUrls == null || imageUrls.isEmpty()) {
            throw new ApplicationException(ErrorCode.IMAGE_REQUIRED);
        }

        if (imageUrls.size() > MAX_IMAGE_COUNT) {
            throw new ApplicationException(ErrorCode.TOO_MANY_IMAGES);
        }

        boolean hasBlankUrl = imageUrls.stream()
                .anyMatch(url -> url == null || url.isBlank());
        if (hasBlankUrl) {
            throw new ApplicationException(ErrorCode.INVALID_IMAGE_URL);
        }
    }
}
