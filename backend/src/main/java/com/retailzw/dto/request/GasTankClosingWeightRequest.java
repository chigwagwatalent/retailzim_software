package com.retailzw.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public class GasTankClosingWeightRequest {
    @NotNull(message = "Tank is required")
    private Long tankId;
    @NotNull(message = "Closing gross weight is required")
    @DecimalMin(value = "0.000", message = "Closing gross weight cannot be negative")
    private BigDecimal closingGrossKg;

    public Long getTankId() { return tankId; }
    public void setTankId(Long tankId) { this.tankId = tankId; }
    public BigDecimal getClosingGrossKg() { return closingGrossKg; }
    public void setClosingGrossKg(BigDecimal closingGrossKg) { this.closingGrossKg = closingGrossKg; }
}
