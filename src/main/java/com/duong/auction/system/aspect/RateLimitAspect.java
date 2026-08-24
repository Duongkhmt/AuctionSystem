package com.duong.auction.system.aspect;

import com.duong.auction.system.exception.ApplicationException;
import com.duong.auction.system.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Aspect tự động chặn đứng các request quá tải bằng Redis Lua Script nguyên tử.
 * 
 * 🚀 GIẢI QUYẾT VẤN ĐỀ 1:
 * Đánh dấu @Order(Ordered.HIGHEST_PRECEDENCE) để ép Spring AOP LUÔN LUÔN chạy Aspect này
 * ở vòng bảo vệ NGOẠI CÙNG TRƯỚC @Transactional và @CacheEvict!
 */
@Aspect
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
@RequiredArgsConstructor
public class RateLimitAspect {

    //dùng để giao tiếp với redis bằng string
    private final StringRedisTemplate redisTemplate;

    // Singleton Lua Script: Gộp INCR và EXPIRE thành 1 bước nguyên tử duy nhất trên Redis RAM
    private static final RedisScript<Long> RATE_LIMIT_LUA_SCRIPT;

    static {
        String scriptText =
                "local current = redis.call('INCR', KEYS[1]) " +
                "if tonumber(current) == 1 then " +
                "   redis.call('EXPIRE', KEYS[1], ARGV[1]) " +
                "end " +
                "return current";

        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setScriptText(scriptText);
        script.setResultType(Long.class);
        RATE_LIMIT_LUA_SCRIPT = script;
    }

    //TRước khi chạy 1 method có "ratelimit thì chạy file check này trước
    @Before("@annotation(rateLimit) && args(bidderId,..)")
    public void checkRateLimit(JoinPoint joinPoint, RateLimit rateLimit, Long bidderId) {
        if (bidderId == null) {
            return;
        }

        String methodName = joinPoint.getSignature().getName();
        String redisKey = "rate_limit:" + methodName + ":user:" + bidderId;

        Long currentCount = redisTemplate.execute(
                RATE_LIMIT_LUA_SCRIPT,
                List.of(redisKey),
                String.valueOf(rateLimit.timeWindowSeconds())
        );

        if (currentCount != null && currentCount > rateLimit.maxRequests()) {
            throw new ApplicationException(ErrorCode.TOO_MANY_REQUESTS);
        }
    }
}
