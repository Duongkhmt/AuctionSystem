package com.duong.auction.system.mapper;

import com.duong.auction.system.dto.request.CategoryRequestDTO;
import com.duong.auction.system.dto.response.CategoryResponseDTO;
import com.duong.auction.system.entity.Category;
import org.mapstruct.Mapper;
import org.mapstruct.MappingTarget;

import java.util.List;

/**
 * Interface MapStruct ánh xạ dữ liệu giữa Category Entity và các DTO.
 */
@Mapper(componentModel = "spring")
public interface CategoryMapper {

    /**
     * Ánh xạ từ Category Entity sang CategoryResponseDTO.
     */
    CategoryResponseDTO toResponseDTO(Category category);

    /**
     * Ánh xạ danh sách Category Entity sang danh sách CategoryResponseDTO.
     */
    List<CategoryResponseDTO> toResponseDTOList(List<Category> categories);

    /**
     * Ánh xạ từ CategoryRequestDTO sang Category Entity mới.
     */
    Category toEntity(CategoryRequestDTO requestDTO);

    /**
     * Cập nhật thông tin từ CategoryRequestDTO đè lên Category Entity hiện tại.
     */
    void updateCategoryFromDTO(CategoryRequestDTO requestDTO, @MappingTarget Category category);
}
