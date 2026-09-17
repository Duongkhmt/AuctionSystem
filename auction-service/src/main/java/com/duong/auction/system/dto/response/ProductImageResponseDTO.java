package com.duong.auction.system.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ProductImageResponseDTO implements Serializable {
    private static final long serialVersionUID = 1L;
    private Long id;
    private String imageUrl;
    private int displayOrder;
}
