package com.duong.auction.system.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.SignatureException;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.time.Instant;
import java.util.Date;

/**
 * Component chuyên trách Sinh Token 10 phút, Giải mã Email và Kiểm tra tính hợp lệ của JWT.
 * Đã được tối ưu High-Performance (Cache Key tại @PostConstruct) và Phân cấp Log chuẩn Production.
 */
@Component
@Slf4j
public class JwtTokenProvider {

    @Value("${app.jwt.secret}")
    private String jwtSecret;

    @Value("${app.jwt.expiration-ms}")
    private long jwtExpirationMs;

    // 🟢 1. CACHE KEY TRONG RAM (Tránh re-parse byte array tốn CPU trên mỗi HTTP Request)
    private Key cachedSecretKey;


    @PostConstruct
    public void init() {
        this.cachedSecretKey = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
    }

    // 🟢 2. HÀM TẠO JWT TOKEN CHỨA EMAIL (Thời hạn 10 phút)
    public String generateToken(Authentication authentication) {
        String email = authentication.getName(); // Email định danh của người dùng
        // 🟢 Dùng java.time.Instant hiện đại của Java 21 -> Chuyển sang Date cho JJWT
        Instant now = Instant.now();
        Instant expiryDate = now.plusMillis(jwtExpirationMs);

        return Jwts.builder()
                .setSubject(email)
                .setIssuedAt(Date.from(now))
                .setExpiration(Date.from(expiryDate))
                .signWith(cachedSecretKey, SignatureAlgorithm.HS256)
                .compact();
    }

    // 🟢 3. HÀM TRÍCH XUẤT EMAIL TỪ CHUỖI JWT TOKEN (Tối ưu dùng cachedSecretKey)
    public String getUsernameFromToken(String token) {
        Claims claims = Jwts.parserBuilder()
                .setSigningKey(cachedSecretKey)
                .build()
                .parseClaimsJws(token)
                .getBody();

        return claims.getSubject();
    }

    // 🟢 4. HÀM KIỂM TRA TÍNH HỢP LỆ VÀ PHÂN CẤP LOG LEVEL CHUẨN PRODUCTION
    public boolean validateToken(String token) {
        try {
            Jwts.parserBuilder().setSigningKey(cachedSecretKey).build().parseClaimsJws(token);
            return true;
        } catch (SignatureException ex) {
            // 🚨 Cảnh báo bảo mật quan trọng: Dấu hiệu kẻ xấu cố tình giả mạo Token
            log.error("🚨 CHỮ KÝ JWT KHÔNG HỢP LỆ: Token có dấu hiệu bị giả mạo!");
        } catch (MalformedJwtException ex) {
            log.warn("⚠️ Chuỗi JWT không đúng cấu trúc định dạng!");
        } catch (ExpiredJwtException ex) {
            // ℹ️ Hết hạn 10 phút là sự kiện tự nhiên: Chuyển sang log.info để KHÔNG LÀM RÁC LOG SERVER
            log.info("ℹ️ JWT Token đã hết hạn tự nhiên (Quá thời hạn 10 phút).");
        } catch (UnsupportedJwtException ex) {
            log.warn("⚠️ JWT Token không được hỗ trợ!");
        } catch (IllegalArgumentException ex) {
            log.warn("⚠️ Chuỗi JWT rỗng hoặc chứa khoảng trắng!");
        }
        return false;
    }
}
