package com.duong.auction.system.security;

import com.duong.auction.system.entity.User;
import com.duong.auction.system.exception.ErrorCode;
import com.duong.auction.system.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;
    /**
     * 🟢 Hàm bắt buộc của Spring Security: Tìm kiếm người dùng trong CSDL theo EMAIL.
     * Tái sử dụng thông báo lỗi chuẩn từ ErrorCode.USER_NOT_FOUND ("Người dùng không tồn tại").
     *
     * @param email Địa chỉ Email gửi lên từ Form Đăng nhập hoặc trích xuất từ JWT Token.
     * @return Đối tượng UserCustomDetails chứa thông tin User.
     * @throws UsernameNotFoundException nếu không tìm thấy Email trong CSDL.
     */
    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        // 🟢 Gọi UserRepository tìm User theo Email, nếu không thấy thì ném ngoại lệ UsernameNotFoundException
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException(ErrorCode.USER_NOT_FOUND.getMessage()));
        // 🟢 Bọc Entity User vừa tìm thấy trong CSDL vào lớp cầu nối UserCustomDetails
        return new UserCustomDetails(user);
    }
}
