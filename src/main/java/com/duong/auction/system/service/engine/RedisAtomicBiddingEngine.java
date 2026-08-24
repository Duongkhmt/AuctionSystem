package com.duong.auction.system.service.engine;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

/**
 * Động cơ so kè giá nguyên tử siêu tốc (0.02ms) trên Redis RAM bằng Lua Script
 */
@Component
@RequiredArgsConstructor
public class RedisAtomicBiddingEngine {

    private final StringRedisTemplate redisTemplate;
    private static final RedisScript<Long> BID_LUA_SCRIPT;

    static {
        // Lua Script nguyên tử (0.02ms): So sánh newBidAmount >= currentPrice + stepPrice
        String scriptText =
                "local stateKey = KEYS[1] " +
                "local newBidAmount = tonumber(ARGV[1]) " +
                "local bidderId = ARGV[2] " +
                "local stepPrice = tonumber(ARGV[3]) " +
                
                "local currentPrice = tonumber(redis.call('HGET', stateKey, 'currentPrice') or '0') " +
                "local minPrice = currentPrice + stepPrice " +
                
                "if newBidAmount < minPrice then " +
                "   return 0 " + // 0 = Thua giá (Outbid)
                "end " +
                
                // Cập nhật giá mới và người dẫn đầu mới trên RAM ngay lập tức
                "redis.call('HSET', stateKey, 'currentPrice', tostring(newBidAmount)) " +
                "redis.call('HSET', stateKey, 'highestBidderId', bidderId) " +
                "return 1"; // 1 = Thắng giá (Success)

        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setScriptText(scriptText);
        script.setResultType(Long.class);
        BID_LUA_SCRIPT = script;
    }

    /**
     * Thực thi so kè giá nguyên tử trên Redis RAM
     */
    public boolean processBidAtomic(Long auctionId, Long bidderId, BigDecimal bidAmount, BigDecimal stepPrice) {
        String redisStateKey = "auction:state:" + auctionId;

        Long result = redisTemplate.execute(
                BID_LUA_SCRIPT,
                List.of(redisStateKey),
                bidAmount.toString(),
                bidderId.toString(),
                stepPrice.toString()
        );

        return Long.valueOf(1L).equals(result);
    }
}
