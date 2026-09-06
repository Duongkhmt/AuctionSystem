package com.duong.auction.system.security;

import com.duong.auction.system.entity.User;
import com.duong.auction.system.enums.UserStatus;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

/**
 * Class cầu nối thích ứng giữa Entity User trong Database với UserDetails của Spring Security.
 */
@Getter
@AllArgsConstructor
public class UserCustomDetails implements UserDetails {

    private final transient User user; // Chứa Entity User gốc từ CSDL

    // 🟢 1. Chuyển đổi UserRole (USER / ADMIN) sang GrantedAuthority ("ROLE_USER", "ROLE_ADMIN")
    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name()));
    }

    // 🟢 2. Trả về Mật khẩu đã băm (Password Hash) từ CSDL
    @Override
    public String getPassword() {
        return user.getPasswordHash();
    }

    // 🟢 3. Trả về Email/Username của người dùng
    @Override
    public String getUsername() {
        return user.getEmail(); // Hoặc user.getUsername() tùy theo định danh đăng nhập
    }

    // 🟢 4. Tài khoản chưa hết hạn? (Trả về true)
    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    // 🟢 5. Tài khoản chưa bị khóa? (Trả về true nếu UserStatus khác BANNED)
    @Override
    public boolean isAccountNonLocked() {
        return user.getStatus() != UserStatus.BANNED;
    }

    // 🟢 6. Mật khẩu chưa hết hạn? (Trả về true)
    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    // 🟢 7. Tài khoản đang hoạt động? (Trả về true nếu UserStatus là ACTIVE)
    @Override
    public boolean isEnabled() {
        return user.getStatus() == UserStatus.ACTIVE;
    }
}
