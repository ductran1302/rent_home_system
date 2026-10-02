package com.ruinhome.contract;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.ruinhome.asset.AssetCategory;
import com.ruinhome.asset.AssetCondition;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public final class ContractDtos {

    private ContractDtos() {
    }

    public record ContractCreateRequest(
            @NotNull Long roomId,
            @NotNull Long holderId,
            @NotNull @PositiveOrZero Long monthlyRent,
            @NotNull @JsonFormat(pattern = "yyyy-MM-dd") LocalDate startDate,
            @NotNull @JsonFormat(pattern = "yyyy-MM-dd") LocalDate endDate,
            List<Long> tenantIds,
            List<Long> assetIds,
            Map<String, Long> feePrices,
            @Size(max = 500) String note) {
    }

    public record ContractUpdateRequest(
            @NotNull @PositiveOrZero Long monthlyRent,
            @NotNull @JsonFormat(pattern = "yyyy-MM-dd") LocalDate endDate,
            List<Long> tenantIds,
            List<Long> assetIds,
            Map<String, Long> feePrices,
            @Size(max = 500) String note) {
    }

    public record TenantResponse(Long id, String fullName, String phone) {
    }

    public record ContractResponse(
            Long id,
            Long roomId,
            Long houseId,
            String houseName,
            String houseCode,
            String roomNumber,
            Long holderId,
            String holderName,
            String holderPhone,
            Long monthlyRent,
            @JsonFormat(pattern = "yyyy-MM-dd") LocalDate startDate,
            @JsonFormat(pattern = "yyyy-MM-dd") LocalDate endDate,
            ContractStatus status,
            List<TenantResponse> tenants,
            Map<String, Long> feePrices,
            String note) {
    }

    public record ContractAssetItemResponse(
            Long id,
            Long assetId,
            String code,
            String name,
            AssetCategory category,
            Long price,
            AssetCondition condition,
            AssetCondition handoverCondition,
            AssetCondition returnCondition,
            String handoverNote,
            @JsonFormat(pattern = "yyyy-MM-dd") LocalDate returnedAt,
            int repairCount,
            long repairCost) {
    }

    public record ContractAssetSummaryResponse(
            int total,
            int brokenCount,
            int needsRepairCount,
            long repairCost) {
    }

    public record ContractAssetListResponse(
            List<ContractAssetItemResponse> items,
            ContractAssetSummaryResponse summary) {
    }

    public record ContractAssetReturnRequest(
            @NotNull AssetCondition returnCondition,
            @JsonFormat(pattern = "yyyy-MM-dd") LocalDate returnedAt) {
    }
}
