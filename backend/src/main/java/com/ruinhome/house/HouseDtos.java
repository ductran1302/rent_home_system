package com.ruinhome.house;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

public final class HouseDtos {

    private HouseDtos() {
    }

    public record HouseRequest(
            @NotBlank @Size(max = 50) String code,
            @NotBlank @Size(max = 200) String name,
            @NotBlank @Size(max = 500) String address,
            @NotNull Long ownerId,
            Long managerId,
            @Size(max = 500) String note) {
    }

    public record HouseResponse(
            Long id,
            String code,
            String name,
            String address,
            Long ownerId,
            String ownerName,
            Long managerId,
            String managerName,
            long roomCount,
            boolean active,
            String note,
            LocalDateTime createdAt,
            LocalDateTime updatedAt) {
    }
}
