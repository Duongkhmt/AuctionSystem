package com.duong.auction.system.service;

import com.cloudinary.Cloudinary;
import com.cloudinary.Uploader;
import com.duong.auction.system.config.CloudinaryProperties;
import com.duong.auction.system.exception.ApplicationException;
import com.duong.auction.system.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.*;

/**
 * Class Unit Test kiểm thử CloudinaryService.
 * Dùng Mockito giả lập SDK Cloudinary và Uploader để test upload/xóa ảnh song song mà không đẩy ảnh rác lên Cloud thật.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Unit Test Cho Class CloudinaryService")
class CloudinaryServiceTest {

    @Mock
    private Cloudinary cloudinary;

    @Mock
    private Uploader uploader;

    @Mock
    private CloudinaryProperties cloudinaryProperties;

    @InjectMocks
    private CloudinaryService cloudinaryService;

    @BeforeEach
    void setUp() {
        lenient().when(cloudinary.uploader()).thenReturn(uploader);
        lenient().when(cloudinaryProperties.getFolder()).thenReturn("auction-products");
        lenient().when(cloudinaryProperties.getResourceType()).thenReturn("image");
    }

    @Test
    @DisplayName("Upload lẻ 1 bức ảnh thành công - Trả về UploadedImage record chứa secure_url và public_id")
    void uploadSingle_Success_ShouldReturnUploadedImage() throws IOException {
        // 1. GIVEN: Giả lập Cloudinary Uploader trả về Map chứa URL và public_id
        MultipartFile mockFile = mock(MultipartFile.class);
        given(mockFile.getBytes()).willReturn("dummy-image-content".getBytes());

        Map<String, Object> mockResult = Map.of(
                "secure_url", "https://res.cloudinary.com/demo/image/upload/sample.jpg",
                "public_id", "auction-products/sample_id"
        );
        given(uploader.upload(any(byte[].class), anyMap())).willReturn(mockResult);

        // 2. WHEN: Gọi hàm uploadSingle
        CloudinaryService.UploadedImage result = cloudinaryService.uploadSingle(mockFile);

        // 3. THEN: Trả về record chứa đúng URL và publicId
        assertThat(result).isNotNull();
        assertThat(result.secureUrl()).isEqualTo("https://res.cloudinary.com/demo/image/upload/sample.jpg");
        assertThat(result.publicId()).isEqualTo("auction-products/sample_id");
    }

    @Test
    @DisplayName("Upload lẻ 1 bức ảnh thất bại do IOException - Ném ngoại lệ RuntimeException")
    void uploadSingle_IOException_ShouldThrowRuntimeException() throws IOException {
        // 1. GIVEN: File giả lập ném lỗi IOException khi đọc bytes
        MultipartFile mockFile = mock(MultipartFile.class);
        given(mockFile.getOriginalFilename()).willReturn("test.png");
        given(mockFile.getBytes()).willThrow(new IOException("Disk read error"));

        // 2. WHEN & THEN: Bắt ngoại lệ RuntimeException chứa thông điệp lỗi
        assertThatThrownBy(() -> cloudinaryService.uploadSingle(mockFile))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Upload failed for file: test.png");
    }

    @Test
    @DisplayName("Upload danh sách ảnh rỗng hoặc null - Trả về danh sách rỗng")
    void uploadAll_NullOrEmptyList_ShouldReturnEmptyList() {
        // 1. WHEN & THEN
        assertThat(cloudinaryService.uploadAll(null)).isEmpty();
        assertThat(cloudinaryService.uploadAll(List.of())).isEmpty();
    }

    @Test
    @DisplayName("Upload song song danh sách ảnh thành công - Trả về danh sách UploadedImage đúng thứ tự")
    void uploadAll_Success_ShouldReturnUploadedImagesList() throws IOException {
        // 1. GIVEN: Danh sách 2 file ảnh
        MultipartFile file1 = mock(MultipartFile.class);
        MultipartFile file2 = mock(MultipartFile.class);
        given(file1.getBytes()).willReturn("bytes1".getBytes());
        given(file2.getBytes()).willReturn("bytes2".getBytes());

        Map<String, Object> result1 = Map.of("secure_url", "http://url1", "public_id", "id1");
        Map<String, Object> result2 = Map.of("secure_url", "http://url2", "public_id", "id2");

        given(uploader.upload(eq("bytes1".getBytes()), anyMap())).willReturn(result1);
        given(uploader.upload(eq("bytes2".getBytes()), anyMap())).willReturn(result2);

        // 2. WHEN: Gọi hàm uploadAll song song
        List<CloudinaryService.UploadedImage> results = cloudinaryService.uploadAll(List.of(file1, file2));

        // 3. THEN: Kiểm tra danh sách kết quả chứa đúng 2 bức ảnh
        assertThat(results).hasSize(2);
        assertThat(results.get(0).publicId()).isEqualTo("id1");
        assertThat(results.get(1).publicId()).isEqualTo("id2");
    }

    @Test
    @DisplayName("Upload song song 10 ảnh nhưng ảnh thứ 10 bị lỗi - Tự động dọn dẹp (delete) các ảnh đã lỡ up thành công trước đó và ném lỗi")
    void uploadAll_PartialFailure_ShouldCleanupSuccessfullyUploadedImagesAndThrowException() throws IOException {
        // 1. GIVEN: Giả lập 2 file ảnh (file 1 thành công, file 2 bị lỗi đọc bytes)
        MultipartFile file1 = mock(MultipartFile.class);
        MultipartFile file2 = mock(MultipartFile.class);
        given(file1.getBytes()).willReturn("bytes1".getBytes());
        given(file2.getBytes()).willThrow(new IOException("Network error on 10th image"));

        Map<String, Object> result1 = Map.of("secure_url", "http://url1", "public_id", "id1");
        given(uploader.upload(eq("bytes1".getBytes()), anyMap())).willReturn(result1);

        // 2. WHEN & THEN: Gọi uploadAll -> Bắt lỗi IMAGE_UPLOAD_FAILED và khẳng định đã tự động gọi destroy cho id1
        assertThatThrownBy(() -> cloudinaryService.uploadAll(List.of(file1, file2)))
                .isInstanceOf(ApplicationException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.IMAGE_UPLOAD_FAILED);

        // Khẳng định ảnh 1 (đã up thành công) tự động được dọn dẹp bằng uploader.destroy
        then(uploader).should(times(1)).destroy(eq("id1"), anyMap());
    }

    @Test
    @DisplayName("Xóa ảnh trên Cloudinary theo Public ID null hoặc rỗng - Không thực thi destroy")
    void deleteByPublicId_NullOrBlank_ShouldNotCallDestroy() throws IOException {
        // 1. WHEN: Gọi hàm xóa với publicId rỗng
        cloudinaryService.deleteByPublicId(null);
        cloudinaryService.deleteByPublicId("   ");

        // 2. THEN: Khẳng định không bao giờ gọi xuống uploader.destroy
        then(uploader).should(never()).destroy(any(), anyMap());
    }

    @Test
    @DisplayName("Xóa ảnh trên Cloudinary theo Public ID thành công - Gọi uploader.destroy")
    void deleteByPublicId_ValidId_ShouldCallDestroy() throws IOException {
        // 1. GIVEN: publicId hợp lệ
        String publicId = "auction-products/sample_id";

        // 2. WHEN: Gọi hàm deleteByPublicId
        cloudinaryService.deleteByPublicId(publicId);

        // 3. THEN: Khẳng định uploader.destroy được gọi đúng 1 lần với publicId đó
        then(uploader).should(times(1)).destroy(eq(publicId), anyMap());
    }
}
