package DuAnTrainning.AuctionSystem.dto.request;

import DuAnTrainning.AuctionSystem.enums.AuctionType;
import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.Setter;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Getter
@Setter
public class ProductRequestDTO {

    @NotNull(message = "Vui lòng chọn danh mục")
    private Long categoryId;

    @NotBlank(message = "Tiêu đề không được để trống")
    @Size(min = 10, max = 150, message = "Tiêu đề phải từ 10-150 ký tự")
    private String title;

    @NotBlank(message = "Mô tả không được để trống")
    private String description;

    private Map<String, Object> attributes;

    @NotEmpty(message = "Sản phẩm cần tối thiểu 1 ảnh")
    private List<MultipartFile> images;

    // ===== THÔNG TIN ĐẤU GIÁ =====


    @NotNull(message = "Loại đấu giá không được để trống")
    private AuctionType auctionType;


    @NotNull(message = "Giá khởi điểm không được để trống")
    @DecimalMin(
            value = "0.01",
            message = "Giá khởi điểm phải lớn hơn 0"
    )
    private BigDecimal startPrice;


    // Chỉ bắt buộc khi auctionType = RESERVE
    @DecimalMin(
            value = "0.01",
            message = "Giá bảo lưu phải lớn hơn 0"
    )
    private BigDecimal reservePrice;


    @NotNull(message = "Bước giá không được để trống")
    @DecimalMin(
            value = "0.01",
            message = "Bước giá phải lớn hơn 0"
    )
    private BigDecimal bidStep;

    @DecimalMin(value = "0.01", message = "Giá mua ngay phải lớn hơn 0")
    private BigDecimal buyNowPrice;

    @NotNull(message = "Thời gian bắt đầu không được để trống")
    private LocalDateTime startTime;


    @NotNull(message = "Thời gian kết thúc không được để trống")
    private LocalDateTime endTime;
}
