package com.duong.auction.system.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;


@Component
@ConfigurationProperties(prefix = "app.cloudinary")
@Getter @Setter
public class CloudinaryProperties {
    private String folder = "auction-products";
    private String resourceType = "image";
    private List<String> allowedFormats = List.of("jpg", "jpeg", "png", "webp");
    private long maxFileSizeMb = 5;
    private int maxImageCount = 20;
}

