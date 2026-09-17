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
 */
@Aspect
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
@RequiredArgsConstructor
public class RateLimitAspect {

    private final StringRedisTemplate redisTemplate;

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

    @Before("@annotation(rateLimit) && args(targetId,..)")
    public void checkRateLimit(JoinPoint joinPoint, RateLimit rateLimit, Object targetId) {
        if (targetId == null) {
            return;
        }

        String methodName = joinPoint.getSignature().getName();
        String redisKey = "rate_limit:" + methodName + ":" + targetId;

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
