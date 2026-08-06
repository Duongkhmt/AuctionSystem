package DuAnTrainning.AuctionSystem.service;

import DuAnTrainning.AuctionSystem.dto.response.CategoryResponseDTO;
import DuAnTrainning.AuctionSystem.repository.CategoryRepository;
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
                .filter(category -> category.isActive())
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
