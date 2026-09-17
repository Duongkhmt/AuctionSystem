package com.duong.auction.system.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CategoryRequestDTO {

    @NotBlank(message = "Tên danh mục không được để trống")
    @Size(max = 100, message = "Tên danh mục tối đa 100 ký tự")
    private String name;

    private Long parentId; // ID danh mục cha (Null nếu là danh mục gốc)

    private Boolean active = true; // Trạng thái ẩn/hiện danh mục (Default = true)
}
