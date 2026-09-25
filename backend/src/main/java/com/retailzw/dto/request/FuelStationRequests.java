package com.retailzw.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.List;

public final class FuelStationRequests {
    private FuelStationRequests() {}

    public record OpenShift(
            @NotNull Long branchId,
            @NotNull @DecimalMin("0.00") BigDecimal openingUsd,
            @NotNull @DecimalMin("0.00") BigDecimal openingZwg) {}

    public record Payment(
            @NotBlank String method,
            @NotBlank String currency,
            @NotNull @DecimalMin("0.01") BigDecimal amount,
            String reference) {}

    public record Sale(
            @NotNull Long branchId,
            @NotNull Long shiftId,
            @NotNull Long nozzleId,
            @NotNull @DecimalMin("0.001") BigDecimal litres,
            @NotBlank String currency,
            @NotEmpty List<@Valid Payment> payments,
            @NotBlank String idempotencyKey,
            String vehicleRegistration,
            String customerReference) {}

    public record MeterReading(@NotNull Long nozzleId, @NotNull @DecimalMin("0.000") BigDecimal meterLitres) {}
    public record DipReading(@NotNull Long tankId, @NotNull @DecimalMin("0.000") BigDecimal dipLitres,
                             @NotNull @DecimalMin("0.000") BigDecimal waterLevelMm) {}

    public record CloseShift(
            @NotNull Long branchId,
            @NotNull Long shiftId,
            @NotNull @DecimalMin("0.00") BigDecimal countedUsd,
            @NotNull @DecimalMin("0.00") BigDecimal countedZwg,
            @NotEmpty List<@Valid MeterReading> meters,
            @NotEmpty List<@Valid DipReading> dips,
            String note) {}

    public record Delivery(
            @NotNull Long branchId,
            @NotNull Long tankId,
            @NotBlank String supplier,
            @NotBlank String deliveryNote,
            @NotNull @DecimalMin("0.001") BigDecimal invoiceLitres,
            @NotNull @DecimalMin("0.001") BigDecimal receivedLitres,
            @NotNull @DecimalMin("0.000") BigDecimal beforeDipLitres,
            @NotNull @DecimalMin("0.000") BigDecimal afterDipLitres,
            @NotNull @DecimalMin("0.00") BigDecimal costAmount,
            @NotBlank String currency) {}
}
