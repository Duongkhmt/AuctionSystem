package DuAnTrainning.AuctionSystem.service.helper;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * Helper tính toán Bước Giá Động (Dynamic Bid Step) dựa trên thang giá trị sản phẩm hiện tại
 * kết hợp ưu tiên Bước giá tùy chỉnh của Seller nếu lớn hơn mốc tối thiểu.
 */
@Component
public class BidStepCalculatorHelper {

    private static final BigDecimal ONE_MILLION = new BigDecimal("1000000");     // Mốc 1 Triệu VNĐ
    private static final BigDecimal TEN_MILLIONS = new BigDecimal("10000000");   // Mốc 10 Triệu VNĐ
    private static final BigDecimal STEP_LOW = new BigDecimal("10000");           // Bước giá 10.000đ cho sản phẩm < 1 Triệu
    private static final BigDecimal STEP_MEDIUM = new BigDecimal("100000");       // Bước giá 100.000đ cho sản phẩm từ 1M - 10M
    private static final BigDecimal STEP_HIGH = new BigDecimal("500000");         // Bước giá 500.000đ cho sản phẩm > 10M

    // 1. Tính toán bước giá dựa trên phân khúc giá hiện tại và bước giá cài đặt do Seller cấu hình
    public BigDecimal calculateBidStep(BigDecimal currentPrice, BigDecimal configuredBidStep) {
        BigDecimal dynamicStep;
        if (currentPrice == null || currentPrice.compareTo(ONE_MILLION) < 0) {
            dynamicStep = STEP_LOW;
        } else if (currentPrice.compareTo(TEN_MILLIONS) <= 0) {
            dynamicStep = STEP_MEDIUM;
        } else {
            dynamicStep = STEP_HIGH;
        }

        // Quy tắc chuẩn: Nếu Seller cài bước giá cao hơn mốc tối thiểu sàn -> Lấy theo bước giá Seller cài
        if (configuredBidStep != null && configuredBidStep.compareTo(dynamicStep) > 0) {
            return configuredBidStep;
        }
        return dynamicStep;
    }

    public BigDecimal calculateBidStep(BigDecimal currentPrice) {
        return calculateBidStep(currentPrice, null);
    }

    // 2. Tính toán mức giá đặt tối thiểu hợp lệ cho lượt tiếp theo (minValidBid = currentPrice + calculatedBidStep)
    public BigDecimal calculateMinValidBid(BigDecimal currentPrice, BigDecimal configuredBidStep) {
        BigDecimal step = calculateBidStep(currentPrice, configuredBidStep);
        return (currentPrice != null) ? currentPrice.add(step) : step;
    }

    public BigDecimal calculateMinValidBid(BigDecimal currentPrice) {
        return calculateMinValidBid(currentPrice, null);
    }
}
