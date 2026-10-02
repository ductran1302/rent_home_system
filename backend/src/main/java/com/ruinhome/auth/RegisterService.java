package com.ruinhome.auth;

import com.ruinhome.user.Role;
import com.ruinhome.user.UserAccount;
import com.ruinhome.user.UserAccountRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class RegisterService {

    private static final String USERNAME_REGEX = "[A-Za-z0-9._-]{3,100}";

    private final UserAccountRepository userAccountRepository;
    private final PasswordEncoder passwordEncoder;
    private final boolean allowSelfRegister;

    public RegisterService(UserAccountRepository userAccountRepository,
                           PasswordEncoder passwordEncoder,
                           @Value("${ruinhome.auth.allow-self-register:false}") boolean allowSelfRegister) {
        this.userAccountRepository = userAccountRepository;
        this.passwordEncoder = passwordEncoder;
        this.allowSelfRegister = allowSelfRegister;
    }

    @Transactional
    public void register(String rawUsername, String password) {
        if (!allowSelfRegister) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Đăng ký tài khoản đang bị tắt, vui lòng liên hệ quản trị viên");
        }
        String username = rawUsername == null ? "" : rawUsername.trim();
        if (!username.matches(USERNAME_REGEX)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Tên đăng nhập chỉ gồm chữ, số, dấu chấm, gạch nối, từ 3 đến 100 ký tự");
        }
        if (password == null || password.isBlank() || password.length() < 8 || password.length() > 32) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Mật khẩu phải từ 8 đến 32 ký tự");
        }
        if (userAccountRepository.existsByUsername(username)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Tên đăng nhập đã tồn tại");
        }
        UserAccount account = new UserAccount();
        account.setUsername(username);
        account.setPasswordHash(passwordEncoder.encode(password));
        account.setRole(Role.ADMIN);
        account.setEnabled(true);
        account.setAreaAdmin(username);
        account.setRoot(false);
        userAccountRepository.save(account);
    }
}
