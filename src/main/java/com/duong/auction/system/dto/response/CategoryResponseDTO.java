package com.duong.auction.system.dto.response;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;

@Getter
@Setter
@Builder
public class CategoryResponseDTO implements Serializable {
    private static final long serialVersionUID = 1L;
    private Long id;
    private Long parentId;
    private String name;
    private boolean active;
    private boolean requiresVerification;
    private boolean requiresDeposit;
}
