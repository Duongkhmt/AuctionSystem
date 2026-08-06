package DuAnTrainning.AuctionSystem.controller;

import DuAnTrainning.AuctionSystem.dto.response.CategoryResponseDTO;
import DuAnTrainning.AuctionSystem.service.CategoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/v1/categories")
@RequiredArgsConstructor
public class CategoryController {

    private final CategoryService categoryService;

    @GetMapping
    public ResponseEntity<List<CategoryResponseDTO>> getCategories() {
        return ResponseEntity.ok(categoryService.getAllCategories());
    }
}
