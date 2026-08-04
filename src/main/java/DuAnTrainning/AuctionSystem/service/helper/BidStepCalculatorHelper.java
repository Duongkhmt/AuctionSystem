package DuAnTrainning.AuctionSystem.service.helper;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
public class BidStepCalculatorHelper {

    private static final BigDecimal ONE_MILLION = new BigDecimal("1000000");
    private static final BigDecimal TEN_MILLIONS = new BigDecimal("10000000");
    private static final BigDecimal STEP_LOW = new BigDecimal("10000");      // 10.000đ cho giá < 1M
    private static final BigDecimal STEP_MEDIUM = new BigDecimal("100000");   // 100.000đ cho giá 1M - 10M
    private static final BigDecimal STEP_HIGH = new BigDecimal("500000");     // 500.000đ cho giá > 10M
    // 1. Tính bước giá tương ứng với mức giá hiện tại
    public BigDecimal calculateBidStep(BigDecimal currentPrice) {
        if (currentPrice == null || currentPrice.compareTo(ONE_MILLION) < 0) {
            return STEP_LOW;
        } else if (currentPrice.compareTo(TEN_MILLIONS) <= 0) {
            return STEP_MEDIUM;
        } else {
            return STEP_HIGH;
        }
    }
    // 2. Tính giá đặt tối thiểu hợp lệ lượt tiếp theo (minValidBid = currentPrice + calculatedBidStep)
    public BigDecimal calculateMinValidBid(BigDecimal currentPrice) {
        BigDecimal step = calculateBidStep(currentPrice);
        return (currentPrice != null) ? currentPrice.add(step) : step;
    }
}
