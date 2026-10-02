package com.ruinhome.auth;

import com.ruinhome.user.ManagerPeriod;
import com.ruinhome.user.Role;
import com.ruinhome.user.UserAccount;
import com.ruinhome.user.UserAccountRepository;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final UserAccountRepository userAccountRepository;
    private final RegisterService registerService;

    public AuthController(AuthenticationManager authenticationManager, JwtService jwtService,
                          UserAccountRepository userAccountRepository, RegisterService registerService) {
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
        this.userAccountRepository = userAccountRepository;
        this.registerService = registerService;
    }

    public record LoginRequest(@NotBlank String username, @NotBlank String password) {
    }

    public record RegisterRequest(String username, String password) {
    }

    public record LoginResponse(String token, String username, String role) {
    }

    public record MeResponse(String username, Role role, Long personId, String fullName, boolean root) {
    }

    @PostMapping("/login")
    public LoginResponse login(@RequestBody LoginRequest request) {
        try {
            authenticationManager.authenticate(
                    new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                            request.username(), request.password()));
        } catch (BadCredentialsException | DisabledException e) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Sai tên đăng nhập hoặc mật khẩu");
        }
        UserAccount account = userAccountRepository.findByUsername(request.username())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                        "Sai tên đăng nhập hoặc mật khẩu"));
        if (!ManagerPeriod.isActiveToday(account)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, ManagerPeriod.inactiveMessage(account));
        }
        return new LoginResponse(jwtService.generate(account.getUsername(), account.getRole().name()),
                account.getUsername(), account.getRole().name());
    }

    @PostMapping("/register")
    public ResponseEntity<Void> register(@RequestBody RegisterRequest request) {
        registerService.register(request.username(), request.password());
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @GetMapping("/me")
    public MeResponse me() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Chưa đăng nhập");
        }
        String username = authentication.getName();
        UserAccount account = userAccountRepository.findByUsername(username)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Phiên không hợp lệ"));
        String fullName = account.getPerson() != null ? account.getPerson().getFullName() : null;
        Long personId = account.getPerson() != null ? account.getPerson().getId() : null;
        return new MeResponse(account.getUsername(), account.getRole(), personId, fullName,
                account.isRoot());
    }
}
