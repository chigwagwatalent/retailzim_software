package com.retailzw.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public class GasStockReconciliationRequest {

    @NotNull(message = "Branch is required")
    private Long branchId;

    @NotNull(message = "Tank is required")
    private Long tankId;

    @NotNull(message = "Counted stock is required")
    @DecimalMin(value = "0.000", message = "Counted stock cannot be negative")
    private BigDecimal countedKg;

    @NotBlank(message = "Reconciliation reason is required")
    private String reason;

    private String notes;

    public Long getBranchId() { return branchId; }
    public void setBranchId(Long branchId) { this.branchId = branchId; }
    public Long getTankId() { return tankId; }
    public void setTankId(Long tankId) { this.tankId = tankId; }
    public BigDecimal getCountedKg() { return countedKg; }
    public void setCountedKg(BigDecimal countedKg) { this.countedKg = countedKg; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
}
