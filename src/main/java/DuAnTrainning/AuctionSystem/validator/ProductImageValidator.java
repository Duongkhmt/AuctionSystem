package DuAnTrainning.AuctionSystem.validator;

import DuAnTrainning.AuctionSystem.exception.ApplicationException;
import DuAnTrainning.AuctionSystem.exception.ErrorCode;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Set;

@Component
public class ProductImageValidator {

    private static final int MAX_IMAGE_COUNT = 20;
    private static final long MAX_IMAGE_SIZE_BYTES = 5L * 1024 * 1024;
    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of("image/jpeg", "image/png", "image/webp");

    public void validate(List<MultipartFile> images) {
        if (images == null || images.isEmpty()) {
            throw new ApplicationException(ErrorCode.IMAGE_REQUIRED);
        }

        if (images.size() > MAX_IMAGE_COUNT) {
            throw new ApplicationException(ErrorCode.TOO_MANY_IMAGES);
        }

        boolean hasBlankUrl = images.stream()
                .anyMatch(image -> image == null
                        || image.isEmpty()
                        || image.getContentType() == null
                        || !ALLOWED_CONTENT_TYPES.contains(image.getContentType().toLowerCase())
                        || image.getSize() > MAX_IMAGE_SIZE_BYTES);
        if (hasBlankUrl) {
            throw new ApplicationException(ErrorCode.INVALID_IMAGE_FILE);
        }
    }
}
