package DuAnTrainning.AuctionSystem.service.helper;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("Deep Dive Unit Test Cho Spying (@Spy) với BidStepCalculatorHelper")
class BidStepCalculatorHelperTest {

    // 🌟 SỬ DỤNG @Spy NGUYÊN BẢN: Bọc một instance THẬT của BidStepCalculatorHelper
    // Mặc định 100% mã nguồn THẬT của class sẽ được thực thi ngoại trừ các hàm bị override bởi doReturn()
    @Spy
    private BidStepCalculatorHelper bidStepCalculatorHelper;

    @Nested
    @DisplayName("Thực thi Logic Thật của Class (@Spy Real Execution)")
    class RealExecutionTests {

        @Test
        @DisplayName("Mức giá < 1M - Tự động dùng bước giá tối thiểu STEP_LOW = 10.000đ")
        void calculateBidStep_LowRange_ReturnsLowStep() {
            // Given (Chạy code thật, không stubbing)
            BigDecimal currentPrice = BigDecimal.valueOf(500000); // 500k

            // When
            BigDecimal step = bidStepCalculatorHelper.calculateBidStep(currentPrice);

            // Then
            assertThat(step).isEqualTo(new BigDecimal("10000"));
        }

        @Test
        @DisplayName("Mức giá từ 1M đến 10M - Tự động dùng bước giá STEP_MEDIUM = 100.000đ")
        void calculateBidStep_MediumRange_ReturnsMediumStep() {
            // Given
            BigDecimal currentPrice = BigDecimal.valueOf(5000000); // 5M

            // When
            BigDecimal step = bidStepCalculatorHelper.calculateBidStep(currentPrice);

            // Then
            assertThat(step).isEqualTo(new BigDecimal("100000"));
        }

        @Test
        @DisplayName("Mức giá > 10M - Tự động dùng bước giá STEP_HIGH = 500.000đ")
        void calculateBidStep_HighRange_ReturnsHighStep() {
            // Given
            BigDecimal currentPrice = BigDecimal.valueOf(20000000); // 20M

            // When
            BigDecimal step = bidStepCalculatorHelper.calculateBidStep(currentPrice);

            // Then
            assertThat(step).isEqualTo(new BigDecimal("500000"));
        }

        @Test
        @DisplayName("Seller cấu hình bước giá cao hơn mốc sàn - Ưu tiên lấy bước giá của Seller")
        void calculateBidStep_SellerHigherStep_ReturnsSellerStep() {
            // Given
            BigDecimal currentPrice = BigDecimal.valueOf(500000); // Sàn quy định 10k
            BigDecimal sellerStep = BigDecimal.valueOf(50000);    // Seller cài 50k

            // When
            BigDecimal step = bidStepCalculatorHelper.calculateBidStep(currentPrice, sellerStep);

            // Then
            assertThat(step).isEqualTo(BigDecimal.valueOf(50000));
        }
    }

    @Nested
    @DisplayName("Thực hành Spying (Partial Mocking - Ghi đè 1 phần hành vi)")
    class PartialMockingTests {

        @Test
        @DisplayName("Kỹ thuật @Spy: Ghi đè hàm phụ calculateBidStep, giữ nguyên logic thật của calculateMinValidBid")
        void calculateMinValidBid_SpiedSubMethod_ExecutesRealParentLogic() {
            // Given
            BigDecimal currentPrice = BigDecimal.valueOf(1000000);
            BigDecimal mockStep = BigDecimal.valueOf(250000); // Giả lập bước giá bị ghi đè là 250k

            // ⚠️ CÚ PHÁP CHUẨN CHO @SPY: Phải dùng doReturn().when(spy)... thay vì when().thenReturn()
            // để tránh việc thực thi code thật trong lúc Stubbing!
            doReturn(mockStep).when(bidStepCalculatorHelper).calculateBidStep(eq(currentPrice), any());

            // When: Gọi hàm calculateMinValidBid (Hàm này vẫn chạy CODE THẬT: currentPrice + step)
            BigDecimal minValidBid = bidStepCalculatorHelper.calculateMinValidBid(currentPrice);

            // Then: 1.000.000 + 250.000 = 1.250.000
            assertThat(minValidBid).isEqualTo(BigDecimal.valueOf(1250000));

            // Verify: Xác minh phương thức calculateBidStep nội bộ đã được gọi
            verify(bidStepCalculatorHelper, times(1)).calculateBidStep(eq(currentPrice), any());
        }
    }
}
