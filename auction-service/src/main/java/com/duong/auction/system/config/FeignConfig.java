package com.duong.auction.system.config;

import feign.RequestInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class FeignConfig {

    @Value("${app.internal.service-key}")
    private String internalServiceKey;

    @Bean
    public RequestInterceptor requestInterceptor() {
        return template -> template.header("X-Internal-Service-Key", internalServiceKey);
    }
}
