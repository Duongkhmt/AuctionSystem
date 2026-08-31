package com.duong.auction.system.repository;

import com.duong.auction.system.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email);
    // 🟢 Kiểm tra tồn tại Email (Dùng cho Đăng ký)
    boolean existsByEmail(String email);
    // 🟢 Kiểm tra tồn tại Username (Dùng cho Đăng ký)
    boolean existsByUsername(String username);
}