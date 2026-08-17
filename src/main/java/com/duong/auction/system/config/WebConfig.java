package com.duong.auction.system.config;

import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.web.servlet.LocaleResolver;
import org.springframework.web.servlet.i18n.AcceptHeaderLocaleResolver;

import java.util.List;
import java.util.Locale;

@Configuration
public class WebConfig {

    /**
     * 1. Cấu hình LocaleResolver để tự động đọc HTTP Header Accept-Language từ Client.
     * Ví dụ: Accept-Language: en -> Locale.ENGLISH
     *        Accept-Language: vi -> Locale("vi")
     */
    @Bean
    public LocaleResolver localeResolver() {
        //Class có sẵn của Spring MVC giúp tự động phân tích (parse) Header này mỗi khi có Request đi vào ứng dụng
        AcceptHeaderLocaleResolver localeResolver = new AcceptHeaderLocaleResolver();

        // Thiết lập ngôn ngữ mặc định là Tiếng Việt nếu Client không truyền Header
        localeResolver.setDefaultLocale(new Locale("vi"));

        // Khai báo danh sách các Locale mà hệ thống hỗ trợ
        localeResolver.setSupportedLocales(List.of(
                new Locale("vi"),
                Locale.ENGLISH
        ));

        return localeResolver;
    }

    /**
     * 2. Cấu hình MessageSource để Spring nạp các file chứa thông điệp ngôn ngữ (messages*.properties)
     */
    @Bean
    public MessageSource messageSource() {
        ResourceBundleMessageSource messageSource = new ResourceBundleMessageSource();

        // Đường dẫn file gốc: Spring sẽ tìm các file messages.properties, messages_vi.properties, messages_en.properties trong src/main/resources/
        messageSource.setBasename("messages");

        // Ép kiểu mã hóa UTF-8 để không bị lỗi phông chữ Tiếng Việt có dấu
        messageSource.setDefaultEncoding("UTF-8");

        // Nếu không tìm thấy key trong file properties, trả lại chính key đó thay vì văng ngoại lệ NoSuchMessageException
        messageSource.setUseCodeAsDefaultMessage(true);

        return messageSource;
    }
}
