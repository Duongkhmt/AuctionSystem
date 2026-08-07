package DuAnTrainning.AuctionSystem.service;

import DuAnTrainning.AuctionSystem.dto.response.CategoryResponseDTO;
import DuAnTrainning.AuctionSystem.entity.Category;
import DuAnTrainning.AuctionSystem.repository.CategoryRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.times;

/**
 * Class Unit Test dành riêng cho CategoryService.
 * Kiểm thử tính năng lọc danh mục sản phẩm hiển thị trên sàn.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Unit Test Cho Class CategoryService")
class CategoryServiceTest {

    @Mock
    private CategoryRepository categoryRepository; // Giả lập dữ liệu bảng danh mục

    @InjectMocks
    private CategoryService categoryService; // Instance Service thật được tiêm @Mock

    @Test
    @DisplayName("Lấy danh sách danh mục - Chỉ lấy danh mục đang hoạt động (active = true)")
    void getAllCategories_Success_ShouldReturnActiveCategoriesOnly() {
        // 1. GIVEN: Giả lập DB có 2 danh mục (1 đang hoạt động active=true, 1 bị ẩn active=false)
        Category activeCategory = new Category();
        activeCategory.setId(1L);
        activeCategory.setName("Điện thoại");
        activeCategory.setActive(true);

        Category inactiveCategory = new Category();
        inactiveCategory.setId(2L);
        inactiveCategory.setName("Danh mục ẩn");
        inactiveCategory.setActive(false);

        given(categoryRepository.findAll()).willReturn(List.of(activeCategory, inactiveCategory));

        // 2. WHEN: Gọi phương thức lấy tất cả danh mục
        List<CategoryResponseDTO> result = categoryService.getAllCategories();

        // 3. THEN: Khẳng định danh sách trả về chỉ chứa 1 phần tử là danh mục active=true ("Điện thoại")
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getName()).isEqualTo("Điện thoại");
        then(categoryRepository).should(times(1)).findAll();
    }
}
