package com.ruinhome.asset;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.time.LocalDateTime;

public final class AssetDtos {

    private AssetDtos() {
    }

    public record AssetRequest(
            @NotNull Long roomId,
            @NotBlank @Size(max = 50) String code,
            @NotBlank @Size(max = 200) String name,
            @NotNull AssetCategory category,
            @NotNull @PositiveOrZero Long price,
            @JsonFormat(pattern = "yyyy-MM-dd") LocalDate purchaseDate,
            @NotNull AssetCondition condition,
            @Size(max = 1000) String note) {
    }

    public record AssetResponse(
            Long id,
            Long roomId,
            Long houseId,
            String houseName,
            String roomNumber,
            String code,
            String name,
            AssetCategory category,
            Long price,
            LocalDate purchaseDate,
            AssetCondition condition,
            String note,
            boolean active,
            int repairCount,
            Long repairCost,
            String photoUrl,
            int photoCount,
            LocalDateTime createdAt,
            LocalDateTime updatedAt) {
    }

    public record AssetRepairRequest(
            @JsonFormat(pattern = "yyyy-MM-dd") @NotNull LocalDate reportedAt,
            @NotBlank @Size(max = 500) String description,
            @NotNull @PositiveOrZero Long cost,
            @NotNull AssetRepairStatus status,
            @JsonFormat(pattern = "yyyy-MM-dd") LocalDate doneAt,
            @Size(max = 500) String note) {
    }

    public record AssetRepairResponse(
            Long id,
            Long assetId,
            String assetCode,
            String assetName,
            Long roomId,
            Long houseId,
            String houseName,
            String roomNumber,
            LocalDate reportedAt,
            String description,
            Long cost,
            AssetRepairStatus status,
            LocalDate doneAt,
            String note,
            String photoBeforeUrl,
            String photoAfterUrl,
            LocalDateTime createdAt,
            LocalDateTime updatedAt) {
    }
}
