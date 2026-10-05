package com.ruinhome.user;

import com.ruinhome.auth.CurrentUserService;
import com.ruinhome.person.Person;
import com.ruinhome.person.PersonRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.List;
import java.util.Locale;

@Service
public class UserService {

    private final UserAccountRepository userAccountRepository;
    private final PersonRepository personRepository;
    private final PasswordEncoder passwordEncoder;
    private final CurrentUserService currentUserService;

    public UserService(UserAccountRepository userAccountRepository, PersonRepository personRepository,
                       PasswordEncoder passwordEncoder, CurrentUserService currentUserService) {
        this.userAccountRepository = userAccountRepository;
        this.personRepository = personRepository;
        this.passwordEncoder = passwordEncoder;
        this.currentUserService = currentUserService;
    }

    @Transactional(readOnly = true)
    public List<UserDtos.UserResponse> list() {
        String areaScope = currentUserService.areaOrNull();
        return userAccountRepository.findAllWithPerson().stream()
                .filter(account -> areaScope == null || areaScope.equals(account.getAreaAdmin()))
                .map(UserDtos::toResponse)
                .toList();
    }

    @Transactional
    public UserDtos.UserResponse create(UserDtos.UserCreateRequest request) {
        Role role = parseRole(request.role());
        if (role == Role.ADMIN) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Không thể tạo tài khoản quản trị viên mới");
        }
        String username = normalizeUsername(request.username());
        if (userAccountRepository.existsByUsername(username)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Tên đăng nhập đã tồn tại");
        }
        UserAccount account = new UserAccount();
        account.setUsername(username);
        account.setPasswordHash(passwordEncoder.encode(request.password()));
        account.setRole(role);
        account.setEnabled(request.enabled() == null || request.enabled());
        account.setAreaAdmin(currentUserService.areaForWrite());
        account.setBankAccount(normalizeBankAccount(request.bankAccount()));
        applyPersonAndPeriod(account, role, request.personId(),
                request.managerStartDate(), request.managerEndDate());
        return UserDtos.toResponse(userAccountRepository.save(account));
    }

    @Transactional
    public UserDtos.UserResponse update(Long id, UserDtos.UserUpdateRequest request) {
        UserAccount account = userAccountRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Không tìm thấy tài khoản"));
        if (!currentUserService.isRoot()
                && !currentUserService.areaForWrite().equals(account.getAreaAdmin())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy tài khoản");
        }
        Role requested = parseRole(request.role());
        String self = currentUserService.username();
        boolean isSelf = account.getUsername().equals(self);
        if (isSelf && Boolean.FALSE.equals(request.enabled())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Không thể tự tắt tài khoản của chính mình");
        }
        if (isSelf && requested != account.getRole()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Không thể thay đổi vai trò của chính mình");
        }
        if ((requested == Role.ADMIN || account.getRole() == Role.ADMIN)
                && requested != account.getRole()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Không thể thay đổi vai trò quản trị viên");
        }
        if (request.password() != null && !request.password().isBlank()) {
            account.setPasswordHash(passwordEncoder.encode(request.password()));
        }
        account.setRole(requested);
        if (request.enabled() != null) {
            account.setEnabled(request.enabled());
        }
        account.setBankAccount(normalizeBankAccount(request.bankAccount()));
        applyPersonAndPeriod(account, requested, request.personId(),
                request.managerStartDate(), request.managerEndDate());
        return UserDtos.toResponse(userAccountRepository.save(account));
    }

    private void applyPersonAndPeriod(UserAccount account, Role role, Long personId,
                                      LocalDate startDate, LocalDate endDate) {
        if (role == Role.MANAGER && personId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Vui lòng liên kết hồ sơ cá nhân cho tài khoản này");
        }
        account.setPerson(personId == null ? null : findPerson(personId));
        if (role == Role.MANAGER) {
            if (startDate == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Vui lòng chọn ngày bắt đầu quản lý");
            }
            if (endDate != null && endDate.isBefore(startDate)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Ngày kết thúc phải sau ngày bắt đầu");
            }
            account.setManagerStartDate(startDate);
            account.setManagerEndDate(endDate);
        } else {
            account.setManagerStartDate(null);
            account.setManagerEndDate(null);
        }
    }

    private Person findPerson(Long personId) {
        return personRepository.findById(personId)
                .filter(Person::isActive)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Không tìm thấy hồ sơ cá nhân"));
    }

    private Role parseRole(String raw) {
        try {
            return Role.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (RuntimeException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Vai trò không hợp lệ");
        }
    }

    private String normalizeUsername(String raw) {
        String username = raw == null ? "" : raw.trim();
        if (!username.matches("[A-Za-z0-9._-]{3,100}")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Tên đăng nhập chỉ gồm chữ, số, dấu chấm, gạch nối, từ 3 đến 100 ký tự");
        }
        return username;
    }

    private String normalizeBankAccount(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        return raw.trim();
    }
}
