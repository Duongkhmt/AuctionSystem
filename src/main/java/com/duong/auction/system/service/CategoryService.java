package com.duong.auction.system.service;

import com.duong.auction.system.dto.request.CategoryRequestDTO;
import com.duong.auction.system.dto.response.CategoryResponseDTO;
import com.duong.auction.system.entity.Category;
import com.duong.auction.system.exception.ApplicationException;
import com.duong.auction.system.exception.ErrorCode;
import com.duong.auction.system.mapper.CategoryMapper;
import com.duong.auction.system.repository.CategoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Service xử lý toàn bộ logic nghiệp vụ liên quan đến Danh Mục Sản Phẩm.
 */
@Service
@RequiredArgsConstructor
public class CategoryService {

    private final CategoryRepository categoryRepository;
    private final CategoryMapper categoryMapper;

    /**
     * Lấy danh sách danh mục dành cho Khách hàng & Người bán công khai.
     * Chỉ lọc và lấy các danh mục đang ở trạng thái hoạt động (active = true).
     */
    @Transactional(readOnly = true)
    public List<CategoryResponseDTO> getAllCategories() {
        List<Category> activeCategories = categoryRepository.findAll().stream()
                .filter(Category::isActive)
                .toList();
        return categoryMapper.toResponseDTOList(activeCategories);
    }

    /**
     * Lấy danh sách tất cả các danh mục dành riêng cho Admin quản lý.
     * Bao gồm cả các danh mục đang bị ẩn hoặc tạm ngưng hoạt động (active = false).
     */
    @Transactional(readOnly = true)
    public List<CategoryResponseDTO> getAllCategoriesForAdmin() {
        List<Category> allCategories = categoryRepository.findAll();
        return categoryMapper.toResponseDTOList(allCategories);
    }

    /**
     * Admin tạo mới một danh mục sản phẩm.
     * Nếu có truyền parentId, kiểm tra sự tồn tại của danh mục cha trong Database.
     */
    @Transactional
    public CategoryResponseDTO createCategory(CategoryRequestDTO requestDTO) {
        // Kiểm tra danh mục cha nếu có khai báo
        if (requestDTO.getParentId() != null) {
            categoryRepository.findById(requestDTO.getParentId())
                    .orElseThrow(() -> new ApplicationException(ErrorCode.CATEGORY_NOT_FOUND));
        }

        // Chuyển DTO thành Entity và cắt khoảng trắng tên danh mục
        Category category = categoryMapper.toEntity(requestDTO);
        category.setName(requestDTO.getName().trim());

        Category savedCategory = categoryRepository.save(category);
        return categoryMapper.toResponseDTO(savedCategory);
    }

    /**
     * Admin cập nhật thông tin một danh mục sản phẩm hiện có.
     * Kiểm tra ID danh mục tồn tại và validate parentId hợp lệ.
     */
    @Transactional
    public CategoryResponseDTO updateCategory(Long id, CategoryRequestDTO requestDTO) {
        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new ApplicationException(ErrorCode.CATEGORY_NOT_FOUND));

        // Kiểm tra danh mục cha mới nếu có sự thay đổi
        if (requestDTO.getParentId() != null && !requestDTO.getParentId().equals(category.getParentId())) {
            categoryRepository.findById(requestDTO.getParentId())
                    .orElseThrow(() -> new ApplicationException(ErrorCode.CATEGORY_NOT_FOUND));
        }

        // Cập nhật thông tin bằng MapStruct và lưu DB
        categoryMapper.updateCategoryFromDTO(requestDTO, category);
        category.setName(requestDTO.getName().trim());

        Category updatedCategory = categoryRepository.save(category);
        return categoryMapper.toResponseDTO(updatedCategory);
    }

    /**
     * Admin ẩn/xóa danh mục sản phẩm (Áp dụng Soft Delete).
     * Chuyển trạng thái active = false để tránh mất mát dữ liệu sản phẩm đã liên kết trước đó.
     */
    @Transactional
    public void deleteCategory(Long id) {
        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new ApplicationException(ErrorCode.CATEGORY_NOT_FOUND));

        category.setActive(false);
        categoryRepository.save(category);
    }
}
