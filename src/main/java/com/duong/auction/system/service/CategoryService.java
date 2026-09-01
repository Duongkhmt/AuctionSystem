package com.duong.auction.system.service;

import com.duong.auction.system.dto.response.CategoryResponseDTO;
import com.duong.auction.system.entity.Category;
import com.duong.auction.system.repository.CategoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CategoryService {

    private final CategoryRepository categoryRepository;

    public List<CategoryResponseDTO> getAllCategories() {
        return categoryRepository.findAll().stream()
                .filter(Category::isActive)
                .map(cat -> CategoryResponseDTO.builder()
                        .id(cat.getId())
                        .parentId(cat.getParentId())
                        .name(cat.getName())
                        .active(cat.isActive())
                        .requiresVerification(cat.isRequiresVerification())
                        .requiresDeposit(cat.isRequiresDeposit())
                        .build())
                .toList();
    }

}
