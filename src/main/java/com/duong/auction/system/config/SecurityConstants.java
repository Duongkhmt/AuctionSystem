package com.duong.auction.system.config;

public final class SecurityConstants {
    private SecurityConstants() {
        // Private constructor ngăn không cho khởi tạo Instance
    }
    // 🟢 HẰNG SỐ TIỀN TỐ VÀ HEADER TOKEN
    public static final String TOKEN_PREFIX = "Bearer ";
    public static final String HEADER_AUTHORIZATION = "Authorization";
    // 🟢 TIỀN TỐ REDIS KEYS BẢO MẬT
    public static final String REFRESH_TOKEN_KEY_PREFIX = "refresh_token:";
    public static final String REFRESH_TOKEN_USER_KEY_PREFIX = "refresh_token_user:";
    public static final String BLACKLIST_TOKEN_KEY_PREFIX = "blacklist_token:";
}
