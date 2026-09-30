package com.ruinhome.person;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

public final class PersonDtos {

    private PersonDtos() {
    }

    public record PersonRequest(
            @NotBlank @Size(max = 200) String fullName,
            @Size(max = 20) String idNumber,
            @Size(max = 20) String phone,
            @Size(max = 500) String address) {
    }

    public record PersonResponse(
            Long id,
            String fullName,
            String idNumber,
            String phone,
            String address,
            boolean active,
            LocalDateTime createdAt,
            LocalDateTime updatedAt,
            String createdBy,
            String updatedBy) {
    }

    public static PersonResponse toResponse(Person person) {
        return new PersonResponse(person.getId(), person.getFullName(), person.getIdNumber(),
                person.getPhone(), person.getAddress(), person.isActive(),
                person.getCreatedAt(), person.getUpdatedAt(),
                person.getCreatedBy(), person.getUpdatedBy());
    }
}
