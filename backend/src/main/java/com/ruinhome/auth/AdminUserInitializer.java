package com.ruinhome.auth;

import com.ruinhome.user.Role;
import com.ruinhome.user.UserAccount;
import com.ruinhome.user.UserAccountRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class AdminUserInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminUserInitializer.class);

    private final UserAccountRepository userAccountRepository;
    private final PasswordEncoder passwordEncoder;
    private final String seedAdminPassword;

    public AdminUserInitializer(UserAccountRepository userAccountRepository,
                                PasswordEncoder passwordEncoder,
                                @Value("${SEED_ADMIN_PASSWORD:}") String seedAdminPassword) {
        this.userAccountRepository = userAccountRepository;
        this.passwordEncoder = passwordEncoder;
        this.seedAdminPassword = seedAdminPassword;
    }

    @Override
    public void run(String... args) {
        if (userAccountRepository.existsByUsername("admin")) {
            return;
        }
        if (seedAdminPassword == null || seedAdminPassword.isBlank()) {
            log.info("Bo qua tao tai khoan admin mac dinh: chua dat bien SEED_ADMIN_PASSWORD");
            return;
        }
        UserAccount admin = new UserAccount();
        admin.setUsername("admin");
        admin.setPasswordHash(passwordEncoder.encode(seedAdminPassword));
        admin.setRole(Role.ADMIN);
        admin.setEnabled(true);
        admin.setAreaAdmin("admin");
        admin.setRoot(true);
        userAccountRepository.save(admin);
        log.info("Da tao tai khoan admin goc (mat khau lay tu SEED_ADMIN_PASSWORD)");
    }
}
