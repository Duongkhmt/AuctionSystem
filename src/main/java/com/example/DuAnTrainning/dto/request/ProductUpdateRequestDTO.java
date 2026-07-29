package com.example.DuAnTrainning.dto.request;

import com.example.DuAnTrainning.enums.AuctionType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Getter
@Setter
public class ProductUpdateRequestDTO {

    @NotNull(message = "Vui lòng chọn danh mục")
    private Long categoryId;

    @Size(min = 10, max = 150, message = "Tiêu đề phải từ 10-150 ký tự")
    private String title;

    private String description;
    private Map<String, Object> attributes;

    private List<Long> deleteImageIds;     // Các ID ảnh cũ muốn xóa
    private List<MultipartFile> newImages; // Các file ảnh mới gửi lên

    private AuctionType auctionType;

    @DecimalMin(value = "0.01", message = "Giá khởi điểm phải lớn hơn 0")
    private BigDecimal startPrice;

    @DecimalMin(value = "0.01", message = "Giá bảo lưu phải lớn hơn 0")
    private BigDecimal reservePrice;

    @DecimalMin(value = "0.01", message = "Bước giá phải lớn hơn 0")
    private BigDecimal bidStep;

    private LocalDateTime startTime;
    private LocalDateTime endTime;
}
