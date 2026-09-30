package com.ruinhome.auth;

import com.ruinhome.user.UserAccount;
import com.ruinhome.user.UserAccountRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class CurrentUserService {

    private final UserAccountRepository userAccountRepository;

    public CurrentUserService(UserAccountRepository userAccountRepository) {
        this.userAccountRepository = userAccountRepository;
    }

    public String username() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Chưa đăng nhập");
        }
        return authentication.getName();
    }

    public UserAccount account() {
        return userAccountRepository.findByUsername(username())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Phiên không hợp lệ"));
    }

    public boolean isAdmin() {
        return account().getRole() == com.ruinhome.user.Role.ADMIN;
    }

    public Long personId() {
        Long personId = personIdOrNull();
        if (personId == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Tài khoản chưa liên kết hồ sơ cá nhân, vui lòng liên hệ quản trị viên");
        }
        return personId;
    }

    public Long personIdOrNull() {
        return account().getPerson() != null ? account().getPerson().getId() : null;
    }
}
