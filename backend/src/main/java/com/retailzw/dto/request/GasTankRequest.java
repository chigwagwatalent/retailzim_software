package com.retailzw.dto.request;

import com.retailzw.enums.GasTankStatus;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public class GasTankRequest {
    @NotNull(message = "Branch is required")
    private Long branchId;

    @NotBlank(message = "Tank name is required")
    private String name;

    @NotBlank(message = "Product name is required")
    private String productName;

    @DecimalMin(value = "0.000", message = "Empty/tare weight cannot be negative")
    private BigDecimal tareWeightKg;

    /**
     * Legacy API input. Capacity is now derived from full gross minus tare.
     * When supplied it must match the derived value.
     */
    @DecimalMin(value = "0.000", message = "Capacity cannot be negative")
    private BigDecimal capacityKg;

    @DecimalMin(value = "0.000", message = "Full gross weight cannot be negative")
    private BigDecimal fullGrossWeightKg;

    /**
     * Legacy API input containing net LPG. New clients must submit
     * currentGrossWeightKg so stock is derived from a physical measurement.
     */
    @DecimalMin(value = "0.000", message = "Current quantity cannot be negative")
    private BigDecimal currentKg;

    @DecimalMin(value = "0.000", message = "Current gross weight cannot be negative")
    private BigDecimal currentGrossWeightKg;

    @DecimalMin(value = "0.000", message = "Reorder level cannot be negative")
    private BigDecimal reorderLevelKg;

    private GasTankStatus status = GasTankStatus.ACTIVE;

    public Long getBranchId() { return branchId; }
    public void setBranchId(Long branchId) { this.branchId = branchId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getProductName() { return productName; }
    public void setProductName(String productName) { this.productName = productName; }
    public BigDecimal getTareWeightKg() { return tareWeightKg; }
    public void setTareWeightKg(BigDecimal tareWeightKg) { this.tareWeightKg = tareWeightKg; }
    public BigDecimal getCapacityKg() { return capacityKg; }
    public void setCapacityKg(BigDecimal capacityKg) { this.capacityKg = capacityKg; }
    public BigDecimal getFullGrossWeightKg() { return fullGrossWeightKg; }
    public void setFullGrossWeightKg(BigDecimal fullGrossWeightKg) { this.fullGrossWeightKg = fullGrossWeightKg; }
    public BigDecimal getCurrentKg() { return currentKg; }
    public void setCurrentKg(BigDecimal currentKg) { this.currentKg = currentKg; }
    public BigDecimal getCurrentGrossWeightKg() { return currentGrossWeightKg; }
    public void setCurrentGrossWeightKg(BigDecimal currentGrossWeightKg) {
        this.currentGrossWeightKg = currentGrossWeightKg;
    }
    public BigDecimal getReorderLevelKg() { return reorderLevelKg; }
    public void setReorderLevelKg(BigDecimal reorderLevelKg) { this.reorderLevelKg = reorderLevelKg; }
    public GasTankStatus getStatus() { return status; }
    public void setStatus(GasTankStatus status) { this.status = status; }
}
