package com.duong.auction.system.entity;

import com.duong.auction.system.config.DateTimeConfig;
import com.duong.auction.system.enums.UserRole;
import com.duong.auction.system.enums.UserStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;

@Entity
@Table(name = "users")
@Getter @Setter
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 50)
    private String username;

    @Column(nullable = false, unique = true, length = 50)
    private String email;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(name = "unpaid_strike_count", nullable = false)
    private Integer unpaidStrikeCount = 0; // Đếm số gậy bùng đơn (Default = 0)

    @Column(name = "banned_until")
    private LocalDateTime bannedUntil; // Thời điểm hết hạn cấm đấu giá (Null = Bình thường)

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private UserRole role; // 👈 Set tường minh ở Service/Mapper, không gán mặc định ở đây!

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private UserStatus status; // 👈 Set tường minh ở Service/Mapper, không gán mặc định ở đây!

    @Column(name = "last_logout_at")
    private LocalDateTime lastLogoutAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now(DateTimeConfig.DEFAULT_ZONE);
}