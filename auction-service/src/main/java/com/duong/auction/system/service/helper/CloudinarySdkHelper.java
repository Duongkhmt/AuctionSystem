package com.duong.auction.system.service.helper;

import com.cloudinary.utils.ObjectUtils;
import com.duong.auction.system.config.CloudinaryProperties;

import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Helper đóng gói các tham số và bóc tách dữ liệu phản hồi từ Cloudinary SDK.
 * Đạt chuẩn đặt tên Helper thống nhất toàn bộ dự án DuAnTrainning.
 */
public final class CloudinarySdkHelper {

    private CloudinarySdkHelper() {} // Private constructor

    // 🟢 1. Helper đóng gói tham số Upload
    @SuppressWarnings("unchecked")
    public static Map<String, Object> buildUploadParams(CloudinaryProperties props) {
        return ObjectUtils.asMap(
                "folder", props.getFolder(),
                "resource_type", props.getResourceType(),
                "allowed_formats", props.getAllowedFormats()
        );
    }

    // 🟢 2. Helper đóng gói tham số Xóa (Destroy)
    @SuppressWarnings("unchecked")
    public static Map<String, Object> buildDestroyParams(CloudinaryProperties props) {
        return ObjectUtils.asMap(
                "resource_type", props.getResourceType(),
                "invalidate", true
        );
    }

    // 🟢 3. Helper bóc tách Secure URL
    public static String extractUrl(Map<?, ?> res) {
        return res != null && res.get("secure_url") != null ? res.get("secure_url").toString() : "";
    }

    // 🟢 4. Helper bóc tách Public ID
    public static String extractId(Map<?, ?> res) {
        return res != null && res.get("public_id") != null ? res.get("public_id").toString() : "";
    }

    public static long getMaxFileSizeBytes(CloudinaryProperties props) {
        return props.getMaxFileSizeMb() * 1024 * 1024;
    }

    // 🟢 6. Helper tạo Tập hợp MIME Types hợp lệ: image/jpeg, image/png, image/webp (dành cho Validator)
    public static Set<String> getAllowedContentTypes(CloudinaryProperties props) {
        return props.getAllowedFormats().stream()
                .map(format -> "image/" + (format.equalsIgnoreCase("jpg") ? "jpeg" : format.toLowerCase()))
                .collect(Collectors.toSet());
    }
}
