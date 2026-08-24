package com.duong.auction.system.aspect;

import java.lang.annotation.*;

/**
 * Custom Annotation khai báo luật giới hạn tốc độ bấm (Rate Limiting).
 * Mặc định: Cho phép tối đa 5 lần bấm trong vòng 10 giây cho mỗi UserID.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RateLimit {
    int maxRequests() default 5;
    int timeWindowSeconds() default 10;
}
