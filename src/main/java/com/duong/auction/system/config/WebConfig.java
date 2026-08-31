package com.duong.auction.system.config;


import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.SerializationFeature;
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
     * 🟢 Cấu hình ObjectMapper Bean tường minh dùng chung cho toàn bộ ứng dụng.
     * Ép định dạng JSON trả về chuẩn SNAKE_CASE (user_id, created_at) và tự động đăng ký các module thời gian.
     */
    @Bean
    public ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();

        // 🟢 Ép kiểu chuyển đổi tên thuộc tính Java (camelCase) sang JSON (snake_case)
        mapper.setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);

        // 🟢 Tự động phát hiện và đăng ký tất cả các module xử lý thời gian Java 8/21 (LocalDateTime, Instant)
        mapper.findAndRegisterModules();

        // 🟢 Tắt định dạng ngày tháng dạng số timestamp (đổi sang chuỗi ISO-8601 chuẩn)
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        return mapper;
    }


    /**
     * 🟢 Cấu hình LocaleResolver tự động phân tích HTTP Header Accept-Language từ Client gửi lên.
     * Mặc định là Tiếng Việt ("vi"), hỗ trợ thêm Tiếng Anh ("en").
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
