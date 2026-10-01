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

    public boolean isRoot() {
        return account().isRoot();
    }

    public String areaOrNull() {
        var account = account();
        if (account.getRole() == com.ruinhome.user.Role.ADMIN) {
            return account.isRoot() ? null : areaOf(account);
        }
        if (account.getRole() == com.ruinhome.user.Role.MANAGER) {
            return areaOf(account);
        }
        return null;
    }

    public String areaForWrite() {
        return areaOf(account());
    }

    public void checkArea(String areaAdmin) {
        var account = account();
        if (account.getRole() != com.ruinhome.user.Role.ADMIN || account.isRoot()) {
            return;
        }
        if (!areaOf(account).equals(areaAdmin)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Bạn không có quyền với dữ liệu này");
        }
    }

    private String areaOf(UserAccount account) {
        return account.getAreaAdmin() != null ? account.getAreaAdmin() : account.getUsername();
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
