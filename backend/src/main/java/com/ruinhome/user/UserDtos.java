package com.ruinhome.user;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.time.LocalDateTime;

public final class UserDtos {

    private UserDtos() {
    }

    public record UserResponse(
            Long id,
            String username,
            Role role,
            Long personId,
            String fullName,
            boolean enabled,
            LocalDate managerStartDate,
            LocalDate managerEndDate,
            String bankAccount,
            LocalDateTime createdAt) {
    }

    public record UserCreateRequest(
            @NotBlank @Size(max = 100) String username,
            @NotBlank @Size(min = 6, max = 100) String password,
            @NotBlank @Size(max = 20) String role,
            Long personId,
            LocalDate managerStartDate,
            LocalDate managerEndDate,
            @Size(max = 30) String bankAccount,
            Boolean enabled) {
    }

    public record UserUpdateRequest(
            @Size(min = 6, max = 100) String password,
            @NotBlank @Size(max = 20) String role,
            Long personId,
            LocalDate managerStartDate,
            LocalDate managerEndDate,
            @Size(max = 30) String bankAccount,
            Boolean enabled) {
    }

    public static UserResponse toResponse(UserAccount account) {
        return new UserResponse(
                account.getId(),
                account.getUsername(),
                account.getRole(),
                account.getPerson() != null ? account.getPerson().getId() : null,
                account.getPerson() != null ? account.getPerson().getFullName() : null,
                account.isEnabled(),
                account.getManagerStartDate(),
                account.getManagerEndDate(),
                account.getBankAccount(),
                account.getCreatedAt());
    }
}
