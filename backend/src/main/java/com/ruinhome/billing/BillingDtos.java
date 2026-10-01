package com.ruinhome.billing;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;

public final class BillingDtos {

    private BillingDtos() {
    }

    // Fee config
    public record FeeTypeResponse(Long id, String code, String name, String unit, boolean active) {
    }

    public record FeeRateResponse(Long id, Long feeTypeId, String feeCode, String feeName,
                                  String unit, String period, Long price) {
    }

    public record FeeRateUpsertRequest(
            @NotNull Long feeTypeId,
            @NotBlank @Size(max = 7) String period,
            @NotNull @PositiveOrZero Long price) {
    }

    // Meter reading
    public record MeterResponse(Long id, Long roomId, String roomNumber, String houseName,
                                Long feeTypeId, String feeCode, String feeName, String unit,
                                String period, BigDecimal reading, String note) {
    }

    public record MeterCreateRequest(
            @NotNull Long roomId,
            @NotNull Long feeTypeId,
            @NotBlank @Size(max = 7) String period,
            @NotNull @PositiveOrZero BigDecimal reading,
            @Size(max = 500) String note) {
    }

    public record MeterUpdateRequest(
            @NotNull @PositiveOrZero BigDecimal reading,
            @Size(max = 500) String note) {
    }

    // Invoice
    public record InvoiceResponse(Long id, Long roomId, Long houseId, String houseName,
                                  String roomNumber, String period, Long totalAmount, Long paidAmount,
                                  InvoiceStatus status, String note, int lineCount) {
    }

    public record InvoiceLineResponse(Long id, Long feeTypeId, String feeCode, String description,
                                      BigDecimal quantity, Long unitPrice, Long amount) {
    }

    public record InvoiceDetailResponse(Long id, Long roomId, Long houseId, String houseName,
                                        String roomNumber, String period, Long totalAmount, Long paidAmount,
                                        InvoiceStatus status, String note, String roomPriceNote,
                                        Long contractRent,
                                        BigDecimal preElectReading, BigDecimal currentElectReading,
                                        BigDecimal preWaterReading, BigDecimal currentWaterReading,
                                        List<InvoiceLineResponse> lines) {
    }

    public record UsageReadingsRequest(
            @PositiveOrZero BigDecimal preElectReading,
            @PositiveOrZero BigDecimal currentElectReading,
            @PositiveOrZero BigDecimal preWaterReading,
            @PositiveOrZero BigDecimal currentWaterReading) {
    }

    public record InvoiceLineRequest(
            @NotNull Long feeTypeId,
            @NotNull @PositiveOrZero BigDecimal quantity,
            @NotNull @PositiveOrZero Long unitPrice,
            @Size(max = 200) String description) {
    }

    public record InvoiceLineUpdateRequest(
            @NotNull @PositiveOrZero BigDecimal quantity,
            @NotNull @PositiveOrZero Long unitPrice) {
    }

    public record PaymentRequest(@NotNull @Min(1) Long amount) {
    }

    public record RoomPriceRequest(
            @NotNull @PositiveOrZero Long amount,
            @Size(max = 500) String note) {
    }

    public record GenerateSkip(Long roomId, String roomNumber, String reason) {
    }

    public record GenerateResponse(int created, List<GenerateSkip> skipped) {
    }
}
