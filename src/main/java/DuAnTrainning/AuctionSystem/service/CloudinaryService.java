package DuAnTrainning.AuctionSystem.service;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import DuAnTrainning.AuctionSystem.exception.ApplicationException;
import DuAnTrainning.AuctionSystem.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class CloudinaryService {

    private static final String PRODUCT_IMAGE_FOLDER = "auction-products";

    private final Cloudinary cloudinary;

    public List<UploadedImage> uploadAll(List<MultipartFile> imageFiles) {
        List<UploadedImage> uploadedImages = new ArrayList<>();
        try {
            for (MultipartFile imageFile : imageFiles) {
                Map<?, ?> result = cloudinary.uploader().upload(imageFile.getBytes(), ObjectUtils.asMap(
                        "folder", PRODUCT_IMAGE_FOLDER,
                        "resource_type", "image",
                        "allowed_formats", List.of("jpg", "jpeg", "png", "webp")
                ));
                uploadedImages.add(new UploadedImage(
                        result.get("secure_url").toString(),
                        result.get("public_id").toString()
                ));
            }
            return uploadedImages;
        } catch (IOException | RuntimeException exception) {
            deleteAll(uploadedImages);
            throw new ApplicationException(ErrorCode.IMAGE_UPLOAD_FAILED);
        }
    }

    public void deleteByPublicId(String publicId) {
        if (publicId == null || publicId.isBlank()) return;
        try {
            cloudinary.uploader().destroy(publicId, ObjectUtils.asMap(
                    "resource_type", "image",
                    "invalidate", true
            ));
        } catch (IOException | RuntimeException ignored) {
        }
    }

    public void deleteAll(List<UploadedImage> uploadedImages) {
        for (UploadedImage image : uploadedImages) {
            try {
                cloudinary.uploader().destroy(image.publicId(), ObjectUtils.asMap(
                        "resource_type", "image",
                        "invalidate", true
                ));
            } catch (IOException | RuntimeException ignored) {
                // Giữ lỗi ban đầu; ảnh sẽ được dọn lại bằng tác vụ quản trị nếu cần.
            }
        }
    }

    public record UploadedImage(String secureUrl, String publicId) {
    }
}
