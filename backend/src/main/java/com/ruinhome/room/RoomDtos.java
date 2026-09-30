package com.ruinhome.room;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public final class RoomDtos {

    private RoomDtos() {
    }

    public record RoomRequest(
            @NotNull Long houseId,
            @NotBlank @Size(max = 20) String roomNumber,
            @DecimalMin(value = "0.01") BigDecimal areaM2,
            @Size(max = 500) String note) {
    }

    public record RoomResponse(
            Long id,
            Long houseId,
            String houseName,
            String roomNumber,
            BigDecimal areaM2,
            boolean occupied,
            boolean active,
            String note,
            LocalDateTime createdAt,
            LocalDateTime updatedAt) {
    }
}
