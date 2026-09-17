package com.duong.auction.system.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;

@Getter
@Setter
public class ProductRejectRequestDTO implements Serializable {
    private static final long serialVersionUID = 1L;
    @NotBlank(message = "Vui lòng cung cấp lý do từ chối bài đăng")
    @Size(max = 255, message = "Lý do từ chối không được vượt quá 255 ký tự")
    private String rejectionReason;
}
